package com.example.wspro;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class AdminConsoleActivity extends AppCompatActivity {

    private SupabaseClient supabaseClient;

    // Header & Navigation
    private TextView textStatus;
    private AppCompatButton btnRefresh;
    private AppCompatButton btnSignOut;
    private AppCompatButton tabAccounts;
    private AppCompatButton tabLogs;
    private AppCompatButton tabAlgorithm;
    private AppCompatButton btnNormalizeWeights;
    private AppCompatButton btnResetWeights;
    private AppCompatButton btnPublishWeights;

    // Sections
    private View sectionAccounts;
    private View sectionLogs;
    private View sectionAlgorithm;

    // Accounts section views
    private TextView statTotalUsers;
    private TextView statActiveUsers;
    private TextView statInactiveUsers;
    private EditText inputSearch;
    private Spinner spinnerStatusFilter;
    private LinearLayout usersContainer;
    private TextView textEmptyUsers;

    // Debug logs section views
    private Spinner spinnerLogFilter;
    private AppCompatButton btnExportLogs;
    private LinearLayout logsContainer;
    private TextView textEmptyLogs;
    private LinearLayout algorithmWeightsContainer;
    private LinearLayout algorithmThresholdsContainer;
    private TextView textAlgorithmWeightTotal;
    private TextView textAlgorithmUpdatedAt;
    private final List<SeekBar> weightSeekBars = new ArrayList<>();
    private final List<TextView> weightValueLabels = new ArrayList<>();
    private SeekBar moderateThresholdSeekBar;
    private SeekBar highThresholdSeekBar;
    private TextView moderateThresholdValue;
    private TextView highThresholdValue;
    private int[] algorithmWeights = AlgorithmConfig.defaults().getWeights();
    private int moderateLoadPercent = AlgorithmConfig.defaults().moderateLoadPercent;
    private int highLoadPercent = AlgorithmConfig.defaults().highLoadPercent;
    private boolean renderingAlgorithmConfig;

    private static final String[] ALGORITHM_WEIGHT_LABELS = {
            "Deadline proximity", "Task Performance", "Group Work", "Laboratory",
            "Assignment", "Work shift collision"
    };

    // Cached data
    private final List<JsonObject> allUsers = new ArrayList<>();
    private final List<JsonObject> allLogs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_console);

        supabaseClient = SupabaseClient.getInstance(this);

        initViews();
        setupAlgorithmControls();
        setupListeners();
        setupFilters();

        verifyAdminSession();
    }

    private void initViews() {
        textStatus = findViewById(R.id.textStatus);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnSignOut = findViewById(R.id.btnSignOut);
        tabAccounts = findViewById(R.id.tabAccounts);
        tabLogs = findViewById(R.id.tabLogs);
        tabAlgorithm = findViewById(R.id.tabAlgorithm);

        sectionAccounts = findViewById(R.id.sectionAccounts);
        sectionLogs = findViewById(R.id.sectionLogs);
        sectionAlgorithm = findViewById(R.id.sectionAlgorithm);

        statTotalUsers = findViewById(R.id.statTotalUsers);
        statActiveUsers = findViewById(R.id.statActiveUsers);
        statInactiveUsers = findViewById(R.id.statInactiveUsers);

        inputSearch = findViewById(R.id.inputSearch);
        spinnerStatusFilter = findViewById(R.id.spinnerStatusFilter);
        usersContainer = findViewById(R.id.usersContainer);
        textEmptyUsers = findViewById(R.id.textEmptyUsers);

        spinnerLogFilter = findViewById(R.id.spinnerLogFilter);
        btnExportLogs = findViewById(R.id.btnExportLogs);
        logsContainer = findViewById(R.id.logsContainer);
        textEmptyLogs = findViewById(R.id.textEmptyLogs);
        algorithmWeightsContainer = findViewById(R.id.algorithmWeightsContainer);
        algorithmThresholdsContainer = findViewById(R.id.algorithmThresholdsContainer);
        textAlgorithmWeightTotal = findViewById(R.id.textAlgorithmWeightTotal);
        textAlgorithmUpdatedAt = findViewById(R.id.textAlgorithmUpdatedAt);
        btnNormalizeWeights = findViewById(R.id.btnNormalizeAlgorithmWeights);
        btnResetWeights = findViewById(R.id.btnResetAlgorithmWeights);
        btnPublishWeights = findViewById(R.id.btnPublishAlgorithmWeights);
    }

    private void setupListeners() {
        btnRefresh.setOnClickListener(v -> verifyAdminSession());
        btnSignOut.setOnClickListener(v -> signOut());

        tabAccounts.setOnClickListener(v -> selectTab(0));
        tabLogs.setOnClickListener(v -> selectTab(1));
        tabAlgorithm.setOnClickListener(v -> selectTab(2));

        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderFilteredUsers();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnExportLogs.setOnClickListener(v -> exportLogsToCsv());
    }

    private void setupFilters() {
        String[] statusOptions = new String[]{"All status", "Active", "Inactive"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, statusOptions);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatusFilter.setAdapter(statusAdapter);
        spinnerStatusFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                renderFilteredUsers();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        String[] initialLogOptions = new String[]{"All events"};
        ArrayAdapter<String> logAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, initialLogOptions);
        logAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLogFilter.setAdapter(logAdapter);
        spinnerLogFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                renderFilteredLogs();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void selectTab(int selectedTab) {
        AppCompatButton[] tabs = {tabAccounts, tabLogs, tabAlgorithm};
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == selectedTab;
            tabs[i].setBackgroundResource(selected
                    ? R.drawable.bg_admin_tab_selected : R.drawable.bg_admin_tab_unselected);
            tabs[i].setTextColor(ContextCompat.getColor(this,
                    selected ? android.R.color.white : R.color.admin_mute));
        }
        sectionAccounts.setVisibility(selectedTab == 0 ? View.VISIBLE : View.GONE);
        sectionLogs.setVisibility(selectedTab == 1 ? View.VISIBLE : View.GONE);
        sectionAlgorithm.setVisibility(selectedTab == 2 ? View.VISIBLE : View.GONE);
    }

    private void verifyAdminSession() {
        setBusy(true, "Verifying administrator access");
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "check_role");
        supabaseClient.callAdminConsole(payload, new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(JsonObject result) {
                boolean isAdmin = boolValue(result, "is_admin");
                boolean mfaVerified = boolValue(result, "mfa_verified");
                if (!isAdmin || !mfaVerified) {
                    statusText("Administrator access or MFA verification is no longer valid. Sign in again.");
                    setBusy(false, textStatus.getText().toString());
                    return;
                }
                setBusy(false, "Administrator access verified.");
                loadUsers();
                loadLogs();
                loadAlgorithmConfig();
            }

            @Override
            public void onError(String errorMessage) {
                setBusy(false, "Couldn't verify administrator access: " + errorMessage);
            }
        });
    }

    private void loadUsers() {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "list_users");
        runAction(payload, new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(JsonObject result) {
                JsonElement usersElement = result.get("users");
                if (usersElement == null || !usersElement.isJsonArray()) {
                    statusText("The admin service returned an unreadable user list.");
                    return;
                }
                allUsers.clear();
                JsonArray users = usersElement.getAsJsonArray();
                for (JsonElement element : users) {
                    if (element.isJsonObject()) {
                        allUsers.add(element.getAsJsonObject());
                    }
                }
                renderFilteredUsers();
            }

            @Override
            public void onError(String errorMessage) {
                statusText("Couldn't load users: " + errorMessage);
            }
        });
    }

    private void renderFilteredUsers() {
        String query = inputSearch.getText() != null
                ? inputSearch.getText().toString().trim().toLowerCase(Locale.ROOT)
                : "";
        String statusFilter = spinnerStatusFilter.getSelectedItem() != null
                ? spinnerStatusFilter.getSelectedItem().toString()
                : "All status";

        usersContainer.removeAllViews();
        int activeCount = 0;
        int inactiveCount = 0;
        int matchedCount = 0;

        for (JsonObject user : allUsers) {
            boolean active = boolValue(user, "is_active");
            if (active) {
                activeCount++;
            } else {
                inactiveCount++;
            }

            String name = value(user, "full_name");
            String email = value(user, "email");

            // Filter by status
            if ("Active".equals(statusFilter) && !active) {
                continue;
            }
            if ("Inactive".equals(statusFilter) && active) {
                continue;
            }

            // Filter by query
            if (!query.isEmpty()) {
                String searchTarget = (name + " " + email).toLowerCase(Locale.ROOT);
                if (!searchTarget.contains(query)) {
                    continue;
                }
            }

            matchedCount++;
            addUserCard(user);
        }

        statTotalUsers.setText(String.valueOf(allUsers.size()));
        statActiveUsers.setText(String.valueOf(activeCount));
        statInactiveUsers.setText(String.valueOf(inactiveCount));

        textEmptyUsers.setVisibility(matchedCount == 0 ? View.VISIBLE : View.GONE);
    }

    private void addUserCard(JsonObject user) {
        String userId = value(user, "id");
        String email = value(user, "email");
        String name = value(user, "full_name");
        String course = value(user, "course");
        String role = value(user, "role");
        boolean active = boolValue(user, "is_active");

        String displayName = name.isEmpty() ? email : name;

        View card = LayoutInflater.from(this).inflate(R.layout.item_admin_user, usersContainer, false);

        TextView userAvatar = card.findViewById(R.id.userAvatar);
        TextView userName = card.findViewById(R.id.userName);
        TextView userEmail = card.findViewById(R.id.userEmail);
        TextView userStatusPill = card.findViewById(R.id.userStatusPill);
        TextView userCourse = card.findViewById(R.id.userCourse);
        TextView userRole = card.findViewById(R.id.userRole);
        AppCompatButton btnResetPassword = card.findViewById(R.id.btnResetPassword);
        AppCompatButton btnToggleActive = card.findViewById(R.id.btnToggleActive);
        AppCompatButton btnDelete = card.findViewById(R.id.btnDelete);

        // Avatar initial matching mockup .av style
        String initial = "?";
        if (!name.isEmpty()) {
            initial = name.substring(0, 1).toUpperCase(Locale.ROOT);
        } else if (!email.isEmpty()) {
            initial = email.substring(0, 1).toUpperCase(Locale.ROOT);
        }
        userAvatar.setText(initial);

        // Avoid repeating the email if name is empty or identical to email
        if (name.isEmpty() || name.equalsIgnoreCase(email)) {
            userName.setText(email);
            userEmail.setVisibility(View.GONE);
        } else {
            userName.setText(name);
            userEmail.setText(email);
            userEmail.setVisibility(View.VISIBLE);
        }

        if (active) {
            userStatusPill.setText("Active");
            userStatusPill.setBackgroundResource(R.drawable.bg_admin_pill_active);
            userStatusPill.setTextColor(ContextCompat.getColor(this, R.color.admin_active_text));
            btnToggleActive.setText("Deactivate");
            btnToggleActive.setTextColor(ContextCompat.getColor(this, R.color.admin_ink));
            btnToggleActive.setBackgroundResource(R.drawable.bg_admin_btn);
        } else {
            userStatusPill.setText("Inactive");
            userStatusPill.setBackgroundResource(R.drawable.bg_admin_pill_inactive);
            userStatusPill.setTextColor(ContextCompat.getColor(this, R.color.admin_suspended_text));
            btnToggleActive.setText("Activate");
            btnToggleActive.setTextColor(ContextCompat.getColor(this, R.color.admin_ink));
            btnToggleActive.setBackgroundResource(R.drawable.bg_admin_btn);
        }

        userCourse.setText(course.isEmpty() ? "No course" : course);
        userRole.setText(role.isEmpty() ? "user" : role);

        btnResetPassword.setOnClickListener(v -> showSetPasswordDialog(userId, displayName));
        btnToggleActive.setOnClickListener(v -> confirmToggleActive(userId, displayName, active));
        btnDelete.setOnClickListener(v -> confirmDelete(userId, displayName));

        usersContainer.addView(card);
    }

    private void setUserActive(String userId, boolean active) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "set_user_active");
        payload.addProperty("user_id", userId);
        payload.addProperty("is_active", active);
        runAction(payload, successMessage(active ? "User activated." : "User deactivated."));
    }

    private void showSetPasswordDialog(String userId, String displayName) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_set_password, null);
        TextView subtitle = dialogView.findViewById(R.id.dialogSetPasswordSubtitle);
        EditText newPassword = dialogView.findViewById(R.id.dialogNewPassword);
        EditText confirmPassword = dialogView.findViewById(R.id.dialogConfirmPassword);
        AppCompatButton btnCancel = dialogView.findViewById(R.id.btnDialogCancel);
        AppCompatButton btnConfirm = dialogView.findViewById(R.id.btnDialogConfirm);

        subtitle.setText("Enter a new password for " + displayName + ".");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String password = newPassword.getText() != null ? newPassword.getText().toString() : "";
            String confirmation = confirmPassword.getText() != null ? confirmPassword.getText().toString() : "";
            if (password.length() < 8) {
                Toast.makeText(this, "Password must be at least 8 characters.", Toast.LENGTH_LONG).show();
                return;
            }
            if (!Objects.equals(password, confirmation)) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_LONG).show();
                return;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("action", "set_user_password");
            payload.addProperty("user_id", userId);
            payload.addProperty("new_password", password);
            runAction(payload, successMessage("Password updated."));
            dialog.dismiss();
        });

        dialog.show();
    }

    private void confirmToggleActive(String userId, String displayName, boolean active) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_confirm, null);
        TextView title = dialogView.findViewById(R.id.dialogConfirmTitle);
        TextView message = dialogView.findViewById(R.id.dialogConfirmMessage);
        AppCompatButton btnCancel = dialogView.findViewById(R.id.btnDialogConfirmCancel);
        AppCompatButton btnAction = dialogView.findViewById(R.id.btnDialogConfirmAction);

        if (active) {
            title.setText("Deactivate account?");
            message.setText(displayName + " will be signed out and blocked from logging in.");
            btnAction.setText("Deactivate");
            btnAction.setBackgroundResource(R.drawable.bg_admin_btn_danger);
            btnAction.setTextColor(ContextCompat.getColor(this, R.color.admin_raspberry));
        } else {
            title.setText("Activate account?");
            message.setText(displayName + " will be able to log in again.");
            btnAction.setText("Activate");
            btnAction.setBackgroundResource(R.drawable.bg_admin_btn_primary);
            btnAction.setTextColor(Color.WHITE);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnAction.setOnClickListener(v -> {
            setUserActive(userId, !active);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void confirmDelete(String userId, String displayName) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_confirm, null);
        TextView title = dialogView.findViewById(R.id.dialogConfirmTitle);
        TextView message = dialogView.findViewById(R.id.dialogConfirmMessage);
        AppCompatButton btnCancel = dialogView.findViewById(R.id.btnDialogConfirmCancel);
        AppCompatButton btnAction = dialogView.findViewById(R.id.btnDialogConfirmAction);

        title.setText("Delete account?");
        message.setText("This permanently removes " + displayName + " and all their schedules and tasks. This can’t be undone.");
        btnAction.setText("Delete");
        btnAction.setBackgroundResource(R.drawable.bg_admin_btn_danger);
        btnAction.setTextColor(ContextCompat.getColor(this, R.color.admin_raspberry));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnAction.setOnClickListener(v -> {
            deleteUser(userId);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void deleteUser(String userId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "delete_user");
        payload.addProperty("user_id", userId);
        runAction(payload, successMessage("User deleted."));
    }

    private void setupAlgorithmControls() {
        for (int i = 0; i < ALGORITHM_WEIGHT_LABELS.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(4));

            TextView label = new TextView(this);
            label.setTextColor(ContextCompat.getColor(this, R.color.admin_ink));
            label.setTextSize(14);
            weightValueLabels.add(label);
            row.addView(label);

            SeekBar seekBar = new SeekBar(this);
            seekBar.setMax(100);
            seekBar.setContentDescription(ALGORITHM_WEIGHT_LABELS[i]);
            weightSeekBars.add(seekBar);
            row.addView(seekBar, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (renderingAlgorithmConfig) return;
                    algorithmWeights[index] = progress;
                    updateAlgorithmWeightSummary();
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {}

                @Override
                public void onStopTrackingTouch(SeekBar bar) {}
            });
            algorithmWeightsContainer.addView(row);
        }

        moderateThresholdSeekBar = addThresholdControl(
                "Moderate load starts at", 10, 95, true);
        highThresholdSeekBar = addThresholdControl(
                "High load starts at", 20, 100, false);
        moderateThresholdSeekBar.setOnSeekBarChangeListener(thresholdListener(true));
        highThresholdSeekBar.setOnSeekBarChangeListener(thresholdListener(false));

        btnNormalizeWeights.setOnClickListener(v -> normalizeAlgorithmWeights());
        btnResetWeights.setOnClickListener(v -> renderAlgorithmConfig(AlgorithmConfig.defaults(), null));
        btnPublishWeights.setOnClickListener(v -> confirmPublishAlgorithmConfig());
        renderAlgorithmConfig(AlgorithmConfig.defaults(), null);
    }

    private SeekBar addThresholdControl(String labelText, int minimum, int maximum, boolean moderate) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(4));

        TextView label = new TextView(this);
        label.setTextColor(ContextCompat.getColor(this, R.color.admin_ink));
        label.setTextSize(14);
        label.setText(labelText + ": " + (moderate ? moderateLoadPercent : highLoadPercent) + "%");
        row.addView(label);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(maximum - minimum);
        seekBar.setProgress((moderate ? moderateLoadPercent : highLoadPercent) - minimum);
        seekBar.setContentDescription(labelText);
        row.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        if (moderate) moderateThresholdValue = label;
        else highThresholdValue = label;
        algorithmThresholdsContainer.addView(row);
        return seekBar;
    }

    private SeekBar.OnSeekBarChangeListener thresholdListener(boolean moderate) {
        final int minimum = moderate ? 10 : 20;
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (renderingAlgorithmConfig) return;
                int value = minimum + progress;
                if (moderate) {
                    moderateLoadPercent = value;
                    moderateThresholdValue.setText("Moderate load starts at: " + value + "%");
                } else {
                    highLoadPercent = value;
                    highThresholdValue.setText("High load starts at: " + value + "%");
                }
                updateAlgorithmWeightSummary();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private void renderAlgorithmConfig(AlgorithmConfig config, String updatedAt) {
        renderingAlgorithmConfig = true;
        algorithmWeights = config.getWeights();
        moderateLoadPercent = config.moderateLoadPercent;
        highLoadPercent = config.highLoadPercent;
        for (int i = 0; i < weightSeekBars.size(); i++) {
            weightSeekBars.get(i).setProgress(algorithmWeights[i]);
            weightValueLabels.get(i).setText(ALGORITHM_WEIGHT_LABELS[i] + ": " + algorithmWeights[i] + "%");
        }
        moderateThresholdSeekBar.setProgress(moderateLoadPercent - 10);
        highThresholdSeekBar.setProgress(highLoadPercent - 20);
        moderateThresholdValue.setText("Moderate load starts at: " + moderateLoadPercent + "%");
        highThresholdValue.setText("High load starts at: " + highLoadPercent + "%");
        textAlgorithmUpdatedAt.setText(updatedAt == null || updatedAt.isEmpty()
                ? "Using the default configuration."
                : "Last published: " + updatedAt);
        renderingAlgorithmConfig = false;
        updateAlgorithmWeightSummary();
    }

    private void updateAlgorithmWeightSummary() {
        int total = 0;
        for (int weight : algorithmWeights) total += weight;
        textAlgorithmWeightTotal.setText("Total: " + total + "%");
        boolean valid = total == 100 && moderateLoadPercent < highLoadPercent;
        textAlgorithmWeightTotal.setTextColor(ContextCompat.getColor(this,
                total == 100 ? R.color.admin_green : R.color.admin_raspberry));
        btnPublishWeights.setEnabled(valid);
        btnPublishWeights.setAlpha(valid ? 1f : 0.5f);
    }

    private void normalizeAlgorithmWeights() {
        int total = 0;
        for (int weight : algorithmWeights) total += weight;
        if (total == 0) {
            algorithmWeights = AlgorithmConfig.defaults().getWeights();
        } else {
            int[] normalized = new int[algorithmWeights.length];
            int normalizedTotal = 0;
            for (int i = 0; i < algorithmWeights.length; i++) {
                normalized[i] = (int) Math.round(algorithmWeights[i] * 100.0 / total);
                normalizedTotal += normalized[i];
            }
            for (int i = 0; normalizedTotal != 100; i = (i + 1) % normalized.length) {
                if (normalizedTotal < 100) {
                    normalized[i]++;
                    normalizedTotal++;
                } else if (normalized[i] > 0) {
                    normalized[i]--;
                    normalizedTotal--;
                }
            }
            algorithmWeights = normalized;
        }
        renderingAlgorithmConfig = true;
        for (int i = 0; i < weightSeekBars.size(); i++) {
            weightSeekBars.get(i).setProgress(algorithmWeights[i]);
            weightValueLabels.get(i).setText(ALGORITHM_WEIGHT_LABELS[i] + ": " + algorithmWeights[i] + "%");
        }
        renderingAlgorithmConfig = false;
        updateAlgorithmWeightSummary();
    }

    private void confirmPublishAlgorithmConfig() {
        AlgorithmConfig config;
        try {
            config = new AlgorithmConfig(algorithmWeights, moderateLoadPercent, highLoadPercent);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Publish algorithm settings?")
                .setMessage("All users will receive these ranking weights and load thresholds on their next sync.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Publish", (dialog, which) -> publishAlgorithmConfig(config))
                .show();
    }

    private void loadAlgorithmConfig() {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "get_algorithm_config");
        runAction(payload, new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(JsonObject result) {
                JsonElement configElement = result.get("config");
                if (configElement == null || !configElement.isJsonObject()) {
                    statusText("The admin service returned an unreadable algorithm configuration.");
                    return;
                }
                try {
                    renderAlgorithmConfig(AlgorithmConfig.fromJson(configElement.getAsJsonObject()),
                            value(result, "updated_at"));
                } catch (IllegalArgumentException e) {
                    statusText("The saved algorithm configuration is invalid: " + e.getMessage());
                }
            }

            @Override
            public void onError(String errorMessage) {
                statusText("Couldn't load algorithm settings: " + errorMessage);
            }
        });
    }

    private void publishAlgorithmConfig(AlgorithmConfig config) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "publish_algorithm_config");
        payload.add("config", config.toJson());
        runAction(payload, new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(JsonObject result) {
                try {
                    JsonElement configElement = result.get("config");
                    if (configElement == null || !configElement.isJsonObject()) {
                        throw new IllegalArgumentException("Missing published configuration.");
                    }
                    AlgorithmConfig published = AlgorithmConfig.fromJson(configElement.getAsJsonObject());
                    AlgorithmConfig.save(getApplicationContext(), published);
                    renderAlgorithmConfig(published, value(result, "updated_at"));
                    boolean auditLogged = boolValue(result, "audit_logged");
                    String message = auditLogged
                            ? "Algorithm settings published to all users."
                            : "Settings were published, but the audit log could not be saved.";
                    statusText(message);
                    Toast.makeText(AdminConsoleActivity.this, message,
                            auditLogged ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                } catch (IllegalArgumentException | IllegalStateException e) {
                    statusText("The admin service returned an invalid published configuration.");
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(AdminConsoleActivity.this,
                        "Couldn't publish algorithm settings: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void loadLogs() {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "debug_logs");
        runAction(payload, new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(JsonObject result) {
                JsonElement logsElement = result.get("logs");
                if (logsElement == null || !logsElement.isJsonArray()) {
                    textEmptyLogs.setText("The admin service returned unreadable logs.");
                    textEmptyLogs.setVisibility(View.VISIBLE);
                    return;
                }
                allLogs.clear();
                JsonArray logs = logsElement.getAsJsonArray();
                for (JsonElement element : logs) {
                    if (element.isJsonObject()) {
                        allLogs.add(element.getAsJsonObject());
                    }
                }
                updateLogFilterOptions();
                renderFilteredLogs();
            }

            @Override
            public void onError(String errorMessage) {
                textEmptyLogs.setText("Couldn't load debug logs: " + errorMessage);
                textEmptyLogs.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateLogFilterOptions() {
        Set<String> events = new LinkedHashSet<>();
        events.add("All events");
        for (JsonObject log : allLogs) {
            String event = value(log, "event");
            if (!event.isEmpty()) {
                events.add(event);
            }
        }
        List<String> options = new ArrayList<>(events);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLogFilter.setAdapter(adapter);
    }

    private void renderFilteredLogs() {
        logsContainer.removeAllViews();
        String selectedFilter = spinnerLogFilter.getSelectedItem() != null
                ? spinnerLogFilter.getSelectedItem().toString()
                : "All events";

        int matched = 0;
        for (JsonObject log : allLogs) {
            String event = value(log, "event");
            if (!"All events".equals(selectedFilter) && !selectedFilter.equalsIgnoreCase(event)) {
                continue;
            }

            matched++;
            addLogItem(log);
        }

        textEmptyLogs.setVisibility(matched == 0 ? View.VISIBLE : View.GONE);
    }

    private void addLogItem(JsonObject log) {
        View itemView = LayoutInflater.from(this).inflate(R.layout.item_admin_log, logsContainer, false);

        TextView logLevel = itemView.findViewById(R.id.logLevel);
        TextView logTimestamp = itemView.findViewById(R.id.logTimestamp);
        TextView logActor = itemView.findViewById(R.id.logActor);
        TextView logDetails = itemView.findViewById(R.id.logDetails);

        String event = value(log, "event");
        String createdAt = value(log, "created_at");
        String actorId = value(log, "actor_id");
        JsonElement details = log.get("details");

        String eventUpper = event.toUpperCase(Locale.ROOT);
        logLevel.setText(eventUpper.isEmpty() ? "EVENT" : eventUpper);

        if (eventUpper.contains("ERR") || eventUpper.contains("FAIL") || eventUpper.contains("DELETE")) {
            logLevel.setTextColor(ContextCompat.getColor(this, R.color.admin_raspberry));
        } else if (eventUpper.contains("WARN") || eventUpper.contains("DEACTIVATE")) {
            logLevel.setTextColor(ContextCompat.getColor(this, R.color.admin_amber));
        } else {
            logLevel.setTextColor(ContextCompat.getColor(this, R.color.admin_green));
        }

        logTimestamp.setText(formatLogTimestamp(createdAt));
        logActor.setText(actorId.isEmpty() ? "" : "actor: " + actorId);

        if (details != null && !details.isJsonNull()) {
            logDetails.setText(details.toString());
            logDetails.setVisibility(View.VISIBLE);
        } else {
            logDetails.setVisibility(View.GONE);
        }

        logsContainer.addView(itemView);
    }

    private String formatLogTimestamp(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            int tIndex = raw.indexOf('T');
            if (tIndex >= 0 && raw.length() >= tIndex + 9) {
                return raw.substring(tIndex + 1, tIndex + 9);
            }
        } catch (Exception ignored) {}
        return raw;
    }

    private void exportLogsToCsv() {
        if (allLogs.isEmpty()) {
            Toast.makeText(this, "No logs to export.", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder csv = new StringBuilder("created_at,event,actor_id,details\n");
        for (JsonObject log : allLogs) {
            csv.append("\"").append(value(log, "created_at")).append("\",")
                    .append("\"").append(value(log, "event")).append("\",")
                    .append("\"").append(value(log, "actor_id")).append("\",")
                    .append("\"").append(log.get("details") != null ? log.get("details").toString().replace("\"", "\"\"") : "").append("\"\n");
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("debug_logs_csv", csv.toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Logs copied as CSV to clipboard (" + allLogs.size() + " entries)", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "CSV export completed (" + allLogs.size() + " entries)", Toast.LENGTH_SHORT).show();
        }
    }

    private SupabaseClient.DataCallback<JsonObject> successMessage(String message) {
        return new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(JsonObject result) {
                Toast.makeText(AdminConsoleActivity.this, message, Toast.LENGTH_SHORT).show();
                loadUsers();
                loadLogs();
                loadAlgorithmConfig();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(AdminConsoleActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        };
    }

    private void runAction(JsonObject payload, SupabaseClient.DataCallback<JsonObject> callback) {
        supabaseClient.callAdminConsole(payload, callback);
    }

    private void setBusy(boolean busy, String message) {
        statusText(message);
        btnRefresh.setEnabled(!busy);
    }

    private void statusText(String message) {
        if (textStatus != null) {
            textStatus.setText(message);
        }
    }

    private void signOut() {
        supabaseClient.clearSession();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String value(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private boolean boolValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }
}
