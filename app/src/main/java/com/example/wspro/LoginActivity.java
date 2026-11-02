package com.example.wspro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

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
            refreshProfileAndNavigate();
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

        // 5. Handle Forgot Password: Navigate to full ForgotPasswordActivity
        tvForgot.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        handleIncomingSnackbar(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingSnackbar(intent);
    }

    private void handleIncomingSnackbar(Intent intent) {
        if (intent != null && intent.hasExtra(ForgotPasswordActivity.EXTRA_SNACKBAR_MESSAGE)) {
            String message = intent.getStringExtra(ForgotPasswordActivity.EXTRA_SNACKBAR_MESSAGE);
            if (message != null && !message.isEmpty()) {
                View rootView = findViewById(android.R.id.content);
                if (rootView != null) {
                    Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
                    snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.ws_accent));
                    snackbar.setTextColor(ContextCompat.getColor(this, R.color.ws_on_brand));
                    snackbar.show();
                }
                intent.removeExtra(ForgotPasswordActivity.EXTRA_SNACKBAR_MESSAGE);
            }
        }
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
                refreshProfileAndNavigate();
            }

            @Override
            public void onError(String errorMessage) {
                btnLogin.setEnabled(true);
                btnLogin.setText("Log In");
                Toast.makeText(LoginActivity.this, "Login failed: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshProfileAndNavigate() {
        if (supabaseClient.isDemoSession()) {
            navigateToDashboard();
            return;
        }
        supabaseClient.loadProfileFromSupabase(this, new SupabaseClient.DataCallback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (profile == null) {
                    supabaseClient.updateProfileData(LoginActivity.this,
                            supabaseClient.getUserName(), supabaseClient.getUserCourse(),
                            supabaseClient.getAvatarUrl(), new SupabaseClient.DataCallback<Boolean>() {
                                @Override
                                public void onSuccess(Boolean result) {
                                    navigateToDashboard();
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                    navigateToDashboard();
                                }
                            });
                    return;
                }
                navigateToDashboard();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(LoginActivity.this,
                        "Couldn't refresh your profile: " + errorMessage, Toast.LENGTH_LONG).show();
                navigateToDashboard();
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
