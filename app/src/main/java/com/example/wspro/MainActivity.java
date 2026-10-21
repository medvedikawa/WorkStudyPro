package com.example.wspro;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
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