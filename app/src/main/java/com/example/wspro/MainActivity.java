package com.example.wspro;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    protected static final String CHANNEL_ID = "work_study_conflicts";
    private ScanButtonHelper scanButtonHelper;

    // Launcher to handle camera permission request

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scanButtonHelper = new ScanButtonHelper(this);

        TextView tvDateHeader = findViewById(R.id.tvDateHeader);
        String currentDate = new SimpleDateFormat("EEEE, MMM dd", Locale.getDefault()).format(new Date());
        tvDateHeader.setText(currentDate);

        createNotificationChannel();

        // 1. Initialize High-Risk Action Button
        Button btnSuggestAction = findViewById(R.id.btnSuggestAction);
        if (btnSuggestAction != null) {
            btnSuggestAction.setOnClickListener(v -> evaluateConflictEngine());
        }

        // 2. Initialize Central Scan Schedule Floating Button
        ImageButton fabScanSchedule = findViewById(R.id.fabScanSchedule);
        scanButtonHelper.attachToFab(fabScanSchedule);


        // 3. Initialize Bottom Navigation Bar Items (using your existing IDs: btnToday, btnWeek, etc.)
        TextView navWeek = findViewById(R.id.btnWeek);
        TextView navTasks = findViewById(R.id.btnTasks);
        TextView navYou = findViewById(R.id.btnYou);


        if (navWeek != null) {
            navWeek.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, WeekActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);

            });
        }

        if (navTasks != null) {
            navTasks.setOnClickListener(v ->{
                Intent intent = new Intent(MainActivity.this, WeekActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);

            });
        }

        if (navYou != null) {
            navYou.setOnClickListener(v ->{
                Intent intent = new Intent(MainActivity.this, WeekActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);

            });
        }

        // 4. Request Notification runtime permissions (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTodaysSchedule();
    }

    private void loadTodaysSchedule() {
        Calendar calendar = Calendar.getInstance();
        // Get full name ("Friday"), short name ("Fri"), and day integer (1 = Sun, 6 = Fri)
        String currentDayFull = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());
        String currentDayShort = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

        // Map day integer to common custom chip names
        String[] dayCodes = {"", "Su", "M", "T", "W", "Th", "F", "Sa"};
        String currentDayCode = (dayOfWeek >= 1 && dayOfWeek <= 7) ? dayCodes[dayOfWeek] : "";

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            List<ScheduleItem> allSchedules = db.scheduleDao().getAllSchedulesRaw();
            List<ScheduleItem> todaysItems = new ArrayList<>();

            for (ScheduleItem item : allSchedules) {
                String d = item.getDay();
                if (d == null) continue;

                d = d.trim();
                boolean isMatch = d.equalsIgnoreCase(currentDayFull)
                        || d.equalsIgnoreCase(currentDayShort)
                        || d.equalsIgnoreCase(currentDayCode)
                        || (currentDayFull != null && currentDayFull.toLowerCase().startsWith(d.toLowerCase()));

                if (isMatch) {
                    todaysItems.add(item);
                }
            }

            runOnUiThread(() -> {
                LinearLayout container = findViewById(R.id.layoutTodaysScheduleContainer);
                if (container == null) return;

                container.removeAllViews(); // Clear previous items

                if (todaysItems.isEmpty()) {
                    // Optional: Show empty state text so it doesn't look broken
                    TextView emptyTv = new TextView(this);
                    emptyTv.setText("No schedules found for today.");
                    emptyTv.setTextColor(getResources().getColor(R.color.ws_muted));
                    emptyTv.setPadding(16, 16, 16, 16);
                    container.addView(emptyTv);
                    return;
                }

                for (ScheduleItem item : todaysItems) {
                    View cardView = getLayoutInflater().inflate(R.layout.item_today_card, container, false);

                    TextView tvTime = cardView.findViewById(R.id.tvScheduleTime);
                    TextView tvSubject = cardView.findViewById(R.id.tvScheduleSubject);
                    View indicator = cardView.findViewById(R.id.viewIndicator);

                    if (tvTime != null) tvTime.setText(item.getStartTime() + " - " + item.getEndTime());
                    if (tvSubject != null) tvSubject.setText(item.getSubject());

                    if (indicator != null) {
                        String category = item.getCategory();
                        if ("Work".equalsIgnoreCase(category)) {
                            indicator.setBackgroundColor(getResources().getColor(R.color.ws_accent)); // Work color
                        } else if ("Class".equalsIgnoreCase(category)) {
                            indicator.setBackgroundColor(getResources().getColor(R.color.ws_accent2)); // Class color
                        } else {
                            indicator.setBackgroundColor(getResources().getColor(R.color.ws_other_default)); // Other color
                        }
                    }

                    container.addView(cardView);
                }
            });
        }).start();
    }
    private void evaluateConflictEngine() {
        String suggestionMessage = "Wednesday Conflict: Your 2:00 PM – 8:00 PM Work shift overlaps with Networking Lab. " +
                "Suggestion: Request a shift swap or notify your instructor.";

        Toast.makeText(this, "⚡ Reviewing Conflict Details...", Toast.LENGTH_LONG).show();
        sendPushNotification("WorkStudyPro Conflict Alert", suggestionMessage);
    }

    private void sendPushNotification(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(404, builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "WorkStudy Conflict Notifications";
            String description = "Alerts for working student time clashes";
            int importance = android.app.NotificationManager.IMPORTANCE_HIGH;
            android.app.NotificationChannel channel = new android.app.NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            android.app.NotificationManager notificationManager = getSystemService(android.app.NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}