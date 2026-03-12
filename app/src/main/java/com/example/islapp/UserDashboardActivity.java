package com.example.islapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class UserDashboardActivity extends AppCompatActivity {

    // UI Components
    CardView btnSignToVoice, btnVoiceToSign;
    Button btnFeedback, btnLogout;
    FrameLayout btnNotifications;
    TextView notificationBadge;

    // Firebase
    FirebaseFirestore fStore;
    FirebaseAuth mAuth;
    SharedPreferences prefs;

    // Data & Tools
    String currentUsername;
    LoadingDialog loadingDialog; // Using the Custom Class

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_dashboard);

        // Initialize Firebase
        fStore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Initialize Tools
        loadingDialog = new LoadingDialog(this);
        prefs = getSharedPreferences("UserSession", Context.MODE_PRIVATE);
        currentUsername = prefs.getString("username", "User");

        // Bind Views
        btnSignToVoice = findViewById(R.id.btnSignToVoice);
        btnVoiceToSign = findViewById(R.id.btnVoiceToSign);
        btnFeedback = findViewById(R.id.btnFeedback);
        btnLogout = findViewById(R.id.btnLogout);
        btnNotifications = findViewById(R.id.btnNotifications);
        notificationBadge = findViewById(R.id.notificationBadge);

        // --- CLICK LISTENERS ---

        // 1. Navigation
        btnSignToVoice.setOnClickListener(v -> startActivity(new Intent(this, CameraActivity.class)));
        btnVoiceToSign.setOnClickListener(v -> startActivity(new Intent(this, VoiceActivity.class)));

        // 2. Notifications
        btnNotifications.setOnClickListener(v -> startActivity(new Intent(this, NotificationActivity.class)));

        // 3. Feedback / Request Word
        btnFeedback.setOnClickListener(v -> showFeedbackDialog());

        // 4. LOGOUT (Professional Implementation)
        btnLogout.setOnClickListener(v -> {
            // Show "Signing Out..." Dialog
            loadingDialog.startLoading("Signing Out...");

            // Delay for UX (1.5 seconds) so user sees the transition
            new android.os.Handler().postDelayed(() -> {
                // A. Clear Local Session
                prefs.edit().clear().apply();

                // B. Sign Out from Firebase
                mAuth.signOut();

                // C. Close Dialog
                loadingDialog.stopLoading();

                // D. Redirect to Login
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // Clear Back Stack
                startActivity(intent);
                finish();
            }, 1500);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh badge count whenever user returns to dashboard
        updateNotificationBadge();
    }

    // --- NOTIFICATION BADGE LOGIC ---
    private void updateNotificationBadge() {
        if (currentUsername == null || currentUsername.isEmpty()) return;

        fStore.collection("notifications")
                .whereEqualTo("target_user", currentUsername)
                .whereEqualTo("is_read", false)
                .count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener(snapshot -> {
                    long count = snapshot.getCount();
                    if (count > 0) {
                        notificationBadge.setVisibility(View.VISIBLE);
                        notificationBadge.setText(String.valueOf(count));
                    } else {
                        notificationBadge.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    // Silently fail if index not built yet
                });
    }

    // --- FEEDBACK DIALOG ---
    private void showFeedbackDialog() {
        EditText input = new EditText(this);
        input.setHint("Type the word you want added...");
        input.setPadding(50, 50, 50, 50);

        new AlertDialog.Builder(this)
                .setTitle("Request New Word")
                .setView(input)
                .setPositiveButton("Send Request", (dialog, which) -> {
                    String word = input.getText().toString().trim();
                    if (!word.isEmpty()) {
                        sendFeedbackToCloud(word);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- SEND TO FIRESTORE ---
    private void sendFeedbackToCloud(String message) {
        Map<String, Object> feedback = new HashMap<>();
        feedback.put("username", currentUsername);
        feedback.put("message", message);
        feedback.put("timestamp", System.currentTimeMillis());

        fStore.collection("feedback")
                .add(feedback)
                .addOnSuccessListener(doc -> Toast.makeText(this, "Request sent successfully!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to send request.", Toast.LENGTH_SHORT).show());
    }
}