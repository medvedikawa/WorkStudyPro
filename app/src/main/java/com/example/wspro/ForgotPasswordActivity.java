package com.example.wspro;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Three-state password recovery flow:
 * State 1: Enter email and trigger Supabase resetPasswordForEmail.
 * State 2: Enter 6-digit OTP code with auto-advance and 30s resend cooldown.
 * State 3: Enter new password with eye toggles, live strength check, and updateUser.
 * Automatically logs the user in upon successful password update.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    public static final int STATE_EMAIL = 1;
    public static final int STATE_CODE = 2;
    public static final int STATE_PASSWORD = 3;

    public static final String EXTRA_SNACKBAR_MESSAGE = "EXTRA_SNACKBAR_MESSAGE";
    private static final int RESEND_COOLDOWN_SECONDS = 30;

    private SupabaseClient supabaseClient;
    private int currentState = STATE_EMAIL;
    private String currentEmail = "";
    private String currentUserId = "";
    private String recoveryAccessToken = "";
    private String recoveryRefreshToken = "";

    // Header views
    private ImageButton btnReturnBack;
    private TextView tvStepIndicator;
    private TextView tvForgotTitle;
    private TextView tvForgotSubtitle;
    private View stepBar1, stepBar2, stepBar3;

    // State 1 views (Email)
    private LinearLayout layoutStateEmail;
    private EditText etForgotEmail;
    private TextView tvEmailError;
    private Button btnSendEmail;
    private TextView tvBackToLogin1;

    // State 2 views (Code)
    private LinearLayout layoutStateCode;
    private TextView tvTargetEmail;
    private TextView tvChangeEmail;
    private CodeInputView codeInputView;
    private TextView tvCodeError;
    private TextView tvResendCode;
    private Button btnVerifyCode;
    private CountDownTimer resendTimer;
    private boolean isTimerRunning = false;

    // State 3 views (New Password)
    private LinearLayout layoutStatePassword;
    private EditText etNewPassword;
    private ImageButton btnToggleNewPassword;
    private EditText etConfirmPassword;
    private ImageButton btnToggleConfirmPassword;
    private TextView tvStrengthLabel;
    private TextView tvNewPasswordError;
    private TextView tvConfirmPasswordError;
    private View strengthBar1, strengthBar2, strengthBar3, strengthBar4;
    private Button btnUpdatePassword;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        supabaseClient = SupabaseClient.getInstance(this);

        initViews();
        setupListeners();
        setupBackPressed();

        switchToState(STATE_EMAIL);
    }

    private void initViews() {
        btnReturnBack = findViewById(R.id.btnReturnBack);
        tvStepIndicator = findViewById(R.id.tvStepIndicator);
        tvForgotTitle = findViewById(R.id.tvForgotTitle);
        tvForgotSubtitle = findViewById(R.id.tvForgotSubtitle);
        stepBar1 = findViewById(R.id.stepBar1);
        stepBar2 = findViewById(R.id.stepBar2);
        stepBar3 = findViewById(R.id.stepBar3);

        // State 1
        layoutStateEmail = findViewById(R.id.layoutStateEmail);
        etForgotEmail = findViewById(R.id.etForgotEmail);
        tvEmailError = findViewById(R.id.tvEmailError);
        btnSendEmail = findViewById(R.id.btnSendEmail);
        tvBackToLogin1 = findViewById(R.id.tvBackToLogin1);

        // State 2
        layoutStateCode = findViewById(R.id.layoutStateCode);
        tvTargetEmail = findViewById(R.id.tvTargetEmail);
        tvChangeEmail = findViewById(R.id.tvChangeEmail);
        codeInputView = findViewById(R.id.codeInputView);
        tvCodeError = findViewById(R.id.tvCodeError);
        tvResendCode = findViewById(R.id.tvResendCode);
        btnVerifyCode = findViewById(R.id.btnVerifyCode);

        // State 3
        layoutStatePassword = findViewById(R.id.layoutStatePassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        btnToggleNewPassword = findViewById(R.id.btnToggleNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnToggleConfirmPassword = findViewById(R.id.btnToggleConfirmPassword);
        tvStrengthLabel = findViewById(R.id.tvStrengthLabel);
        tvNewPasswordError = findViewById(R.id.tvNewPasswordError);
        tvConfirmPasswordError = findViewById(R.id.tvConfirmPasswordError);
        strengthBar1 = findViewById(R.id.strengthBar1);
        strengthBar2 = findViewById(R.id.strengthBar2);
        strengthBar3 = findViewById(R.id.strengthBar3);
        strengthBar4 = findViewById(R.id.strengthBar4);
        btnUpdatePassword = findViewById(R.id.btnUpdatePassword);
    }

    private void setupListeners() {
        btnReturnBack.setOnClickListener(v -> handleBackNavigation());
        tvBackToLogin1.setOnClickListener(v -> finish());

        // State 1 Listeners
        etForgotEmail.addTextChangedListener(new SimpleTextWatcher(() -> setInlineError(tvEmailError, null)));
        btnSendEmail.setOnClickListener(v -> handleSendRecoveryEmail());

        // State 2 Listeners
        tvChangeEmail.setOnClickListener(v -> switchToState(STATE_EMAIL));
        tvResendCode.setOnClickListener(v -> handleResendCode());
        btnVerifyCode.setOnClickListener(v -> handleVerifyCode());
        codeInputView.setOnCodeCompleteListener(new CodeInputView.OnCodeCompleteListener() {
            @Override
            public void onCodeComplete(@NonNull String code) {
                setInlineError(tvCodeError, null);
                codeInputView.setError(false);
            }

            @Override
            public void onCodeChanged(@NonNull String code) {
                setInlineError(tvCodeError, null);
                codeInputView.setError(false);
            }
        });

        // State 3 Listeners
        btnToggleNewPassword.setOnClickListener(v -> togglePasswordVisibility(etNewPassword, btnToggleNewPassword));
        btnToggleConfirmPassword.setOnClickListener(v -> togglePasswordVisibility(etConfirmPassword, btnToggleConfirmPassword));

        etNewPassword.addTextChangedListener(new SimpleTextWatcher(() -> {
            setInlineError(tvNewPasswordError, null);
            updatePasswordStrength(etNewPassword.getText().toString());
        }));

        etConfirmPassword.addTextChangedListener(
                new SimpleTextWatcher(() -> setInlineError(tvConfirmPasswordError, null)));

        btnUpdatePassword.setOnClickListener(v -> handleUpdatePassword());
    }

    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackNavigation();
            }
        });
    }

    private void handleBackNavigation() {
        if (currentState == STATE_PASSWORD) {
            switchToState(STATE_CODE);
        } else if (currentState == STATE_CODE) {
            switchToState(STATE_EMAIL);
        } else {
            finish();
        }
    }

    // --- STATE MACHINE ---

    private void switchToState(int targetState) {
        this.currentState = targetState;

        int activeColor = ContextCompat.getColor(this, R.color.ws_accent);
        int inactiveColor = ContextCompat.getColor(this, R.color.ws_border);

        layoutStateEmail.setVisibility(targetState == STATE_EMAIL ? View.VISIBLE : View.GONE);
        layoutStateCode.setVisibility(targetState == STATE_CODE ? View.VISIBLE : View.GONE);
        layoutStatePassword.setVisibility(targetState == STATE_PASSWORD ? View.VISIBLE : View.GONE);

        switch (targetState) {
            case STATE_EMAIL:
                tvStepIndicator.setText(getString(R.string.forgot_step_indicator, 1));
                tvForgotTitle.setText("Reset Password");
                tvForgotSubtitle.setText("Enter your registered email address to receive a recovery code.");
                stepBar1.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                stepBar2.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
                stepBar3.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
                etForgotEmail.requestFocus();
                break;

            case STATE_CODE:
                tvStepIndicator.setText(getString(R.string.forgot_step_indicator, 2));
                tvForgotTitle.setText("Verify Code");
                tvForgotSubtitle.setText("Enter the 6-digit recovery code sent to your email.");
                stepBar1.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                stepBar2.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                stepBar3.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
                tvTargetEmail.setText(currentEmail);
                codeInputView.clearCode();
                codeInputView.requestInputFocus();
                startResendCooldown();
                break;

            case STATE_PASSWORD:
                tvStepIndicator.setText(getString(R.string.forgot_step_indicator, 3));
                tvForgotTitle.setText("Create New Password");
                tvForgotSubtitle.setText("Set a strong new password for your account.");
                stepBar1.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                stepBar2.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                stepBar3.setBackgroundTintList(ColorStateList.valueOf(activeColor));
                etNewPassword.requestFocus();
                updatePasswordStrength(etNewPassword.getText().toString());
                break;
        }
    }

    // --- STATE 1: SEND RECOVERY EMAIL ---

    private void handleSendRecoveryEmail() {
        String email = etForgotEmail.getText().toString().trim();

        if (email.isEmpty()) {
            setInlineError(tvEmailError, "Please enter your email address.");
            etForgotEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setInlineError(tvEmailError, "Please enter a valid email address (e.g. name@school.edu).");
            etForgotEmail.requestFocus();
            return;
        }

        if (!SupabaseClient.isNetworkAvailable(this)) {
            setInlineError(tvEmailError, "No internet connection. Please check your network and try again.");
            return;
        }

        setInlineError(tvEmailError, null);
        btnSendEmail.setEnabled(false);
        btnSendEmail.setText("Sending Code...");

        supabaseClient.resetPasswordForEmail(email, new SupabaseClient.AuthCallback() {
            @Override
            public void onSuccess(String userId, String userEmail, String accessToken) {
                btnSendEmail.setEnabled(true);
                btnSendEmail.setText("Send Recovery Code");
                currentEmail = email;
                switchToState(STATE_CODE);
                Toast.makeText(ForgotPasswordActivity.this, "Verification code sent to " + email, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                btnSendEmail.setEnabled(true);
                btnSendEmail.setText("Send Recovery Code");
                setInlineError(tvEmailError, errorMessage);
            }
        });
    }

    // --- STATE 2: VERIFY OTP CODE ---

    private void handleVerifyCode() {
        String code = codeInputView.getCode();

        if (code.length() < codeInputView.getCodeLength()) {
            setInlineError(tvCodeError, "Please enter the complete 6-digit code.");
            codeInputView.setError(true);
            return;
        }

        if (!SupabaseClient.isNetworkAvailable(this)) {
            setInlineError(tvCodeError, "No internet connection. Please check your network and try again.");
            return;
        }

        setInlineError(tvCodeError, null);
        codeInputView.setError(false);
        btnVerifyCode.setEnabled(false);
        btnVerifyCode.setText("Verifying Code...");

        supabaseClient.verifyOtp(currentEmail, code, "recovery", new SupabaseClient.RecoveryCallback() {
            @Override
            public void onSuccess(String userId, String email, String accessToken, String refreshToken) {
                btnVerifyCode.setEnabled(true);
                btnVerifyCode.setText("Verify Code");
                currentUserId = userId;
                if (email != null && !email.isEmpty()) currentEmail = email;
                recoveryAccessToken = accessToken;
                recoveryRefreshToken = refreshToken;
                cancelResendTimer();
                switchToState(STATE_PASSWORD);
            }

            @Override
            public void onError(String errorMessage) {
                btnVerifyCode.setEnabled(true);
                btnVerifyCode.setText("Verify Code");
                codeInputView.setError(true);
                setInlineError(tvCodeError, errorMessage);
            }
        });
    }

    private void handleResendCode() {
        if (isTimerRunning) return;

        if (!SupabaseClient.isNetworkAvailable(this)) {
            setInlineError(tvCodeError, "No internet connection. Please check your network and try again.");
            return;
        }

        setInlineError(tvCodeError, null);
        codeInputView.setError(false);
        startResendCooldown();

        supabaseClient.resetPasswordForEmail(currentEmail, new SupabaseClient.AuthCallback() {
            @Override
            public void onSuccess(String userId, String email, String accessToken) {
                Toast.makeText(ForgotPasswordActivity.this, "A fresh verification code has been sent.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                setInlineError(tvCodeError, errorMessage);
            }
        });
    }

    private void startResendCooldown() {
        cancelResendTimer();
        isTimerRunning = true;
        tvResendCode.setEnabled(false);
        tvResendCode.setTextColor(ContextCompat.getColor(this, R.color.ws_muted));

        resendTimer = new CountDownTimer(RESEND_COOLDOWN_SECONDS * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = (millisUntilFinished / 1000L) + 1;
                tvResendCode.setText(getString(R.string.forgot_resend_countdown, seconds));
            }

            @Override
            public void onFinish() {
                isTimerRunning = false;
                tvResendCode.setEnabled(true);
                tvResendCode.setText("Resend code");
                tvResendCode.setTextColor(ContextCompat.getColor(ForgotPasswordActivity.this, R.color.ws_accent));
            }
        }.start();
    }

    private void cancelResendTimer() {
        if (resendTimer != null) {
            resendTimer.cancel();
            resendTimer = null;
        }
        isTimerRunning = false;
    }

    // --- STATE 3: NEW PASSWORD & STRENGTH CHECK ---

    private void handleUpdatePassword() {
        String newPassword = etNewPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        String passwordError = evaluatePasswordRules(newPassword);
        if (passwordError != null) {
            setInlineError(tvNewPasswordError, passwordError);
            etNewPassword.requestFocus();
            return;
        }
        setInlineError(tvNewPasswordError, null);

        if (confirmPassword.isEmpty()) {
            setInlineError(tvConfirmPasswordError, "Please confirm your new password.");
            etConfirmPassword.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            setInlineError(tvConfirmPasswordError, "Passwords do not match.");
            etConfirmPassword.requestFocus();
            return;
        }
        setInlineError(tvConfirmPasswordError, null);

        if (!SupabaseClient.isNetworkAvailable(this)) {
            setInlineError(tvNewPasswordError, "No internet connection. Please check your network and try again.");
            return;
        }

        btnUpdatePassword.setEnabled(false);
        btnUpdatePassword.setText("Updating Password...");

        supabaseClient.updateUser(newPassword, recoveryAccessToken, new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(Boolean result) {
                btnUpdatePassword.setEnabled(true);
                btnUpdatePassword.setText("Update Password");

                // Automatically log user in with session credentials
                String name = supabaseClient.getUserName();
                String course = supabaseClient.getUserCourse();
                supabaseClient.saveSession(recoveryAccessToken, recoveryRefreshToken,
                        currentUserId, currentEmail, name, course);

                Intent intent = new Intent(ForgotPasswordActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                Toast.makeText(ForgotPasswordActivity.this, "Password updated! Welcome back.", Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                btnUpdatePassword.setEnabled(true);
                btnUpdatePassword.setText("Update Password");
                setInlineError(tvNewPasswordError, errorMessage);
            }
        });
    }

    private String evaluatePasswordRules(String password) {
        if (password == null || password.isEmpty()) {
            return "Please enter a new password.";
        }
        if (password.length() < 8) {
            return "Password must be at least 8 characters.";
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                hasSpecial = true;
            }
        }

        if (!hasLetter) return "Password must include at least one letter.";
        if (!hasDigit) return "Password must include at least one number.";
        if (!hasSpecial) return "Password must include at least one special character.";

        return null;
    }

    private void updatePasswordStrength(String password) {
        int inactive = ContextCompat.getColor(this, R.color.ws_border);
        int weakColor = ContextCompat.getColor(this, R.color.ws_error);
        int fairColor = ContextCompat.getColor(this, R.color.ws_warning);
        int goodColor = ContextCompat.getColor(this, R.color.ws_accent);
        int strongColor = ContextCompat.getColor(this, R.color.ws_success);

        if (password == null || password.isEmpty()) {
            strengthBar1.setBackgroundTintList(ColorStateList.valueOf(inactive));
            strengthBar2.setBackgroundTintList(ColorStateList.valueOf(inactive));
            strengthBar3.setBackgroundTintList(ColorStateList.valueOf(inactive));
            strengthBar4.setBackgroundTintList(ColorStateList.valueOf(inactive));
            tvStrengthLabel.setText("Enter password");
            tvStrengthLabel.setTextColor(ContextCompat.getColor(this, R.color.ws_muted));
            return;
        }

        int score = calculatePasswordScore(password);

        if (score <= 1) {
            strengthBar1.setBackgroundTintList(ColorStateList.valueOf(weakColor));
            strengthBar2.setBackgroundTintList(ColorStateList.valueOf(inactive));
            strengthBar3.setBackgroundTintList(ColorStateList.valueOf(inactive));
            strengthBar4.setBackgroundTintList(ColorStateList.valueOf(inactive));
            tvStrengthLabel.setText("Weak");
            tvStrengthLabel.setTextColor(weakColor);
        } else if (score == 2) {
            strengthBar1.setBackgroundTintList(ColorStateList.valueOf(fairColor));
            strengthBar2.setBackgroundTintList(ColorStateList.valueOf(fairColor));
            strengthBar3.setBackgroundTintList(ColorStateList.valueOf(inactive));
            strengthBar4.setBackgroundTintList(ColorStateList.valueOf(inactive));
            tvStrengthLabel.setText("Fair");
            tvStrengthLabel.setTextColor(fairColor);
        } else if (score == 3) {
            strengthBar1.setBackgroundTintList(ColorStateList.valueOf(goodColor));
            strengthBar2.setBackgroundTintList(ColorStateList.valueOf(goodColor));
            strengthBar3.setBackgroundTintList(ColorStateList.valueOf(goodColor));
            strengthBar4.setBackgroundTintList(ColorStateList.valueOf(inactive));
            tvStrengthLabel.setText("Good");
            tvStrengthLabel.setTextColor(goodColor);
        } else {
            strengthBar1.setBackgroundTintList(ColorStateList.valueOf(strongColor));
            strengthBar2.setBackgroundTintList(ColorStateList.valueOf(strongColor));
            strengthBar3.setBackgroundTintList(ColorStateList.valueOf(strongColor));
            strengthBar4.setBackgroundTintList(ColorStateList.valueOf(strongColor));
            tvStrengthLabel.setText("Strong");
            tvStrengthLabel.setTextColor(strongColor);
        }
    }

    private int calculatePasswordScore(String password) {
        if (password == null || password.length() < 8) return 1;

        int score = 1;
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) hasSpecial = true;
        }

        if (hasLower || hasUpper) score++;
        if (hasDigit) score++;
        if (hasSpecial) score++;
        if (hasLower && hasUpper && score < 4) score++;

        return score;
    }

    private void togglePasswordVisibility(EditText editText, ImageButton toggleButton) {
        if (editText.getTransformationMethod() instanceof PasswordTransformationMethod) {
            editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            toggleButton.setImageResource(R.drawable.ic_eye_off);
        } else {
            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            toggleButton.setImageResource(R.drawable.ic_eye);
        }
        editText.setSelection(editText.getText().length());
    }

    private void setInlineError(TextView textView, @Nullable String error) {
        if (error == null || error.trim().isEmpty()) {
            textView.setText("");
            textView.setVisibility(View.GONE);
        } else {
            textView.setText(error);
            textView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelResendTimer();
    }

    private static class SimpleTextWatcher implements TextWatcher {
        private final Runnable action;

        public SimpleTextWatcher(Runnable action) {
            this.action = action;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged(Editable s) {
            if (action != null) action.run();
        }
    }
}
