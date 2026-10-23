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
    private String targetCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_schedule);

        targetCategory = getIntent().getStringExtra("SELECTED_CATEGORY");

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

        // Check if a category was passed to pre-load items from the database
        if (targetCategory != null && !targetCategory.isEmpty()) {
            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                List<ScheduleItem> rawList = db.scheduleDao().getAllSchedulesRaw();
                List<TimeSlotItem> categoryItems = new ArrayList<>();

                for (ScheduleItem item : rawList) {
                    if (targetCategory.equalsIgnoreCase(item.getCategory())) {
                        categoryItems.add(new TimeSlotItem(
                                item.getSubject(),
                                item.getDay(),
                                item.getStartTime(),
                                item.getEndTime(),
                                item.getCategory()
                        ));
                    }
                }

                runOnUiThread(() -> {
                    timeSlotList.clear();
                    if (categoryItems.isEmpty()) {
                        timeSlotList.add(new TimeSlotItem("", "", "", "", targetCategory));
                    } else {
                        timeSlotList.addAll(categoryItems);
                    }
                    adapter.notifyDataSetChanged();
                });
            }).start();
        } else {
            // Check if an image Uri was passed from the camera/gallery capture
            String imageUriString = getIntent().getStringExtra("CAPTURED_IMAGE_URI");
            if (imageUriString != null) {
                Uri imageUri = Uri.parse(imageUriString);
                processImageWithMLKit(imageUri);
            } else {
                // Fallback empty row if opened manually
                timeSlotList.add(new TimeSlotItem("", "", "", "", "Class"));
                adapter.notifyDataSetChanged();
            }
        }

        btnAddTimeSlot.setOnClickListener(v -> {
            String defaultCat = targetCategory != null ? targetCategory : "Class";
            timeSlotList.add(new TimeSlotItem("", "", "", "", defaultCat));
            adapter.notifyItemInserted(timeSlotList.size() - 1);
            recyclerTimeSlots.scrollToPosition(timeSlotList.size() - 1);
        });

        btnSaveSchedule.setOnClickListener(v -> {
            if (timeSlotList.isEmpty()) {
                Toast.makeText(this, "Please add at least one time slot.", Toast.LENGTH_SHORT).show();
                return;
            }

            List<ScheduleItem> itemsToSave = new ArrayList<>();
            for (TimeSlotItem item : timeSlotList) {
                if (item.getDay() == null || item.getDay().isEmpty() ||
                        item.getStartTime() == null || item.getStartTime().isEmpty() ||
                        item.getEndTime() == null || item.getEndTime().isEmpty()) {
                    Toast.makeText(this, "Please complete all fields.", Toast.LENGTH_SHORT).show();
                    return;
                }

                String subject = item.getSubject().trim();
                String category = item.getCategory() != null && !item.getCategory().isEmpty() ? item.getCategory() : "Class";

                itemsToSave.add(new ScheduleItem(
                        subject,
                        item.getDay(),
                        item.getStartTime(),
                        item.getEndTime(),
                        category
                ));
            }

            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                boolean hasConflict = false;
                String conflictingSubject = "";

                // Check for conflicts to trigger the warning
                for (ScheduleItem item : itemsToSave) {
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

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, "SCHEDULE_CONFLICT_CHANNEL")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
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
                        String defaultCat = targetCategory != null ? targetCategory : "Class";
                        timeSlotList.add(new TimeSlotItem("", "", "", "", defaultCat));
                        adapter.notifyDataSetChanged();
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseScannedText(Text visionText) {
        boolean foundData = false;
        String currentSubject = "Scanned Class";
        String defaultCat = targetCategory != null ? targetCategory : "Class";

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String text = line.getText().trim();
                if (text.isEmpty()) continue;

                if (text.toUpperCase().contains("AM") || text.toUpperCase().contains("PM") || text.contains("-") || text.contains("–")) {
                    String detectDay = extractDay(text);
                    String[] times = extractTimeRange(text);

                    String startTime = times.length > 0 ? times[0] : "";
                    String endTime = times.length > 1 ? times[1] : "";

                    if (!startTime.isEmpty() || !endTime.isEmpty()) {
                        timeSlotList.add(new TimeSlotItem(currentSubject, detectDay, startTime, endTime, defaultCat));
                        foundData = true;
                    }
                } else {
                    currentSubject = text;
                }
            }
        }

        if (!foundData) {
            timeSlotList.add(new TimeSlotItem("", "", "", "", defaultCat));
            Toast.makeText(this, "Please verify and adjust fields manually.", Toast.LENGTH_LONG).show();
        }

        adapter.notifyDataSetChanged();
    }

    private String extractDay(String line){
        String upper = line.toUpperCase();
        if (upper.contains("TH")) return "Thursday";
        if (upper.contains("TU")) return "Tuesday";
        if (upper.contains("WE")) return "Wednesday";
        if (upper.contains("MO")) return "Monday";
        if (upper.contains("FR")) return "Friday";
        if (upper.contains("SA")) return "Saturday";
        if (upper.contains("SU")) return "Sunday";

        if (upper.contains("W")) return "Wednesday";
        if (upper.contains("F")) return "Friday";
        if (upper.contains("M")) return "Monday";
        return "";
    }

    private String[] extractTimeRange(String line) {
        String[] parts = line.split("[-–]|(?i)\\bto\\b");
        if (parts.length >= 2) {
            return new String[]{cleanTimeUnit(parts[0]), cleanTimeUnit(parts[1])};
        }
        return new String[]{cleanTimeUnit(line), ""};
    }

    private String cleanTimeUnit(String token) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{1,2}[:.]\\d{2}\\s*(?i)[ap]m|\\d{1,2}\\s*(?i)[ap]m)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(token);
        if (matcher.find()) {
            return matcher.group(0).toUpperCase().trim();
        }
        return "";
    }
}