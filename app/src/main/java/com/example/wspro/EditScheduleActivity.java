package com.example.wspro;

import static com.example.wspro.MainActivity.CHANNEL_ID;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EditScheduleActivity extends AppCompatActivity {
    public static final String EXTRA_EDIT_MODE = "EDIT_MODE";
    public static final String EXTRA_CATEGORY = "CATEGORY";

    private RecyclerView recyclerTimeSlots;
    private TextView btnAddTimeSlot;
    private Button btnSaveSchedule;
    private ImageButton btnReturnBack;
    private TextView tvScheduleTitle;
    private TextView tvScheduleSubtitle;
    private TextView tvEmptyState;
    private TimeSlotAdapter adapter;
    private List<TimeSlotItem> timeSlotList;
    private final List<TimeSlotItem> originalEditItems = new ArrayList<>();
    private boolean editMode;
    private boolean categoryWork;

    public static String normalizeDay(String rawDay) {
        if (rawDay == null || rawDay.trim().isEmpty()) return "MON";
        String d = rawDay.trim().toUpperCase();
        if (d.startsWith("MO") || d.contains("MON")) return "MON";
        if (d.startsWith("TU") || d.contains("TUE")) return "TUE";
        if (d.startsWith("WE") || d.contains("WED")) return "WED";
        if (d.startsWith("TH") || d.contains("THU")) return "THU";
        if (d.startsWith("FR") || d.contains("FRI")) return "FRI";
        if (d.startsWith("SA") || d.contains("SAT")) return "SAT";
        if (d.startsWith("SU") || d.contains("SUN")) return "SUN";
        return d;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_schedule);
        createNotificationChannel();

        recyclerTimeSlots = findViewById(R.id.recyclerTimeSlots);
        btnAddTimeSlot = findViewById(R.id.btnAddTimeSlot);
        btnSaveSchedule = findViewById(R.id.btnSaveSchedule);
        btnReturnBack = findViewById(R.id.btnReturnBack);
        tvScheduleTitle = findViewById(R.id.tvScheduleTitle);
        tvScheduleSubtitle = findViewById(R.id.tvScheduleSubtitle);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        LinearLayout previewCard = findViewById(R.id.layoutScannedPreviewCard);
        ImageView thumbnail = findViewById(R.id.ivScannedThumbnail);
        Button recropButton = findViewById(R.id.btnReCrop);
        editMode = getIntent().getBooleanExtra(EXTRA_EDIT_MODE, false);
        categoryWork = "work".equalsIgnoreCase(getIntent().getStringExtra(EXTRA_CATEGORY));

        timeSlotList = new ArrayList<>();
        recyclerTimeSlots.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TimeSlotAdapter(this, timeSlotList, editMode);
        adapter.setOnItemsChangedListener(this::updateEmptyState);
        recyclerTimeSlots.setAdapter(adapter);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                requestBack();
            }
        });
        btnReturnBack.setOnClickListener(v -> requestBack());
        btnAddTimeSlot.setOnClickListener(v -> addEmptySlot());
        btnSaveSchedule.setOnClickListener(v -> saveSchedule());

        if (editMode) {
            tvScheduleTitle.setText("Edit Schedule");
            tvScheduleSubtitle.setText("Update, add, or remove your school/work entries.");
            previewCard.setVisibility(View.GONE);
            btnSaveSchedule.setEnabled(false);
            loadCategorySchedules();
        } else {
            initializeScanMode(previewCard, thumbnail, recropButton);
        }
    }

    private void initializeScanMode(LinearLayout previewCard, ImageView thumbnail, Button recropButton) {
        String imageUriString = getIntent().getStringExtra("CAPTURED_IMAGE_URI");
        String rawUriString = getIntent().getStringExtra("RAW_IMAGE_URI");
        if (rawUriString == null || rawUriString.isEmpty()) rawUriString = imageUriString;

        if (imageUriString != null) {
            Uri imageUri = Uri.parse(imageUriString);
            previewCard.setVisibility(View.VISIBLE);
            thumbnail.setImageURI(imageUri);
            String finalRaw = rawUriString;
            recropButton.setOnClickListener(v -> {
                Intent cropIntent = new Intent(EditScheduleActivity.this, CropRotateActivity.class);
                cropIntent.putExtra("RAW_IMAGE_URI", finalRaw);
                startActivity(cropIntent);
                finish();
            });
            processImageWithMLKit(imageUri);
        } else {
            addEmptySlot();
        }
    }

    private void loadCategorySchedules() {
        new Thread(() -> {
            List<ScheduleItem> saved = AppDatabase.getInstance(getApplicationContext())
                    .scheduleDao().getSchedulesByWork(categoryWork);
            for (ScheduleItem schedule : saved) {
                TimeSlotItem item = new TimeSlotItem(schedule.getSubject(), schedule.getDay(),
                        schedule.getStartTime(), schedule.getEndTime(), categoryWork,
                        schedule.getColorHex());
                item.setId(schedule.getId());
                timeSlotList.add(item);
                originalEditItems.add(copyTimeSlot(item));
            }
            runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
                updateEmptyState();
                btnSaveSchedule.setEnabled(true);
            });
        }).start();
    }

    private void addEmptySlot() {
        int colorResource = editMode
                ? (categoryWork ? R.color.schedule_work : R.color.schedule_school)
                : R.color.schedule_school;
        timeSlotList.add(new TimeSlotItem("", "MON", "", "",
                editMode && categoryWork, colorHex(colorResource)));
        adapter.notifyItemInserted(timeSlotList.size() - 1);
        recyclerTimeSlots.scrollToPosition(timeSlotList.size() - 1);
        updateEmptyState();
    }

    private String colorHex(int colorResource) {
        int color = getColor(colorResource);
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private void updateEmptyState() {
        boolean isEmptyEdit = editMode && timeSlotList.isEmpty();
        if (isEmptyEdit) {
            String category = getString(categoryWork ? R.string.schedule_work : R.string.schedule_school);
            tvEmptyState.setText(getString(R.string.schedule_empty_edit, category));
        }
        tvEmptyState.setVisibility(isEmptyEdit ? View.VISIBLE : View.GONE);
    }

    private void saveSchedule() {
        if (!editMode && timeSlotList.isEmpty()) {
            Toast.makeText(this, "Please add at least one time slot.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (editMode && !validateEditRows()) return;
        if (editMode && !hasUnsavedChanges()) {
            Toast.makeText(this, "No changes to save.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnSaveSchedule.setEnabled(false);
        btnSaveSchedule.setText("Saving...");
        List<ScheduleItem> itemsToSave = createScheduleItems();
        List<Integer> deletedIds = findDeletedIds();

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                String conflictSubject = findConflict(db, itemsToSave);
                if (editMode) {
                    LocalDataRepository.saveEditedSchedules(db, itemsToSave, deletedIds);
                } else {
                    LocalDataRepository.saveSchedules(db, itemsToSave);
                }
                SupabaseClient.getInstance(getApplicationContext()).setHasUnsyncedChanges(true);
                ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();

                runOnUiThread(() -> {
                    btnSaveSchedule.setEnabled(true);
                    btnSaveSchedule.setText("Save");
                    if (!conflictSubject.isEmpty()) {
                        sendConflictNotification(conflictSubject);
                        Toast.makeText(this, "Conflict detected with " + conflictSubject
                                + (editMode ? ". Schedule updated." : ". Saved successfully!"),
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, editMode ? "Schedule updated."
                                : "Schedule saved successfully.", Toast.LENGTH_SHORT).show();
                    }
                    finish();
                });
            } catch (RuntimeException e) {
                android.util.Log.e("EditScheduleActivity", "Could not save schedule", e);
                runOnUiThread(() -> {
                    btnSaveSchedule.setEnabled(true);
                    btnSaveSchedule.setText("Save");
                    Toast.makeText(this, "Couldn't save your schedule. Please try again.",
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private boolean validateEditRows() {
        boolean valid = true;
        SimpleDateFormat formatter = new SimpleDateFormat("h:mm a", Locale.US);
        formatter.setLenient(false);
        for (TimeSlotItem item : timeSlotList) {
            item.setSubjectError(item.getSubject() == null || item.getSubject().trim().isEmpty()
                    ? "Subject is required." : null);
            item.setStartTimeError(isValidTime(item.getStartTime(), formatter)
                    ? null : "Enter a valid start time.");
            item.setEndTimeError(isValidTime(item.getEndTime(), formatter)
                    ? null : "Enter a valid end time.");
            Date start = parseTime(item.getStartTime(), formatter);
            Date end = parseTime(item.getEndTime(), formatter);
            if (start != null && end != null && !start.before(end)) {
                item.setEndTimeError("End time must be after start time.");
            }
            if (item.getSubjectError() != null || item.getStartTimeError() != null
                    || item.getEndTimeError() != null) valid = false;
        }
        adapter.notifyDataSetChanged();
        return valid;
    }

    private boolean isValidTime(String value, SimpleDateFormat formatter) {
        return parseTime(value, formatter) != null;
    }

    private Date parseTime(String value, SimpleDateFormat formatter) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.US);
        ParsePosition position = new ParsePosition(0);
        Date parsed = formatter.parse(normalized, position);
        return parsed != null && position.getIndex() == normalized.length() ? parsed : null;
    }

    private List<ScheduleItem> createScheduleItems() {
        List<ScheduleItem> result = new ArrayList<>();
        for (TimeSlotItem item : timeSlotList) {
            ScheduleItem schedule = new ScheduleItem(item.getSubject().trim(),
                    normalizeDay(item.getDay()), item.getStartTime(), item.getEndTime(),
                    editMode ? categoryWork : item.isWork(), item.getColorHex());
            schedule.setId(item.getId());
            result.add(schedule);
        }
        return result;
    }

    private List<Integer> findDeletedIds() {
        Set<Integer> retainedIds = new HashSet<>();
        for (TimeSlotItem item : timeSlotList) {
            if (item.getId() > 0) retainedIds.add(item.getId());
        }
        List<Integer> deletedIds = new ArrayList<>();
        for (TimeSlotItem original : originalEditItems) {
            if (!retainedIds.contains(original.getId())) deletedIds.add(original.getId());
        }
        return deletedIds;
    }

    private String findConflict(AppDatabase db, List<ScheduleItem> proposed) {
        Set<Integer> excludedIds = new HashSet<>();
        if (editMode) {
            for (TimeSlotItem original : originalEditItems) excludedIds.add(original.getId());
        }
        List<ScheduleItem> checked = new ArrayList<>();
        for (ScheduleItem item : proposed) {
            List<ScheduleItem> existingRows = db.scheduleDao().getAllSchedules(item.getDay());
            for (ScheduleItem existing : existingRows) {
                if (excludedIds.contains(existing.getId())) continue;
                if (ConflictChecker.hasConflict(item.getDay(), item.getStartTime(), item.getEndTime(),
                        existing.getDay(), existing.getStartTime(), existing.getEndTime())) {
                    return existing.getSubject();
                }
            }
            for (ScheduleItem previous : checked) {
                if (ConflictChecker.hasConflict(item.getDay(), item.getStartTime(), item.getEndTime(),
                        previous.getDay(), previous.getStartTime(), previous.getEndTime())) {
                    return previous.getSubject();
                }
            }
            checked.add(item);
        }
        return "";
    }

    private TimeSlotItem copyTimeSlot(TimeSlotItem item) {
        TimeSlotItem copy = new TimeSlotItem(item.getSubject(), item.getDay(), item.getStartTime(),
                item.getEndTime(), item.isWork(), item.getColorHex());
        copy.setId(item.getId());
        return copy;
    }

    private boolean hasUnsavedChanges() {
        if (timeSlotList.size() != originalEditItems.size()) return true;
        for (int i = 0; i < timeSlotList.size(); i++) {
            TimeSlotItem current = timeSlotList.get(i);
            TimeSlotItem original = originalEditItems.get(i);
            if (current.getId() != original.getId()
                    || !same(current.getSubject(), original.getSubject())
                    || !same(normalizeDay(current.getDay()), normalizeDay(original.getDay()))
                    || !same(current.getStartTime(), original.getStartTime())
                    || !same(current.getEndTime(), original.getEndTime())
                    || current.isWork() != original.isWork()
                    || !same(current.getColorHex(), original.getColorHex())) return true;
        }
        return false;
    }

    private boolean same(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private void requestBack() {
        if (!editMode || !hasUnsavedChanges()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Discard changes?")
                .setMessage("Your schedule changes have not been saved.")
                .setNegativeButton("Keep editing", null)
                .setPositiveButton("Discard", (dialog, which) -> finish())
                .show();
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

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
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
                        timeSlotList.add(new TimeSlotItem("", "MON", "", "", false,
                                colorHex(R.color.schedule_school)));
                        adapter.notifyDataSetChanged();
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseScannedText(Text visionText) {
        boolean foundData = false;
        String currentSubject = "Scanned Class";

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
                        timeSlotList.add(new TimeSlotItem(currentSubject, detectDay, startTime,
                                endTime, false, colorHex(R.color.schedule_school)));
                        foundData = true;
                    }
                } else {
                    currentSubject = text;
                }
            }
        }

        if (!foundData) {
            timeSlotList.add(new TimeSlotItem("", "MON", "", "", false,
                    colorHex(R.color.schedule_school)));
            Toast.makeText(this, "Please verify and adjust fields manually.", Toast.LENGTH_LONG).show();
        }

        adapter.notifyDataSetChanged();
    }

    private String extractDay(String line) {
        String upper = line.toUpperCase();
        if (upper.contains("TH") || upper.contains("THU")) return "THU";
        if (upper.contains("TU") || upper.contains("TUE")) return "TUE";
        if (upper.contains("MO") || upper.contains("MON")) return "MON";
        if (upper.contains("WE") || upper.contains("WED")) return "WED";
        if (upper.contains("FR") || upper.contains("FRI")) return "FRI";
        if (upper.contains("SA") || upper.contains("SAT")) return "SAT";
        if (upper.contains("SU") || upper.contains("SUN")) return "SUN";
        return "MON";
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
