package com.example.wspro;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.function.Consumer;

public class SettingsActivity extends AppCompatActivity {

    private SupabaseClient client;
    private TextView tvLastSynced;
    private Button btnSyncNow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        client = SupabaseClient.getInstance(this);
        AppearanceSetting.bind(this, (Spinner) findViewById(R.id.spinnerAppearance), client);
        EditText etWeeklyLimit = findViewById(R.id.etWeeklyHoursLimit);
        etWeeklyLimit.setText(String.valueOf(client.getWeeklyHoursLimit()));
        tvLastSynced = findViewById(R.id.tvLastSynced);
        btnSyncNow = findViewById(R.id.btnSyncNow);
        refreshLastSynced();

        ImageButton btnBack = findViewById(R.id.btnSettingsBack);
        btnBack.setOnClickListener(v -> finish());

        Button btnSaveLimit = findViewById(R.id.btnSaveWeeklyLimit);
        btnSaveLimit.setOnClickListener(v -> saveWeeklyLimit(etWeeklyLimit));

        bindAlertSwitch(R.id.switchConflictAlerts, client.isAlertConflictsEnabled(),
                client::setAlertConflictsEnabled);
        bindAlertSwitch(R.id.switchCollisionAlerts, client.isAlertCollisionsEnabled(),
                client::setAlertCollisionsEnabled);
        bindAlertSwitch(R.id.switchBurnoutAlerts, client.isAlertBurnoutEnabled(),
                client::setAlertBurnoutEnabled);

        btnSyncNow.setOnClickListener(v -> syncNow());
    }

    private void bindAlertSwitch(int viewId, boolean initialValue, Consumer<Boolean> onChanged) {
        SwitchCompat alertSwitch = findViewById(viewId);
        alertSwitch.setChecked(initialValue);
        alertSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> onChanged.accept(isChecked));
    }

    private void saveWeeklyLimit(EditText field) {
        String value = field.getText().toString().trim();
        int hours;
        try {
            hours = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            field.setError("Enter a number from 1 to 168.");
            return;
        }

        if (hours < 1 || hours > 168) {
            field.setError("Enter a number from 1 to 168.");
            return;
        }

        client.setWeeklyHoursLimit(hours);
        field.setError(null);
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
                Toast.makeText(SettingsActivity.this, "Your data is synced.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String errorMessage) {
                btnSyncNow.setEnabled(true);
                btnSyncNow.setText("Sync now");
                Toast.makeText(SettingsActivity.this,
                        "Couldn't sync right now. Check your connection and try again.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshLastSynced() {
        tvLastSynced.setText(client.getLastSyncTimeString());
    }

    public void refreshSyncStatus() {
        refreshLastSynced();
    }
}
