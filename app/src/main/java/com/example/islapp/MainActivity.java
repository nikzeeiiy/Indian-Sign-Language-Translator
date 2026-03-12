package com.example.islapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    EditText emailInput, passwordInput;
    Button loginButton;
    TextView registerLink, forgotPasswordText;
    CheckBox chkRememberMe;

    FirebaseAuth mAuth;
    FirebaseFirestore fStore;
    SharedPreferences loginPrefs;
    LoadingDialog loadingDialog; // Using the Custom Class

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();

        // Initialize UI Components
        emailInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        registerLink = findViewById(R.id.registerLink);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        chkRememberMe = findViewById(R.id.chkRememberMe);

        // Initialize Helpers
        loginPrefs = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE);
        loadingDialog = new LoadingDialog(this);

        // 1. Check if User is already Logged In (and Verified)
        if (mAuth.getCurrentUser() != null && mAuth.getCurrentUser().isEmailVerified()) {
            checkRoleAndRedirect(mAuth.getCurrentUser().getUid());
        }

        // 2. Auto-Fill if "Remember Me" was used previously
        checkRememberedCredentials();

        // --- LOGIN BUTTON ---
        loginButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String pass = passwordInput.getText().toString().trim();

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please enter Email and Password", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Invalid Email Format", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save or Clear Credentials based on Checkbox
            handleRememberMe(email, pass);

            // Perform Login
            loginUser(email, pass);
        });

        // --- NAVIGATION ---
        registerLink.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, RegisterActivity.class)));

        if (forgotPasswordText != null) {
            forgotPasswordText.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ForgotPasswordActivity.class)));
        }
    }

    // --- REMEMBER ME LOGIC ---
    private void checkRememberedCredentials() {
        boolean isRemembered = loginPrefs.getBoolean("saveLogin", false);
        if (isRemembered) {
            emailInput.setText(loginPrefs.getString("email", ""));
            passwordInput.setText(loginPrefs.getString("password", ""));
            chkRememberMe.setChecked(true);
        }
    }

    private void handleRememberMe(String email, String password) {
        SharedPreferences.Editor editor = loginPrefs.edit();
        if (chkRememberMe.isChecked()) {
            editor.putBoolean("saveLogin", true);
            editor.putString("email", email);
            editor.putString("password", password);
        } else {
            editor.clear();
        }
        editor.apply();
    }

    // --- FIREBASE LOGIN ---
    private void loginUser(String email, String password) {
        // Show Professional Loading Dialog
        loadingDialog.startLoading("Logging In...");
        loginButton.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            // Dismiss Dialog only when result arrives

            if (!task.isSuccessful()) {
                loadingDialog.stopLoading();
                loginButton.setEnabled(true);

                // Error Handling
                try {
                    throw task.getException();
                } catch (FirebaseAuthInvalidUserException e) {
                    Toast.makeText(this, "No account found with this email.", Toast.LENGTH_LONG).show();
                } catch (FirebaseAuthInvalidCredentialsException e) {
                    Toast.makeText(this, "Incorrect Password.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Login Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                // Success: Check Verification Status
                if (mAuth.getCurrentUser().isEmailVerified()) {
                    checkRoleAndRedirect(mAuth.getCurrentUser().getUid());
                } else {
                    loadingDialog.stopLoading();
                    loginButton.setEnabled(true);
                    Toast.makeText(this, "Email not verified. Please check your inbox.", Toast.LENGTH_LONG).show();
                    mAuth.signOut();
                }
            }
        });
    }

    // --- ROLE REDIRECTION ---
    private void checkRoleAndRedirect(String uid) {
        // Retrieve User Role from Firestore to decide where to go

        fStore.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
            loadingDialog.stopLoading(); // Stop loading now that we have data
            loginButton.setEnabled(true);

            if (documentSnapshot.exists()) {
                String role = documentSnapshot.getString("role");
                String username = documentSnapshot.getString("username");

                // Save Session Locally
                SharedPreferences prefs = getSharedPreferences("UserSession", Context.MODE_PRIVATE);
                prefs.edit().putString("username", username).apply();

                // Redirect
                if ("admin".equals(role)) {
                    startActivity(new Intent(MainActivity.this, AdminDashboardActivity.class));
                } else {
                    startActivity(new Intent(MainActivity.this, UserDashboardActivity.class));
                }
                finish(); // Prevent going back to login
            } else {
                Toast.makeText(this, "User data error.", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            loadingDialog.stopLoading();
            loginButton.setEnabled(true);
            Toast.makeText(this, "Network Error.", Toast.LENGTH_SHORT).show();
        });
    }
}