package com.example.wspro;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginId, etLoginPass;
    private CheckBox cbRemember;
    private SharedPreferences sharedPreferences;

    private static final String PREF_NAME = "WorkStudyPrefs";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Check if "Remember me" was previously checked — auto-skip login if true
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);

        if (isLoggedIn) {
            navigateToDashboard();
            return;
        }

        setContentView(R.layout.activity_layout);

        etLoginId = findViewById(R.id.etLoginId);
        etLoginPass = findViewById(R.id.etLoginPass);
        cbRemember = findViewById(R.id.cbRemember);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnCreateAccountNav = findViewById(R.id.btnCreateAccountNav);
        Button btnDemo = findViewById(R.id.btnDemo);
        TextView tvForgot = findViewById(R.id.tvForgot);

        // 2. Handle Sign In Click
        btnLogin.setOnClickListener(v -> handleLogin());

        // 3. Handle "Create an account" Button Click -> Leads to Profile Setup
        btnCreateAccountNav.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ProfileSetupActivity.class);
            startActivity(intent);
        });

        // 4. Handle Demo Account Quick Access
        btnDemo.setOnClickListener(v -> {
            Toast.makeText(this, "Logging in with Demo Account...", Toast.LENGTH_SHORT).show();
            saveSessionAndNavigate();
        });

        tvForgot.setOnClickListener(v ->
                Toast.makeText(this, "Password recovery link sent to your registered email.", Toast.LENGTH_LONG).show()
        );
    }

    private void handleLogin() {
        String idOrEmail = etLoginId.getText().toString().trim();
        String password = etLoginPass.getText().toString().trim();

        if (idOrEmail.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Authentication logic simulation (connects to your local or capstone user data)
        Toast.makeText(this, "Welcome back, " + idOrEmail + "!", Toast.LENGTH_SHORT).show();
        saveSessionAndNavigate();
    }

    private void saveSessionAndNavigate() {
        // If "Remember me" is checked, save session state locally
        if (cbRemember != null && cbRemember.isChecked()) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_IS_LOGGED_IN, true);
            editor.apply();
        }

        navigateToDashboard();
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish(); // Close LoginActivity so the user can't press back into it
    }
}