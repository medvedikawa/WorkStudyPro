package com.example.wspro;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class EditProfileActivity extends AppCompatActivity {

    private SupabaseClient client;

    private TextView tvAvatarInitial;
    private ImageView ivProfileAvatar;
    private ProgressBar pbAvatarLoading;
    private Button btnChangePhoto;

    private EditText etEditName, etEditCourse, etEditEmail;
    private TextView tvNameError, tvCourseError, tvEmailError;
    private Button btnSaveProfileInfo, btnUpdateEmail;

    private Button btnSendPasswordCode, btnUpdatePassword;
    private LinearLayout layoutPasswordVerificationStep;
    private EditText etPasswordNonce, etNewPassword, etConfirmPassword;
    private TextView tvNonceError, tvNewPasswordError, tvConfirmPasswordError;

    private boolean validationAttemptedInfo = false;
    private boolean validationAttemptedEmail = false;
    private boolean validationAttemptedPassword = false;

    private final ActivityResultLauncher<String> mediaPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    openCircularCropper(uri);
                }
            }
    );

    private final ActivityResultLauncher<Intent> cropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleCroppedAvatarResult(result.getData());
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        client = SupabaseClient.getInstance(this);
        if (client.isDemoSession()) {
            Toast.makeText(this, R.string.profile_demo_edit_unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        tvAvatarInitial = findViewById(R.id.tvAvatarInitial);
        ivProfileAvatar = findViewById(R.id.ivProfileAvatar);
        pbAvatarLoading = findViewById(R.id.pbAvatarLoading);
        btnChangePhoto = findViewById(R.id.btnChangePhoto);

        etEditName = findViewById(R.id.etEditName);
        etEditCourse = findViewById(R.id.etEditCourse);
        etEditEmail = findViewById(R.id.etEditEmail);

        tvNameError = findViewById(R.id.tvNameError);
        tvCourseError = findViewById(R.id.tvCourseError);
        tvEmailError = findViewById(R.id.tvEmailError);

        btnSaveProfileInfo = findViewById(R.id.btnSaveProfileInfo);
        btnUpdateEmail = findViewById(R.id.btnUpdateEmail);

        btnSendPasswordCode = findViewById(R.id.btnSendPasswordCode);
        btnUpdatePassword = findViewById(R.id.btnUpdatePassword);
        layoutPasswordVerificationStep = findViewById(R.id.layoutPasswordVerificationStep);

        etPasswordNonce = findViewById(R.id.etPasswordNonce);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        tvNonceError = findViewById(R.id.tvNonceError);
        tvNewPasswordError = findViewById(R.id.tvNewPasswordError);
        tvConfirmPasswordError = findViewById(R.id.tvConfirmPasswordError);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnChangePhoto.setOnClickListener(v -> checkAndOpenGallery());
        ivProfileAvatar.setOnClickListener(v -> checkAndOpenGallery());
        tvAvatarInitial.setOnClickListener(v -> checkAndOpenGallery());

        btnSaveProfileInfo.setOnClickListener(v -> handleSaveProfileInfo());
        btnUpdateEmail.setOnClickListener(v -> handleUpdateEmail());
        btnSendPasswordCode.setOnClickListener(v -> handleRequestPasswordCode());
        btnUpdatePassword.setOnClickListener(v -> handleUpdatePassword());

        populateFields();
        setupTextWatchers();
        renderAvatar();
    }

    private void populateFields() {
        etEditName.setText(client.getUserName());
        etEditCourse.setText(client.getUserCourse());
        etEditEmail.setText(client.getUserEmail());
    }

    private void renderAvatar() {
        String name = client.getUserName();
        String initial = (name == null || name.trim().isEmpty())
                ? "?" : name.trim().substring(0, 1).toUpperCase(Locale.getDefault());
        tvAvatarInitial.setText(initial);
        tvAvatarInitial.setVisibility(View.VISIBLE);
        ivProfileAvatar.setVisibility(View.GONE);
        client.loadProfileAvatar(this, new SupabaseClient.DataCallback<Bitmap>() {
            @Override
            public void onSuccess(Bitmap bitmap) {
                if (isFinishing()) return;
                ivProfileAvatar.setImageBitmap(CropImageView.getCircularBitmap(bitmap));
                ivProfileAvatar.setVisibility(View.VISIBLE);
                tvAvatarInitial.setVisibility(View.GONE);
            }

            @Override
            public void onError(String errorMessage) {
                if (!isFinishing()) {
                    ivProfileAvatar.setVisibility(View.GONE);
                    tvAvatarInitial.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void checkAndOpenGallery() {
        openGalleryPicker();
    }

    private void openGalleryPicker() {
        mediaPickerLauncher.launch("image/*");
    }

    private void openCircularCropper(Uri uri) {
        Intent intent = new Intent(this, CropProfilePictureActivity.class);
        intent.putExtra("RAW_IMAGE_URI", uri.toString());
        cropLauncher.launch(intent);
    }

    private void handleCroppedAvatarResult(Intent data) {
        String croppedUriStr = data.getStringExtra("CROPPED_IMAGE_URI");
        String filePath = data.getStringExtra("CROPPED_FILE_PATH");
        if (croppedUriStr == null) return;

        Uri croppedUri = Uri.parse(croppedUriStr);
        new Thread(() -> {
            try {
                Bitmap bitmap;
                if (filePath != null && new File(filePath).exists()) {
                    bitmap = BitmapFactory.decodeFile(filePath);
                } else {
                    try (InputStream in = getContentResolver().openInputStream(croppedUri)) {
                        bitmap = BitmapFactory.decodeStream(in);
                    }
                }

                if (bitmap != null) {
                    Bitmap circular = CropImageView.getCircularBitmap(bitmap);
                    File localAvatarFile = client.getAvatarCacheFile();
                    if (localAvatarFile == null) {
                        throw new IOException("No signed-in account is available.");
                    }
                    try (FileOutputStream out = new FileOutputStream(localAvatarFile)) {
                        circular.compress(Bitmap.CompressFormat.JPEG, 92, out);
                    }
                    client.setAvatarPath(localAvatarFile.getAbsolutePath());

                    runOnUiThread(() -> {
                        ivProfileAvatar.setImageBitmap(circular);
                        ivProfileAvatar.setVisibility(View.VISIBLE);
                        tvAvatarInitial.setVisibility(View.GONE);
                        uploadAvatarToCloud(localAvatarFile);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(EditProfileActivity.this, "Failed to load cropped photo.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void uploadAvatarToCloud(File localFile) {
        pbAvatarLoading.setVisibility(View.VISIBLE);
        btnChangePhoto.setEnabled(false);

        new Thread(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Bitmap bitmap = BitmapFactory.decodeFile(localFile.getAbsolutePath());
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
                byte[] bytes = baos.toByteArray();

                runOnUiThread(() -> client.uploadProfilePicture(bytes, new SupabaseClient.DataCallback<String>() {
                    @Override
                    public void onSuccess(String publicUrl) {
                        pbAvatarLoading.setVisibility(View.GONE);
                        btnChangePhoto.setEnabled(true);
                        client.updateProfileData(EditProfileActivity.this, client.getUserName(),
                                client.getUserCourse(), publicUrl,
                                new SupabaseClient.DataCallback<Boolean>() {
                                    @Override
                                    public void onSuccess(Boolean result) {
                                        Toast.makeText(EditProfileActivity.this,
                                                "Profile picture updated!", Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onError(String errorMessage) {
                                        Toast.makeText(EditProfileActivity.this,
                                                "Picture uploaded, but profile save failed: " + errorMessage,
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        pbAvatarLoading.setVisibility(View.GONE);
                        btnChangePhoto.setEnabled(true);
                        Toast.makeText(EditProfileActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                }));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pbAvatarLoading.setVisibility(View.GONE);
                    btnChangePhoto.setEnabled(true);
                });
            }
        }).start();
    }

    private void handleSaveProfileInfo() {
        validationAttemptedInfo = true;
        String name = etEditName.getText().toString().trim();
        String course = etEditCourse.getText().toString().trim();

        boolean isNameValid = validateName();
        boolean isCourseValid = validateCourse();
        if (!isNameValid || !isCourseValid) return;

        btnSaveProfileInfo.setEnabled(false);
        btnSaveProfileInfo.setText("Saving...");

        client.updateProfileData(this, name, course, client.getAvatarUrl(), new SupabaseClient.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                btnSaveProfileInfo.setEnabled(true);
                btnSaveProfileInfo.setText("Save Info");
                renderAvatar();
                Toast.makeText(EditProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                btnSaveProfileInfo.setEnabled(true);
                btnSaveProfileInfo.setText("Save Info");
                Toast.makeText(EditProfileActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleUpdateEmail() {
        validationAttemptedEmail = true;
        String newEmail = etEditEmail.getText().toString().trim();
        if (!validateEmail()) return;

        btnUpdateEmail.setEnabled(false);
        btnUpdateEmail.setText("Sending confirmation...");

        client.updateEmail(this, newEmail, new SupabaseClient.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                btnUpdateEmail.setEnabled(true);
                btnUpdateEmail.setText("Update Email");
                Toast.makeText(EditProfileActivity.this,
                        "Confirmation link sent to " + newEmail + ". Please check your inbox.",
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String errorMessage) {
                btnUpdateEmail.setEnabled(true);
                btnUpdateEmail.setText("Update Email");
                setInlineError(tvEmailError, errorMessage);
            }
        });
    }

    private void handleRequestPasswordCode() {
        btnSendPasswordCode.setEnabled(false);
        btnSendPasswordCode.setText("Sending code...");

        client.reauthenticate(new SupabaseClient.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                btnSendPasswordCode.setEnabled(true);
                btnSendPasswordCode.setText("Resend Verification Code");
                layoutPasswordVerificationStep.setVisibility(View.VISIBLE);
                Toast.makeText(EditProfileActivity.this,
                        "Verification code sent to your email.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String errorMessage) {
                btnSendPasswordCode.setEnabled(true);
                btnSendPasswordCode.setText("Send Verification Code");
                Toast.makeText(EditProfileActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleUpdatePassword() {
        validationAttemptedPassword = true;
        String nonce = etPasswordNonce.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        boolean isNonceValid = validateNonce();
        boolean isNewPasswordValid = validateNewPassword();
        boolean isConfirmValid = validateConfirmPassword();
        if (!isNonceValid || !isNewPasswordValid || !isConfirmValid) return;

        btnUpdatePassword.setEnabled(false);
        btnUpdatePassword.setText("Updating password...");

        client.updatePasswordWithNonce(newPassword, nonce, new SupabaseClient.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                btnUpdatePassword.setEnabled(true);
                btnUpdatePassword.setText("Update Password");
                etPasswordNonce.setText("");
                etNewPassword.setText("");
                etConfirmPassword.setText("");
                layoutPasswordVerificationStep.setVisibility(View.GONE);
                Toast.makeText(EditProfileActivity.this, "Password updated successfully!", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String errorMessage) {
                btnUpdatePassword.setEnabled(true);
                btnUpdatePassword.setText("Update Password");
                setInlineError(tvNonceError, errorMessage);
            }
        });
    }

    private void setupTextWatchers() {
        etEditName.addTextChangedListener(new SimpleTextWatcher(() -> {
            if (validationAttemptedInfo) validateName();
        }));
        etEditCourse.addTextChangedListener(new SimpleTextWatcher(() -> {
            if (validationAttemptedInfo) validateCourse();
        }));
        etEditEmail.addTextChangedListener(new SimpleTextWatcher(() -> {
            if (validationAttemptedEmail) validateEmail();
        }));
        etPasswordNonce.addTextChangedListener(new SimpleTextWatcher(() -> {
            if (validationAttemptedPassword) validateNonce();
        }));
        etNewPassword.addTextChangedListener(new SimpleTextWatcher(() -> {
            if (validationAttemptedPassword) validateNewPassword();
        }));
        etConfirmPassword.addTextChangedListener(new SimpleTextWatcher(() -> {
            if (validationAttemptedPassword) validateConfirmPassword();
        }));
    }

    private boolean validateName() {
        String name = etEditName.getText().toString().trim();
        String error = name.isEmpty() ? "Please enter your name." : null;
        setInlineError(tvNameError, error);
        return error == null;
    }

    private boolean validateCourse() {
        String course = etEditCourse.getText().toString().trim();
        String error = course.isEmpty() ? "Please enter your course/program." : null;
        setInlineError(tvCourseError, error);
        return error == null;
    }

    private boolean validateEmail() {
        String email = etEditEmail.getText().toString().trim();
        String currentEmail = client.getUserEmail();
        String error;
        if (email.isEmpty()) {
            error = "Please enter your email address.";
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            error = "Enter a valid email address.";
        } else if (email.equalsIgnoreCase(currentEmail.trim())) {
            error = "New email is identical to your current email.";
        } else {
            error = null;
        }
        setInlineError(tvEmailError, error);
        return error == null;
    }

    private boolean validateNonce() {
        String nonce = etPasswordNonce.getText().toString().trim();
        String error = nonce.isEmpty() ? "Please enter the verification code sent to your email." : null;
        setInlineError(tvNonceError, error);
        return error == null;
    }

    private boolean validateNewPassword() {
        String password = etNewPassword.getText().toString();
        String error = password.isEmpty() ? "Please enter a new password." : passwordError(password);
        setInlineError(tvNewPasswordError, error);
        return error == null;
    }

    private boolean validateConfirmPassword() {
        String password = etNewPassword.getText().toString();
        String confirm = etConfirmPassword.getText().toString();
        String error = confirm.isEmpty()
                ? "Please confirm your new password."
                : (!confirm.equals(password) ? "Passwords do not match." : null);
        setInlineError(tvConfirmPasswordError, error);
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
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                hasSpecial = true;
            }
        }

        if (!hasLetter) return "Include at least one letter.";
        if (!hasDigit) return "Include at least one number.";
        if (!hasSpecial) return "Include at least one special character.";
        return null;
    }

    private void setInlineError(TextView errorView, String message) {
        errorView.setText(message);
        errorView.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    private static class SimpleTextWatcher implements TextWatcher {
        private final Runnable onTextChangedRunnable;

        SimpleTextWatcher(Runnable runnable) {
            this.onTextChangedRunnable = runnable;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (onTextChangedRunnable != null) onTextChangedRunnable.run();
        }

        @Override
        public void afterTextChanged(Editable s) {}
    }
}
