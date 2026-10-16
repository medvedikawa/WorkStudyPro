package com.example.wspro;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    private static final String CHANNEL_ID = "work_study_conflicts";
    private android.net.Uri photoUri;

    // Launcher to handle camera permission request
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openScheduleCamera();
                } else {
                    Toast.makeText(this, "Camera permission is required to scan schedules.", Toast.LENGTH_SHORT).show();
                }
            }
    );

    // Launcher to open the camera app and capture the schedule image
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Toast.makeText(this, "Schedule captured! Opening editor...", Toast.LENGTH_SHORT).show();

                    // Launch EditScheduleActivity instead of doing nothing
                    Intent intent = new Intent(MainActivity.this, EditScheduleActivity.class);
                    if (photoUri != null) {
                        intent.putExtra("CAPTURED_IMAGE_URI", photoUri.toString());
                    }
                    startActivity(intent);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();

        // 1. Initialize High-Risk Action Button
        Button btnSuggestAction = findViewById(R.id.btnSuggestAction);
        if (btnSuggestAction != null) {
            btnSuggestAction.setOnClickListener(v -> evaluateConflictEngine());
        }

        // 2. Initialize Central Scan Schedule Floating Button
        ImageButton fabScanSchedule = findViewById(R.id.fabScanSchedule);
        if (fabScanSchedule != null) {
            fabScanSchedule.setOnClickListener(v -> checkCameraPermissionAndScan());
        }

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

    private void checkCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openScheduleCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openScheduleCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        java.io.File photoFile = new java.io.File(getFilesDir(), "schedule_capture.jpg");
        photoUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileProvider",
                photoFile);
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        cameraLauncher.launch(takePictureIntent);




        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(takePictureIntent);
        } else {
            Toast.makeText(this, "No camera app found on device.", Toast.LENGTH_SHORT).show();
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
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}