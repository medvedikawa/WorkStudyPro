package com.example.wspro;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginId, etLoginPass;
    private CheckBox cbRemember;
    private Button btnLogin;
    private SupabaseClient supabaseClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supabaseClient = SupabaseClient.getInstance(this);

        // 1. Auto-skip login if session exists in client
        if (supabaseClient.isLoggedIn()) {
            navigateToDashboard();
            return;
        }

        setContentView(R.layout.activity_welcomepage);

        etLoginId = findViewById(R.id.etLoginId);
        etLoginPass = findViewById(R.id.etLoginPass);
        cbRemember = findViewById(R.id.cbRemember);
        btnLogin = findViewById(R.id.btnLogin);
        Button btnCreateAccountNav = findViewById(R.id.btnCreateAccountNav);
        Button btnDemo = findViewById(R.id.btnDemo);
        TextView tvForgot = findViewById(R.id.tvForgot);

        // 2. Handle Sign In Click
        btnLogin.setOnClickListener(v -> handleLogin());

        // 3. Handle "Create an account" Button Click -> Leads to Profile Setup / Registration
        btnCreateAccountNav.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ProfileSetupActivity.class);
            startActivity(intent);
        });

        // 4. Handle Demo Account Quick Access
        btnDemo.setOnClickListener(v -> {
            Toast.makeText(this, "Logging in with Demo Account...", Toast.LENGTH_SHORT).show();
            supabaseClient.saveSession("demo_token", "demo_user_id", "demo@student.edu", "Demo Student", "BS Computer Science");
            navigateToDashboard();
        });

        // 5. Handle Forgot Password
        tvForgot.setOnClickListener(v -> {
            EditText etEmail = new EditText(this);
            etEmail.setHint("Enter your registered email");
            etEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_CLASS_TEXT);
            int padding = (int) (16 * getResources().getDisplayMetrics().density);
            etEmail.setPadding(padding, padding, padding, padding);

            new AlertDialog.Builder(this)
                    .setTitle("Reset Password")
                    .setMessage("Enter your registered email address to receive password reset instructions.")
                    .setView(etEmail)
                    .setPositiveButton("Send Reset Link", (dialog, which) -> {
                        String email = etEmail.getText().toString().trim();
                        if (email.isEmpty()) {
                            Toast.makeText(LoginActivity.this, "Please enter your email.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Toast.makeText(LoginActivity.this, "Sending reset link...", Toast.LENGTH_SHORT).show();
                        supabaseClient.resetPassword(email, new SupabaseClient.AuthCallback() {
                            @Override
                            public void onSuccess(String userId, String userEmail, String accessToken) {
                                Toast.makeText(LoginActivity.this, "Password reset instructions sent to " + email, Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                Toast.makeText(LoginActivity.this, "Failed to send reset link: " + errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void handleLogin() {
        String email = etLoginId.getText().toString().trim();
        String password = etLoginPass.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in both email and password.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Signing In...");
        Toast.makeText(this, "Signing in...", Toast.LENGTH_SHORT).show();

        supabaseClient.signIn(email, password, new SupabaseClient.AuthCallback() {
            @Override
            public void onSuccess(String userId, String userEmail, String accessToken) {
                btnLogin.setEnabled(true);
                btnLogin.setText("Log In");
                Toast.makeText(LoginActivity.this, "Welcome back!", Toast.LENGTH_SHORT).show();
                navigateToDashboard();
            }

            @Override
            public void onError(String errorMessage) {
                btnLogin.setEnabled(true);
                btnLogin.setText("Log In");
                Toast.makeText(LoginActivity.this, "Login failed: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
