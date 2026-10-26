package com.example.wspro;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class ProfileActivity extends AppCompatActivity {

    private SupabaseClient client;
    private TextView tvWeeklyLoad;
    private TextView tvPendingTasks;
    private TextView tvLastSynced;
    private Button btnSyncNow;
    private EditText etWeeklyLimit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        client = SupabaseClient.getInstance(this);
        AppearanceSetting.bind(this, (Spinner) findViewById(R.id.spinnerAppearance), client);
        TextView name = findViewById(R.id.tvProfileName);
        name.setText(client.getUserName());
        ((TextView) findViewById(R.id.tvProfileEmail)).setText(client.getUserEmail());
        ((TextView) findViewById(R.id.tvProfileCourse)).setText(client.getUserCourse());
        String userName = client.getUserName();
        TextView initial = findViewById(R.id.tvProfileInitial);
        initial.setText(userName == null || userName.trim().isEmpty()
                ? "?" : userName.trim().substring(0, 1).toUpperCase(Locale.getDefault()));

        tvWeeklyLoad = findViewById(R.id.tvProfileWeeklyLoad);
        tvPendingTasks = findViewById(R.id.tvProfilePendingTasks);
        tvLastSynced = findViewById(R.id.tvLastSynced);
        btnSyncNow = findViewById(R.id.btnSyncNow);
        etWeeklyLimit = findViewById(R.id.etWeeklyHoursLimit);
        etWeeklyLimit.setText(String.valueOf(client.getWeeklyHoursLimit()));

        Button btnSaveLimit = findViewById(R.id.btnSaveWeeklyLimit);
        btnSaveLimit.setOnClickListener(v -> saveWeeklyLimit());
        bindAlertSwitch(R.id.switchConflictAlerts, client.isAlertConflictsEnabled(),
                client::setAlertConflictsEnabled);
        bindAlertSwitch(R.id.switchCollisionAlerts, client.isAlertCollisionsEnabled(),
                client::setAlertCollisionsEnabled);
        bindAlertSwitch(R.id.switchBurnoutAlerts, client.isAlertBurnoutEnabled(),
                client::setAlertBurnoutEnabled);

        boolean showSync = client.isLoggedIn() && !client.isDemoSession();
        findViewById(R.id.layoutSyncSection).setVisibility(showSync
                ? android.view.View.VISIBLE : android.view.View.GONE);
        refreshLastSynced();
        btnSyncNow.setOnClickListener(v -> syncNow());

        findViewById(R.id.btnLogOut).setOnClickListener(v -> {
            client.clearSession();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        ScanButtonHelper scanButtonHelper = new ScanButtonHelper(this);
        BottomNavHelper.setup(this, BottomNavHelper.Tab.YOU, scanButtonHelper);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSummary();
    }

    private void bindAlertSwitch(int viewId, boolean initialValue, Consumer<Boolean> onChanged) {
        SwitchCompat alertSwitch = findViewById(viewId);
        alertSwitch.setChecked(initialValue);
        alertSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> onChanged.accept(isChecked));
    }

    private void saveWeeklyLimit() {
        int hours;
        try {
            hours = Integer.parseInt(etWeeklyLimit.getText().toString().trim());
        } catch (NumberFormatException e) {
            etWeeklyLimit.setError("Enter a number from 1 to 168.");
            return;
        }
        if (hours < 1 || hours > 168) {
            etWeeklyLimit.setError("Enter a number from 1 to 168.");
            return;
        }
        client.setWeeklyHoursLimit(hours);
        etWeeklyLimit.setError(null);
        loadSummary();
        Toast.makeText(this, "Weekly hours limit saved.", Toast.LENGTH_SHORT).show();
    }

    private void syncNow() {
        if (!client.isLoggedIn()) {
            Toast.makeText(this, "Log in to sync your data.", Toast.LENGTH_LONG).show();
            return;
        }
        btnSyncNow.setEnabled(false);
        btnSyncNow.setText("Syncing...");
        client.syncAllLocalDataToCloud(this, new SupabaseClient.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                btnSyncNow.setEnabled(true);
                btnSyncNow.setText("Sync now");
                refreshLastSynced();
                Toast.makeText(ProfileActivity.this, "Your data is synced.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String errorMessage) {
                btnSyncNow.setEnabled(true);
                btnSyncNow.setText("Sync now");
                Toast.makeText(ProfileActivity.this,
                        "Couldn't sync right now. Check your connection and try again.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshLastSynced() {
        tvLastSynced.setText(client.getLastSyncTimeString());
    }

    private void loadSummary() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            List<ScheduleItem> schedules = db.scheduleDao().getAll();
            int pendingTasks = db.assignmentDao().getPendingAssignments().size();
            double totalHours = 0;
            for (ScheduleItem item : schedules) {
                int start = ConflictChecker.timeToMinutes(item.getStartTime());
                int end = ConflictChecker.timeToMinutes(item.getEndTime());
                if (end > start) totalHours += (end - start) / 60.0;
            }

            int weeklyLimit = client.getWeeklyHoursLimit();
            String loadStatus = totalHours > weeklyLimit
                    ? "High workload"
                    : totalHours >= weeklyLimit * 0.75
                    ? "Moderate workload"
                    : "Low workload";
            String loadSummary = String.format(Locale.getDefault(),
                    "%s · %.1f / %d hours this week", loadStatus, totalHours, weeklyLimit);

            runOnUiThread(() -> {
                tvWeeklyLoad.setText(loadSummary);
                tvPendingTasks.setText(pendingTasks + (pendingTasks == 1
                        ? " pending task" : " pending tasks"));
            });
        }).start();
    }
}
