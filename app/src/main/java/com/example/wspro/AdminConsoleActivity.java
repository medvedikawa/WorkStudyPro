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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.ChipGroup;
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
    private AppCompatButton btnSignOut;
    private AppCompatButton tabAccounts;
    private AppCompatButton tabLogs;

    // Sections
    private View sectionAccounts;
    private View sectionLogs;

    // Accounts section views
    private TextView statTotalUsers;
    private TextView statActiveUsers;
    private TextView statInactiveUsers;
    private EditText inputSearch;
    private ChipGroup chipGroupStatusFilter;
    private LinearLayout usersContainer;
    private TextView textEmptyUsers;

    // Debug logs section views
    private Spinner spinnerLogFilter;
    private AppCompatButton btnExportLogs;
    private LinearLayout logsContainer;
    private TextView textEmptyLogs;

    // Cached data
    private final List<JsonObject> allUsers = new ArrayList<>();
    private final List<JsonObject> allLogs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_console);

        supabaseClient = SupabaseClient.getInstance(this);

        initViews();
        setupListeners();
        setupFilters();

        verifyAdminSession();
    }

    private void initViews() {
        textStatus = findViewById(R.id.textStatus);
        btnSignOut = findViewById(R.id.btnSignOut);
        tabAccounts = findViewById(R.id.tabAccounts);
        tabLogs = findViewById(R.id.tabLogs);

        sectionAccounts = findViewById(R.id.sectionAccounts);
        sectionLogs = findViewById(R.id.sectionLogs);

        statTotalUsers = findViewById(R.id.statTotalUsers);
        statActiveUsers = findViewById(R.id.statActiveUsers);
        statInactiveUsers = findViewById(R.id.statInactiveUsers);

        inputSearch = findViewById(R.id.inputSearch);
        chipGroupStatusFilter = findViewById(R.id.chipGroupStatusFilter);
        usersContainer = findViewById(R.id.usersContainer);
        textEmptyUsers = findViewById(R.id.textEmptyUsers);

        spinnerLogFilter = findViewById(R.id.spinnerLogFilter);
        btnExportLogs = findViewById(R.id.btnExportLogs);
        logsContainer = findViewById(R.id.logsContainer);
        textEmptyLogs = findViewById(R.id.textEmptyLogs);
    }

    private void setupListeners() {
        btnSignOut.setOnClickListener(v -> signOut());

        tabAccounts.setOnClickListener(v -> selectTab(0));
        tabLogs.setOnClickListener(v -> selectTab(1));

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
        chipGroupStatusFilter.setOnCheckedStateChangeListener((group, checkedIds) -> renderFilteredUsers());

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
        AppCompatButton[] tabs = {tabAccounts, tabLogs};
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == selectedTab;
            tabs[i].setBackgroundResource(selected
                    ? R.drawable.bg_admin_tab_selected : R.drawable.bg_admin_tab_unselected);
            tabs[i].setTextColor(ContextCompat.getColor(this,
                    selected ? android.R.color.white : R.color.admin_mute));
        }
        sectionAccounts.setVisibility(selectedTab == 0 ? View.VISIBLE : View.GONE);
        sectionLogs.setVisibility(selectedTab == 1 ? View.VISIBLE : View.GONE);
    }

    private void verifyAdminSession() {
        statusText("Verifying administrator access");
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "check_role");
        supabaseClient.callAdminConsole(payload, new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(JsonObject result) {
                boolean isAdmin = boolValue(result, "is_admin");
                boolean mfaVerified = boolValue(result, "mfa_verified");
                if (!isAdmin || !mfaVerified) {
                    statusText("Administrator access or MFA verification is no longer valid. Sign in again.");
                    return;
                }
                statusText("Administrator access verified.");
                loadUsers();
                loadLogs();
            }

            @Override
            public void onError(String errorMessage) {
                statusText("Couldn't verify administrator access: " + errorMessage);
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
        int checkedChipId = chipGroupStatusFilter.getCheckedChipId();
        boolean filterActive = checkedChipId == R.id.chipFilterActive;
        boolean filterArchived = checkedChipId == R.id.chipFilterArchived;

        usersContainer.removeAllViews();
        int activeCount = 0;
        int archivedCount = 0;
        int matchedCount = 0;

        for (JsonObject user : allUsers) {
            boolean active = boolValue(user, "is_active");
            if (active) {
                activeCount++;
            } else {
                archivedCount++;
            }

            String name = value(user, "full_name");
            String email = value(user, "email");

            // Filter by status chip
            if (filterActive && !active) {
                continue;
            }
            if (filterArchived && active) {
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
        statInactiveUsers.setText(String.valueOf(archivedCount));

        textEmptyUsers.setVisibility(matchedCount == 0 ? View.VISIBLE : View.GONE);
    }

    private void addUserCard(JsonObject user) {
        String userId = value(user, "id");
        String email = value(user, "email");
        String name = value(user, "full_name");
        boolean active = boolValue(user, "is_active");

        String displayName = name.isEmpty() ? email : name;

        View card = LayoutInflater.from(this).inflate(R.layout.item_admin_user, usersContainer, false);

        TextView userAvatar = card.findViewById(R.id.userAvatar);
        TextView userName = card.findViewById(R.id.userName);
        TextView userEmail = card.findViewById(R.id.userEmail);
        TextView userStatusPill = card.findViewById(R.id.userStatusPill);
        AppCompatButton btnResetPassword = card.findViewById(R.id.btnResetPassword);
        AppCompatButton btnToggleActive = card.findViewById(R.id.btnToggleActive);

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
            btnToggleActive.setText("Archive");
            btnToggleActive.setTextColor(ContextCompat.getColor(this, R.color.admin_ink));
            btnToggleActive.setBackgroundResource(R.drawable.bg_admin_btn);
        } else {
            userStatusPill.setText("Archived");
            userStatusPill.setBackgroundResource(R.drawable.bg_admin_pill_inactive);
            userStatusPill.setTextColor(ContextCompat.getColor(this, R.color.admin_suspended_text));
            btnToggleActive.setText("Restore");
            btnToggleActive.setTextColor(ContextCompat.getColor(this, R.color.admin_ink));
            btnToggleActive.setBackgroundResource(R.drawable.bg_admin_btn);
        }

        btnResetPassword.setOnClickListener(v -> showSetPasswordDialog(userId, displayName));
        btnToggleActive.setOnClickListener(v -> confirmToggleActive(userId, displayName, active));

        usersContainer.addView(card);
    }

    private void setUserActive(String userId, boolean active) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", "set_user_active");
        payload.addProperty("user_id", userId);
        payload.addProperty("is_active", active);
        runAction(payload, successMessage(active ? "User restored." : "User archived."));
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
            title.setText("Archive account?");
            message.setText(displayName + " will be signed out and blocked from logging in.");
            btnAction.setText("Archive");
            btnAction.setBackgroundResource(R.drawable.bg_admin_btn_danger);
            btnAction.setTextColor(ContextCompat.getColor(this, R.color.admin_raspberry));
        } else {
            title.setText("Restore account?");
            message.setText(displayName + " will be able to log in again.");
            btnAction.setText("Restore");
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
