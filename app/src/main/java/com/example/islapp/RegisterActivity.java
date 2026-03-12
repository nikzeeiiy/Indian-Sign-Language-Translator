package com.example.islapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    EditText regEmail, regUsername, regPassword;
    Button btnRegister, btnCancel;

    // REMOVED: LinearLayout otpLayout; (Not needed anymore)

    // Firebase
    FirebaseAuth mAuth;
    FirebaseFirestore fStore;

    // Tools
    LoadingDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();

        // Initialize Loading Tool
        loadingDialog = new LoadingDialog(this);

        // Bind Views
        regEmail = findViewById(R.id.regEmail);
        regUsername = findViewById(R.id.regUsername);
        regPassword = findViewById(R.id.regPassword);
        btnRegister = findViewById(R.id.btnSendCode); // Make sure XML has this ID
        btnCancel = findViewById(R.id.btnCancel);

        // REMOVED: otpLayout = findViewById...
        // REMOVED: otpLayout.setVisibility...

        btnRegister.setText("Create Account");

        // --- REGISTER BUTTON ---
        btnRegister.setOnClickListener(v -> {
            String email = regEmail.getText().toString().trim();
            String user = regUsername.getText().toString().trim();
            String pass = regPassword.getText().toString().trim();

            if (email.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (pass.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            checkUserAndRegister(email, pass, user);
        });

        // --- CANCEL BUTTON ---
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> finish());
        }
    }

    private void checkUserAndRegister(String email, String password, String username) {
        loadingDialog.startLoading("Checking Availability...");
        btnRegister.setEnabled(false);

        // Check if Username Unique
        fStore.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot snapshot = task.getResult();
                        if (snapshot != null && !snapshot.isEmpty()) {
                            // Fail: Username Taken
                            loadingDialog.stopLoading();
                            btnRegister.setEnabled(true);
                            Toast.makeText(this, "User ID '" + username + "' is already taken.", Toast.LENGTH_LONG).show();
                        } else {
                            // Success: Create Account
                            performFirebaseRegistration(email, password, username);
                        }
                    } else {
                        loadingDialog.stopLoading();
                        btnRegister.setEnabled(true);
                        Toast.makeText(this, "Network Error. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void performFirebaseRegistration(String email, String password, String username) {
        // Update Loading Message
        loadingDialog.stopLoading();
        loadingDialog.startLoading("Creating Account...");

        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Account Created
                FirebaseUser fUser = mAuth.getCurrentUser();
                if (fUser != null) fUser.sendEmailVerification();

                // Save Data
                String userId = fUser.getUid();
                Map<String, Object> userData = new HashMap<>();
                userData.put("username", username);
                userData.put("email", email);
                userData.put("role", "user");

                fStore.collection("users").document(userId).set(userData).addOnSuccessListener(aVoid -> {
                    loadingDialog.stopLoading();
                    Toast.makeText(this, "Account Created! Verification email sent.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(getApplicationContext(), MainActivity.class));
                    finish();
                });
            } else {
                // Failure
                loadingDialog.stopLoading();
                btnRegister.setEnabled(true);

                Exception e = task.getException();
                if (e instanceof FirebaseAuthUserCollisionException) {
                    showDuplicateAccountDialog(email);
                } else {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showDuplicateAccountDialog(String email) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_custom_alert, null);
        builder.setView(dialogView);

        TextView title = dialogView.findViewById(R.id.dialogTitle);
        TextView message = dialogView.findViewById(R.id.dialogMessage);
        Button btnAction = dialogView.findViewById(R.id.btnDialogAction);
        Button btnCancel = dialogView.findViewById(R.id.btnDialogCancel);
        ImageView icon = dialogView.findViewById(R.id.dialogIcon);

        title.setText("Account Exists");
        message.setText("The email '" + email + "' is already registered.\nDo you want to recover your password?");

        icon.setImageResource(android.R.drawable.ic_dialog_alert);
        icon.setColorFilter(Color.parseColor("#F44336"));

        btnAction.setText("Recover");
        btnAction.setBackgroundColor(Color.parseColor("#2196F3"));

        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnAction.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(RegisterActivity.this, ForgotPasswordActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
            finish();
        });

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            regEmail.requestFocus();
        });

        dialog.show();
    }
}