package com.example.islapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;

public class AdminDashboardActivity extends AppCompatActivity {

    // UI Components (Matches the IDs in activity_admin_dashboard.xml)
    CardView cardAddWord, cardViewFeedback, cardManageDb, cardLogout;

    // Tools
    LoadingDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // Initialize Tools
        loadingDialog = new LoadingDialog(this);

        // Bind Views
        cardAddWord = findViewById(R.id.cardAddWord);       // "+ Add New Video"
        cardViewFeedback = findViewById(R.id.cardViewFeedback); // "View User Requests"
        cardManageDb = findViewById(R.id.cardManageDb);     // "Manage Database"
        cardLogout = findViewById(R.id.cardLogout);         // "Logout"

        // --- NAVIGATION LISTENERS ---

        // 1. Add New Video / Manage Database
        // (Both currently point to ManageDatabaseActivity where you can add/edit words)
        cardAddWord.setOnClickListener(v -> {
            startActivity(new Intent(AdminDashboardActivity.this, AddVideoActivity.class));
        });

        cardManageDb.setOnClickListener(v -> {
            startActivity(new Intent(AdminDashboardActivity.this, ManageDatabaseActivity.class));
        });

        // 2. View User Requests (Feedback)
        cardViewFeedback.setOnClickListener(v -> {
            startActivity(new Intent(AdminDashboardActivity.this, AdminFeedbackActivity.class));
        });

        // --- PROFESSIONAL LOGOUT LOGIC ---
        cardLogout.setOnClickListener(v -> {
            // 1. Show the nice "Signing Out..." animation
            loadingDialog.startLoading("Signing Out...");

            // 2. Add a small delay (1.5s) so the user sees the animation
            new android.os.Handler().postDelayed(() -> {

                // A. Clear Local Session
                SharedPreferences prefs = getSharedPreferences("UserSession", Context.MODE_PRIVATE);
                prefs.edit().clear().apply();

                // B. Sign Out from Firebase
                FirebaseAuth.getInstance().signOut();

                // C. Dismiss Dialog
                loadingDialog.stopLoading();

                // D. Redirect to Login Page
                Intent intent = new Intent(AdminDashboardActivity.this, MainActivity.class);
                // Clear the back stack so they can't press "Back" to return
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();

            }, 1500);
        });
    }
}