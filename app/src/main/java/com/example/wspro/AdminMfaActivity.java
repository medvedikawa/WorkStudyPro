package com.example.wspro;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class AdminMfaActivity extends AppCompatActivity {

    private static final String STATE_FACTOR_ID = "factor_id";
    private static final String STATE_TOTP_URI = "totp_uri";
    private static final String STATE_SECRET = "secret";
    private SupabaseClient supabaseClient;
    private LinearLayout content;
    private TextView status;
    private EditText codeInput;
    private String factorId;
    private String totpUri;
    private String secret;
    private boolean enrolling;
    private Button verifyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supabaseClient = SupabaseClient.getInstance(this);
        buildScreen();
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_FACTOR_ID)) {
            factorId = savedInstanceState.getString(STATE_FACTOR_ID);
            totpUri = savedInstanceState.getString(STATE_TOTP_URI);
            secret = savedInstanceState.getString(STATE_SECRET);
            enrolling = totpUri != null && !totpUri.isEmpty();
            showVerificationForm();
        } else {
            loadFactors();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (factorId != null) outState.putString(STATE_FACTOR_ID, factorId);
        if (totpUri != null) outState.putString(STATE_TOTP_URI, totpUri);
        if (secret != null) outState.putString(STATE_SECRET, secret);
    }

    private void buildScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(28), dp(24), dp(28));
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setBackgroundColor(Color.rgb(248, 249, 252));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scrollView);

        TextView title = text("Admin verification", 26, true);
        content.addView(title, matchWrap());
        TextView description = text("Use an authenticator app to verify your administrator account.", 16, false);
        description.setGravity(Gravity.CENTER);
        addSpaced(description, 12);
        status = text("Checking your authenticator setup...", 15, false);
        status.setGravity(Gravity.CENTER);
        addSpaced(status, 20);

        codeInput = new EditText(this);
        codeInput.setSingleLine(true);
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        codeInput.setHint("6-digit code");
        codeInput.setGravity(Gravity.CENTER);
        codeInput.setTextSize(20);
        codeInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        LinearLayout.LayoutParams codeParams = matchWrap();
        codeParams.bottomMargin = dp(12);
        content.addView(codeInput, codeParams);

        verifyButton = button("Verify and continue");
        verifyButton.setOnClickListener(v -> verifyCode());
        content.addView(verifyButton, matchWrap());

        Button signOut = button("Sign out");
        signOut.setOnClickListener(v -> signOut());
        LinearLayout.LayoutParams signOutParams = matchWrap();
        signOutParams.topMargin = dp(12);
        content.addView(signOut, signOutParams);
    }

    private void loadFactors() {
        setBusy(true, "Checking your authenticator setup...");
        supabaseClient.loadMfaFactors(new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(JsonArray factors) {
                for (JsonElement element : factors) {
                    if (!element.isJsonObject()) continue;
                    JsonObject factor = element.getAsJsonObject();
                    if ("totp".equals(value(factor, "factor_type"))
                            && "verified".equals(value(factor, "status"))) {
                        factorId = value(factor, "id");
                        if (factorId.isEmpty()) {
                            setBusy(false, "The verified authenticator factor has no identifier.");
                            return;
                        }
                        enrolling = false;
                        showVerificationForm();
                        return;
                    }
                }

                List<String> unverifiedFactorIds = new ArrayList<>();
                for (JsonElement element : factors) {
                    if (!element.isJsonObject()) continue;
                    JsonObject factor = element.getAsJsonObject();
                    String id = value(factor, "id");
                    if (!id.isEmpty() && !"verified".equals(value(factor, "status"))) {
                        unverifiedFactorIds.add(id);
                    }
                }
                deleteUnverifiedFactors(unverifiedFactorIds, 0);
            }

            @Override
            public void onError(String errorMessage) {
                setBusy(false, errorMessage);
                verifyButton.setEnabled(false);
            }
        });
    }

    private void beginEnrollment() {
        setBusy(true, "Preparing authenticator enrollment...");
        supabaseClient.enrollTotp(new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(JsonObject result) {
                factorId = value(result, "id");
                enrolling = true;
                JsonElement totpElement = result.get("totp");
                if (factorId.isEmpty() || totpElement == null || !totpElement.isJsonObject()) {
                    factorId = null;
                    setBusy(false, "The authenticator setup response was incomplete.");
                    return;
                }
                JsonObject totp = totpElement.getAsJsonObject();
                totpUri = value(totp, "uri");
                secret = value(totp, "secret");
                if (totpUri.isEmpty() || secret.isEmpty()) {
                    factorId = null;
                    setBusy(false, "The authenticator setup did not include a QR URI or secret.");
                    return;
                }
                showVerificationForm();
            }

            @Override
            public void onError(String errorMessage) {
                setBusy(false, errorMessage);
                verifyButton.setEnabled(false);
            }
        });
    }

    private void showVerificationForm() {
        setBusy(false, enrolling
                ? "Scan this QR code with your authenticator app, then enter its 6-digit code."
                : "Enter the 6-digit code from your authenticator app.");
        if (enrolling) {
            int secretIndex = 3;
            try {
                ImageView qrView = new ImageView(this);
                qrView.setImageBitmap(createQrBitmap(totpUri));
                qrView.setContentDescription("Authenticator enrollment QR code");
                LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(240), dp(240));
                qrParams.gravity = Gravity.CENTER_HORIZONTAL;
                qrParams.bottomMargin = dp(12);
                content.addView(qrView, secretIndex++, qrParams);
            } catch (WriterException | IllegalArgumentException e) {
                status.setText("Couldn't display the QR code. Enter the setup key below instead.");
            }
            TextView secretLabel = text("Setup key", 14, true);
            secretLabel.setGravity(Gravity.CENTER);
            addAt(secretLabel, secretIndex, 4);
            TextView secretValue = text(secret, 16, false);
            secretValue.setGravity(Gravity.CENTER);
            secretValue.setTextIsSelectable(true);
            addAt(secretValue, secretIndex + 1, 4);
        }
    }

    private void verifyCode() {
        String code = codeInput.getText().toString().trim();
        if (!code.matches("\\d{6}")) {
            codeInput.setError("Enter the 6-digit authenticator code.");
            return;
        }
        if (factorId == null || factorId.isEmpty()) {
            setBusy(false, "No authenticator factor is available. Sign in again to retry setup.");
            return;
        }
        setBusy(true, "Verifying code...");
        supabaseClient.challengeTotp(factorId, new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(String challengeId) {
                supabaseClient.verifyTotp(factorId, challengeId, code,
                        new SupabaseClient.DataCallback<>() {
                            @Override
                            public void onSuccess(Boolean verified) {
                                supabaseClient.refreshAuthenticatedSession(new SupabaseClient.DataCallback<>() {
                                    @Override
                                    public void onSuccess(Boolean refreshed) {
                                        Intent intent = new Intent(AdminMfaActivity.this,
                                                AdminConsoleActivity.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();
                                    }

                                    @Override
                                    public void onError(String errorMessage) {
                                        setBusy(false, errorMessage);
                                    }
                                });
                            }

                            @Override
                            public void onError(String errorMessage) {
                                setBusy(false, errorMessage);
                            }
                        });
            }

            @Override
            public void onError(String errorMessage) {
                setBusy(false, errorMessage);
            }
        });
    }

    private void signOut() {
        supabaseClient.clearSession();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private Bitmap createQrBitmap(String uri) throws WriterException {
        BitMatrix matrix = new QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 512, 512);
        Bitmap bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.ARGB_8888);
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }

    private void setBusy(boolean busy, String message) {
        status.setText(message);
        verifyButton.setEnabled(!busy && factorId != null && !factorId.isEmpty()
                && (!enrolling || (secret != null && !secret.isEmpty())));
        codeInput.setEnabled(!busy);
    }

    private void addSpaced(View view, int topMarginDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(topMarginDp);
        content.addView(view, params);
    }

    private void addAt(View view, int index, int topMarginDp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(topMarginDp);
        content.addView(view, Math.min(index, content.getChildCount() - 2), params);
    }

    private TextView text(String value, int sizeSp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(Color.rgb(30, 38, 56));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String value(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private void deleteUnverifiedFactors(List<String> factorIds, int index) {
        if (index >= factorIds.size()) {
            beginEnrollment();
            return;
        }
        supabaseClient.deleteMfaFactor(factorIds.get(index), new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(Boolean deleted) {
                if (Boolean.TRUE.equals(deleted)) {
                    deleteUnverifiedFactors(factorIds, index + 1);
                } else {
                    setBusy(false, "Couldn't remove the incomplete authenticator setup.");
                    verifyButton.setEnabled(false);
                }
            }

            @Override
            public void onError(String errorMessage) {
                setBusy(false, errorMessage);
                verifyButton.setEnabled(false);
            }
        });
    }
}
