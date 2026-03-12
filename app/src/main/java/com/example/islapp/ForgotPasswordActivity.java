package com.example.islapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    EditText resetEmailInput;
    Button btnResetPassword, btnBackToLogin;
    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();

        resetEmailInput = findViewById(R.id.resetEmailInput);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);

        btnResetPassword.setOnClickListener(v -> {
            String email = resetEmailInput.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
                return;
            }
            performRecovery(email);
        });

        btnBackToLogin.setOnClickListener(v -> finish());
    }

    private void performRecovery(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(aVoid -> showCustomSuccessDialog(email))
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showCustomSuccessDialog(String email) {
        // Inflate custom dialog layout
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_recovery_success, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Find Button in dialog
        Button btnOk = dialogView.findViewById(R.id.btnDialogOk);
        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(ForgotPasswordActivity.this, MainActivity.class));
            finish();
        });

        dialog.show();
    }
}