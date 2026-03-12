package com.example.islapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class NotificationActivity extends AppCompatActivity {

    ListView listView;
    TextView btnClear; // Changed to TextView because in the new XML header it is text
    FirebaseFirestore fStore;
    String currentUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        // Initialize Firebase
        fStore = FirebaseFirestore.getInstance();

        // Bind Views
        listView = findViewById(R.id.notifList);
        btnClear = findViewById(R.id.btnClear);

        // Get Current User
        SharedPreferences prefs = getSharedPreferences("UserSession", Context.MODE_PRIVATE);
        currentUsername = prefs.getString("username", "");

        loadNotifications();

        // Clear All Logic
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> clearAllNotifications());
        }
    }

    private void loadNotifications() {
        if (currentUsername.isEmpty()) return;

        fStore.collection("notifications")
                .whereEqualTo("target_user", currentUsername)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        ArrayList<Map<String, String>> listItems = new ArrayList<>();

                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            Map<String, String> item = new HashMap<>();

                            // Get Data safely
                            String msg = doc.getString("message");
                            if (msg == null) msg = "New Update Available";

                            item.put("message", msg);
                            item.put("title", "Admin Update"); // Static Title for now
                            listItems.add(item);

                            // Mark as read in background
                            fStore.collection("notifications").document(doc.getId()).update("is_read", true);
                        }

                        if (listItems.isEmpty()) {
                            // Optional: Add a placeholder item if empty
                            Map<String, String> emptyItem = new HashMap<>();
                            emptyItem.put("title", "No Notifications");
                            emptyItem.put("message", "You are all caught up!");
                            listItems.add(emptyItem);
                        }

                        // --- FIX: Use Custom Pro Layout ---
                        SimpleAdapter adapter = new SimpleAdapter(this, listItems,
                                R.layout.item_notification, // The new Card layout
                                new String[]{"title", "message"},
                                new int[]{R.id.notifTitle, R.id.notifMessage} // IDs inside item_notification.xml
                        );

                        listView.setAdapter(adapter);
                    } else {
                        Toast.makeText(this, "Failed to load notifications.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void clearAllNotifications() {
        fStore.collection("notifications")
                .whereEqualTo("target_user", currentUsername)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        fStore.collection("notifications").document(doc.getId()).delete();
                    }
                    Toast.makeText(this, "Notifications Cleared", Toast.LENGTH_SHORT).show();
                    loadNotifications(); // Refresh list to show empty state
                });
    }
}