package com.example.wspro;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class AdminActivity extends AppCompatActivity {
    private SupabaseClient client;
    private LinearLayout usersContainer;
    private LinearLayout logsContainer;
    private TextView status;
    private boolean authorized;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);
        client = SupabaseClient.getInstance(this);
        usersContainer = findViewById(R.id.layoutAdminUsers);
        logsContainer = findViewById(R.id.layoutAdminLogs);
        status = findViewById(R.id.tvAdminStatus);
        findViewById(R.id.btnAdminBack).setOnClickListener(v -> finish());
        verifyAdmin();
    }

    private void verifyAdmin() {
        JsonObject request = new JsonObject();
        request.addProperty("action", "check_role");
        client.invokeAdminFunction(request, new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(JsonObject result) {
                authorized = result.has("is_admin") && result.get("is_admin").getAsBoolean();
                if (!authorized) {
                    Toast.makeText(AdminActivity.this, "Admin access required.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                loadUsers();
                loadLogs();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(AdminActivity.this, "Couldn't verify admin access.", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void loadUsers() {
        invoke("list_users", null, new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(JsonObject result) {
                JsonArray users = result.has("users") ? result.getAsJsonArray("users") : new JsonArray();
                usersContainer.removeAllViews();
                status.setText(users.size() + (users.size() == 1 ? " user" : " users"));
                for (int i = 0; i < users.size(); i++) addUser(users.get(i).getAsJsonObject());
            }

            @Override
            public void onError(String errorMessage) {
                status.setText("Unable to load users.");
                showError(errorMessage);
            }
        });
    }

    private void addUser(JsonObject user) {
        String id = stringValue(user, "id");
        String email = stringValue(user, "email");
        String name = stringValue(user, "full_name");
        String course = stringValue(user, "course");
        String role = stringValue(user, "role");
        boolean active = !user.has("is_active") || user.get("is_active").getAsBoolean();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(14 * getResources().getDisplayMetrics().density);
        row.setPadding(padding, padding, padding, padding);
        row.setBackgroundResource(R.drawable.bg_input);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = padding / 2;
        row.setLayoutParams(rowParams);

        TextView details = new TextView(this);
        details.setText((name.isEmpty() ? email : name + " · " + email) + "\n"
                + (course.isEmpty() ? "No course" : course) + " · "
                + (role.isEmpty() ? "user" : role) + (active ? " · Active" : " · Inactive"));
        details.setTextColor(getColor(R.color.text_primary));
        row.addView(details);

        Button toggle = new Button(this);
        toggle.setText(active ? "Deactivate" : "Reactivate");
        toggle.setBackgroundTintList(getColorStateList(R.color.ws_accent));
        toggle.setTextColor(getColor(R.color.ws_on_brand));
        toggle.setEnabled(!id.equals(client.getUserId()));
        toggle.setOnClickListener(v -> setActive(id, !active));
        row.addView(toggle);
        usersContainer.addView(row);
    }

    private void setActive(String userId, boolean active) {
        JsonObject fields = new JsonObject();
        fields.addProperty("user_id", userId);
        fields.addProperty("is_active", active);
        invoke("set_user_active", fields, new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(JsonObject result) {
                loadUsers();
                loadLogs();
            }

            @Override
            public void onError(String errorMessage) {
                showError(errorMessage);
            }
        });
    }

    private void loadLogs() {
        invoke("debug_logs", null, new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(JsonObject result) {
                JsonArray logs = result.has("logs") ? result.getAsJsonArray("logs") : new JsonArray();
                logsContainer.removeAllViews();
                if (logs.size() == 0) {
                    addLogText("No admin activity recorded yet.");
                    return;
                }
                for (int i = 0; i < logs.size(); i++) {
                    JsonObject entry = logs.get(i).getAsJsonObject();
                    String details = entry.has("details") && entry.get("details").isJsonObject()
                            ? entry.getAsJsonObject("details").toString() : "";
                    addLogText(stringValue(entry, "created_at") + " · "
                            + stringValue(entry, "event") + "\n" + details);
                }
            }

            @Override
            public void onError(String errorMessage) {
                addLogText("Unable to load debug logs.");
                showError(errorMessage);
            }
        });
    }

    private void addLogText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(R.color.text_secondary));
        view.setTextSize(13);
        view.setPadding(12, 10, 12, 10);
        logsContainer.addView(view);
    }

    private void invoke(String action, JsonObject fields, SupabaseClient.DataCallback<JsonObject> callback) {
        if (!authorized) {
            callback.onError("Admin access required.");
            return;
        }
        JsonObject request = fields == null ? new JsonObject() : fields;
        request.addProperty("action", action);
        client.invokeAdminFunction(request, callback);
    }

    private void showError(String message) {
        if (!isFinishing()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String stringValue(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
}
