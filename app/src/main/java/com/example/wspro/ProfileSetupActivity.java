package com.example.wspro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class ProfileSetupActivity extends AppCompatActivity {

    private EditText etProfileName, etProfileEmail, etProfilePassword, etProfileCourse;
    private TextView tvNameError, tvEmailError, tvPasswordError;
    private Button btnCompleteSetup;
    private boolean validationAttempted;

    // 1. Media picker launcher for photos and videos from device albums
    private final ActivityResultLauncher<String> mediaPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    Toast.makeText(this, "Profile photo selected!", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        etProfileName = findViewById(R.id.etProfileName);
        etProfileEmail = findViewById(R.id.etProfileEmail);
        etProfilePassword = findViewById(R.id.etProfilePassword);
        etProfileCourse = findViewById(R.id.etProfileCourse);
        tvNameError = findViewById(R.id.tvNameError);
        tvEmailError = findViewById(R.id.tvEmailError);
        tvPasswordError = findViewById(R.id.tvPasswordError);
        Button btnSelectAlbumMedia = findViewById(R.id.btnSelectAlbumMedia);
        btnCompleteSetup = findViewById(R.id.btnCompleteSetup);

        checkAndRequestNotificationPermission();
        addValidationWatcher(etProfileName, this::validateName);
        addValidationWatcher(etProfileEmail, this::validateEmail);
        addValidationWatcher(etProfilePassword, this::validatePassword);

        btnSelectAlbumMedia.setOnClickListener(v -> openDeviceAlbums());
        btnCompleteSetup.setOnClickListener(v -> handleAccountCreation());

        ImageButton btnReturnBack = findViewById(R.id.btnReturnBack);
        if (btnReturnBack != null) {
            btnReturnBack.setOnClickListener(v -> finish());
        }
    }

    private void handleAccountCreation() {
        validationAttempted = true;
        String name = etProfileName.getText().toString().trim();
        String email = etProfileEmail.getText().toString().trim();
        String password = etProfilePassword.getText().toString();
        String course = etProfileCourse.getText().toString().trim();

        boolean isNameValid = validateName();
        boolean isEmailValid = validateEmail();
        boolean isPasswordValid = validatePassword();
        if (!isNameValid || !isEmailValid || !isPasswordValid) {
            return;
        }

        btnCompleteSetup.setEnabled(false);
        btnCompleteSetup.setText("Creating account...");
        Toast.makeText(this, "Creating your account...", Toast.LENGTH_SHORT).show();

        SupabaseClient.getInstance(this).signUp(email, password, name, course, new SupabaseClient.AuthCallback() {
            @Override
            public void onSuccess(String userId, String userEmail, String accessToken) {
                SupabaseClient client = SupabaseClient.getInstance(ProfileSetupActivity.this);
                client.updateProfileData(ProfileSetupActivity.this, name, course, null,
                        new SupabaseClient.DataCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean result) {
                                finishAccountCreation(name, null);
                            }

                            @Override
                            public void onError(String errorMessage) {
                                finishAccountCreation(name, errorMessage);
                            }
                        });
            }

            @Override
            public void onError(String errorMessage) {
                btnCompleteSetup.setEnabled(true);
                btnCompleteSetup.setText("Create Account & Sign In");
                Toast.makeText(ProfileSetupActivity.this, "Sign-up error: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void finishAccountCreation(String name, String profileError) {
        btnCompleteSetup.setEnabled(true);
        btnCompleteSetup.setText("Create Account & Sign In");
        if (profileError == null) {
            Toast.makeText(this, "Welcome, " + name + "! Account created.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Account created, but profile save failed: " + profileError,
                    Toast.LENGTH_LONG).show();
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void addValidationWatcher(EditText field, Runnable validation) {
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (validationAttempted) validation.run();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    private boolean validateName() {
        String name = etProfileName.getText().toString().trim();
        setInlineError(tvNameError, name.isEmpty() ? "Please enter your name." : null);
        return !name.isEmpty();
    }

    private boolean validateEmail() {
        String email = etProfileEmail.getText().toString().trim();
        String error = email.isEmpty()
                ? "Please enter your email address."
                : Patterns.EMAIL_ADDRESS.matcher(email).matches()
                ? null
                : "Enter a valid email address.";
        setInlineError(tvEmailError, error);
        return error == null;
    }

    private boolean validatePassword() {
        String password = etProfilePassword.getText().toString();
        String error = password.isEmpty() ? "Please enter a password." : passwordError(password);
        setInlineError(tvPasswordError, error);
        return error == null;
    }

    private String passwordError(String password) {
        if (password.length() < 8) return "Use at least 8 characters.";
        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c) && !Character.isSpaceChar(c)) hasSpecial = true;
        }

        if (!hasLetter) return "Include at least one letter.";
        if (!hasDigit) return "Include at least one number.";
        if (!hasSpecial) return "Include at least one special character (spaces don't count).";
        return null;
    }

    private void setInlineError(TextView errorView, String message) {
        errorView.setText(message);
        errorView.setVisibility(message == null ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void openDeviceAlbums() {
        mediaPickerLauncher.launch("image/*");
    }
}
