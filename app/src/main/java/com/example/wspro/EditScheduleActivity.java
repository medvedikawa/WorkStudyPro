package com.example.wspro;

import static com.example.wspro.MainActivity.CHANNEL_ID;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EditScheduleActivity extends AppCompatActivity {
    private RecyclerView recyclerTimeSlots;
    private TextView btnAddTimeSlot;
    private Button btnSaveSchedule;
    private ImageButton btnReturnBack;
    private TimeSlotAdapter adapter;
    private List<TimeSlotItem> timeSlotList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_schedule);

        createNotificationChannel();
        recyclerTimeSlots = findViewById(R.id.recyclerTimeSlots);
        btnAddTimeSlot = findViewById(R.id.btnAddTimeSlot);
        btnSaveSchedule = findViewById(R.id.btnSaveSchedule);
        btnReturnBack = findViewById(R.id.btnReturnBack);

        if (btnReturnBack != null) {
            btnReturnBack.setOnClickListener(v -> finish());
        }

        timeSlotList = new ArrayList<>();
        recyclerTimeSlots.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TimeSlotAdapter(this, timeSlotList);
        recyclerTimeSlots.setAdapter(adapter);

        // Check if an image Uri was passed from the camera/gallery capture
        String imageUriString = getIntent().getStringExtra("CAPTURED_IMAGE_URI");
        if (imageUriString != null) {
            Uri imageUri = Uri.parse(imageUriString);
            processImageWithMLKit(imageUri);
        } else {
            // Fallback empty row if opened manually
            timeSlotList.add(new TimeSlotItem("", "", "", ""));
            adapter.notifyDataSetChanged();
        }

        btnAddTimeSlot.setOnClickListener(v -> {
            timeSlotList.add(new TimeSlotItem("", "", "", ""));
            adapter.notifyItemInserted(timeSlotList.size() - 1);
            recyclerTimeSlots.scrollToPosition(timeSlotList.size() - 1);
        });

        btnSaveSchedule.setOnClickListener(v -> {
            if (timeSlotList.isEmpty()) {
                Toast.makeText(this, "Please add at least one time slot.", Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                boolean hasConflict = false;
                String conflictingSubject = "";

                // Check for conflicts to trigger the warning
                for (TimeSlotItem item : timeSlotList) {
                    List<ScheduleItem> existingSchedules = db.scheduleDao().getAllSchedules(item.getDay());

                    for (ScheduleItem existing : existingSchedules) {
                        boolean clash = ConflictChecker.hasConflict(
                                item.getDay(), item.getStartTime(), item.getEndTime(),
                                existing.getDay(), existing.getStartTime(), existing.getEndTime()
                        );

                        if (clash) {
                            hasConflict = true;
                            conflictingSubject = existing.getSubject();
                            break;
                        }
                    }
                    if (hasConflict) break;
                }

                // Always prepare and save the items, regardless of conflict status
                List<ScheduleItem> itemsToSave = new ArrayList<>();
                for (TimeSlotItem item : timeSlotList) {
                    itemsToSave.add(new ScheduleItem(
                            item.getSubject().trim(),
                            item.getDay(),
                            item.getStartTime(),
                            item.getEndTime(), false
                    ));
                }
                db.scheduleDao().insertAll(itemsToSave);

                final boolean clashFound = hasConflict;
                final String clashName = conflictingSubject;

                // UI updates on the main thread
                runOnUiThread(() -> {
                    if (clashFound) {
                        sendConflictNotification(clashName);
                        Toast.makeText(this, "Conflict detected with " + clashName + ". Saved successfully!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Schedule saved successfully", Toast.LENGTH_SHORT).show();
                    }
                    finish();
                });
            }).start();
        });
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

    private void sendConflictNotification(String conflictingSubject) {
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);

        // Build the notification
        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, "SCHEDULE_CONFLICT_CHANNEL")
                .setSmallIcon(android.R.drawable.ic_dialog_alert) // Replace with your app's notification icon
                .setContentTitle("Schedule Conflict Detected!")
                .setContentText("Your new schedule overlaps with: " + conflictingSubject)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void processImageWithMLKit(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(this::parseScannedText)
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to read text: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        timeSlotList.add(new TimeSlotItem("", "", "", ""));
                        adapter.notifyDataSetChanged();
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseScannedText(Text visionText) {
        boolean foundData = false;
        String currentSubject = " Scanned Class"; // Default fallback subject

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String text = line.getText().trim();
                if (text.isEmpty()) continue;

                // Check if this line looks like it contains a time range
                if (text.toUpperCase().contains("AM") || text.toUpperCase().contains("PM") || text.contains("-") || text.contains("–")) {
                    String detectDay = extractDay(text);
                    String[] times = extractTimeRange(text);

                    String startTime = times.length > 0 ? times[0] : "";
                    String endTime = times.length > 1 ? times[1] : "";

                    // Only add if we successfully extracted clean time strings
                    if (!startTime.isEmpty() || !endTime.isEmpty()) {
                        timeSlotList.add(new TimeSlotItem(currentSubject, detectDay, startTime, endTime));
                        foundData = true;
                    }
                } else {
                    // If it's not a timeline, treat it as the subject name for upcoming slots
                    currentSubject = text;
                }
            }
        }

        if (!foundData) {
            timeSlotList.add(new TimeSlotItem("", "", "", ""));
            Toast.makeText(this, "Please verify and adjust fields manually.", Toast.LENGTH_LONG).show();
        }

        adapter.notifyDataSetChanged();
    }

    private String extractDay(String line){
        String upper = line.toUpperCase();
        if (upper.contains("M")) return "Mo";
        if (upper.contains("T")) return "Tu";
        if (upper.contains("W")) return "We";
        if (upper.contains("Th")) return "Th";
        if (upper.contains("F")) return "Fr";
        if (upper.contains("Sa")) return "Sa";
        if (upper.contains("Su")) return "Su";
        return "";
    }

    private String[] extractTimeRange(String line) {
        // Clean up the line to isolate time formats (e.g., 08:00 AM - 11:00 AM)
        String[] parts = line.split("[-–]|(?i)\\bto\\b");
        if (parts.length >= 2) {
            return new String[]{cleanTimeUnit(parts[0]), cleanTimeUnit(parts[1])};
        }
        return new String[]{cleanTimeUnit(line), ""};
    }

private String cleanTimeUnit(String token) {
    // Extracts standard time patterns like "8:00 AM" or "12:30PM" from messy OCR text
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{1,2}[:.]\\d{2}\\s*(?i)[ap]m|\\d{1,2}\\s*(?i)[ap]m)", java.util.regex.Pattern.CASE_INSENSITIVE);
    java.util.regex.Matcher matcher = pattern.matcher(token);
    if (matcher.find()) {
        return matcher.group(0).toUpperCase().trim();
    }
    return "";
}

}