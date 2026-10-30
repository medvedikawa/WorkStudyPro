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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class EditScheduleActivity extends AppCompatActivity {
    public static final String EXTRA_EDIT_MODE = "EDIT_MODE";
    public static final String EXTRA_CATEGORY = "CATEGORY";

    private RecyclerView recyclerTimeSlots;
    private Button btnSaveSchedule;
    private TextView tvEmptyState;
    private TimeSlotAdapter adapter;
    private List<TimeSlotItem> timeSlotList;
    private final List<TimeSlotItem> originalEditItems = new ArrayList<>();
    private boolean editMode;
    private boolean categoryWork;

    public static String normalizeDay(String rawDay) {
        if (rawDay == null || rawDay.trim().isEmpty()) return "";
        String d = rawDay.trim().toUpperCase(Locale.US);
        if (d.contains("CHOOSE") || d.contains("SELECT")) return "";
        if (d.startsWith("MO") || d.contains("MON") || d.contains("LUN")) return "MON";
        if (d.startsWith("TU") || d.contains("TUE") || d.contains("MAR")) return "TUE";
        if (d.startsWith("WE") || d.contains("WED") || d.contains("MIY")) return "WED";
        if (d.startsWith("TH") || d.contains("THU") || d.contains("HUW") || d.equals("R")) return "THU";
        if (d.startsWith("FR") || d.contains("FRI") || d.contains("BIY")) return "FRI";
        if (d.startsWith("SA") || d.contains("SAT") || d.contains("SAB")) return "SAT";
        if (d.startsWith("SU") || d.contains("SUN") || d.contains("LIN")) return "SUN";
        return d;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_schedule);
        createNotificationChannel();

        recyclerTimeSlots = findViewById(R.id.recyclerTimeSlots);
        TextView btnAddTimeSlot = findViewById(R.id.btnAddTimeSlot);
        btnSaveSchedule = findViewById(R.id.btnSaveSchedule);
        ImageButton btnReturnBack = findViewById(R.id.btnReturnBack);
        TextView tvScheduleTitle = findViewById(R.id.tvScheduleTitle);
        TextView tvScheduleSubtitle = findViewById(R.id.tvScheduleSubtitle);
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
            tvScheduleTitle.setText(R.string.schedule_title_edit);
            tvScheduleSubtitle.setText(R.string.schedule_edit_subtitle);
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
        String finalRaw = rawUriString == null || rawUriString.isEmpty()
                ? imageUriString : rawUriString;

        if (imageUriString != null) {
            Uri imageUri = Uri.parse(imageUriString);
            previewCard.setVisibility(View.VISIBLE);
            thumbnail.setImageURI(imageUri);
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
                if (!saved.isEmpty()) {
                    adapter.notifyItemRangeInserted(0, saved.size());
                }
                updateEmptyState();
                btnSaveSchedule.setEnabled(true);
            });
        }).start();
    }

    private void addEmptySlot() {
        int colorResource = editMode
                ? (categoryWork ? R.color.schedule_work : R.color.schedule_school)
                : R.color.schedule_school;
        timeSlotList.add(new TimeSlotItem("", "", "", "",
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
        if (timeSlotList.isEmpty()) {
            Toast.makeText(this, "Please add at least one time slot.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!validateEditRows()) return;
        if (editMode && isUnchanged()) {
            Toast.makeText(this, "No changes to save.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnSaveSchedule.setEnabled(false);
        btnSaveSchedule.setText(R.string.schedule_saving);
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
                    btnSaveSchedule.setText(R.string.schedule_save);
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
                    btnSaveSchedule.setText(R.string.schedule_save);
                    Toast.makeText(this, "Couldn't save your schedule. Please try again.",
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private boolean validateEditRows() {
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
            if (start != null && end != null) {
                int startMin = ConflictChecker.timeToMinutes(item.getStartTime());
                int endMin = ConflictChecker.timeToMinutes(item.getEndTime());
                if (startMin == endMin) {
                    item.setEndTimeError("Start and end time cannot be identical.");
                }
            }
        }
        boolean valid = areRowsValid();
        if (!valid) {
            Toast.makeText(this, "Please select a day and fill in all required fields.", Toast.LENGTH_SHORT).show();
        }
        adapter.notifyItemRangeChanged(0, timeSlotList.size());
        return valid;
    }

    private boolean areRowsValid() {
        for (TimeSlotItem item : timeSlotList) {
            if (normalizeDay(item.getDay()).isEmpty()
                    || item.getSubjectError() != null
                    || item.getStartTimeError() != null
                    || item.getEndTimeError() != null) {
                return false;
            }
        }
        return true;
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

    private boolean isUnchanged() {
        if (timeSlotList.size() != originalEditItems.size()) return false;
        Iterator<TimeSlotItem> originals = originalEditItems.iterator();
        for (TimeSlotItem current : timeSlotList) {
            TimeSlotItem original = originals.next();
            if (current.getId() != original.getId()
                    || !Objects.equals(current.getSubject(), original.getSubject())
                    || !Objects.equals(normalizeDay(current.getDay()), normalizeDay(original.getDay()))
                    || !Objects.equals(current.getStartTime(), original.getStartTime())
                    || !Objects.equals(current.getEndTime(), original.getEndTime())
                    || current.isWork() != original.isWork()
                    || !Objects.equals(current.getColorHex(), original.getColorHex())) return false;
        }
        return true;
    }

    private void requestBack() {
        if (!editMode || isUnchanged()) {
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
                        adapter.notifyItemInserted(timeSlotList.size() - 1);
                    });
        } catch (IOException e) {
            android.util.Log.e("EditScheduleActivity", "Could not load image for text recognition", e);
            Toast.makeText(this, "Failed to read schedule image.", Toast.LENGTH_SHORT).show();
        }
    }

    private void parseScannedText(Text visionText) {
        List<ScheduleTableParser.Word> words = new ArrayList<>();
        if (visionText != null) {
            for (Text.TextBlock block : visionText.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    for (Text.Element element : line.getElements()) {
                        android.graphics.Rect box = element.getBoundingBox();
                        if (box != null) {
                            words.add(new ScheduleTableParser.Word(
                                    element.getText(), box.left, box.top, box.right, box.bottom
                            ));
                        }
                    }
                }
            }
        }

        // Export raw OCR word list to debug JSON file
        exportDebugOcrWords(words);

        ScheduleTableParser.ParseResult result = ScheduleTableParser.parse(words);

        if (result.qualityGateFailed) {
            showQualityGateDialog(result.qualityGateMessage);
            return;
        }

        // Log parser output list (count plus each item)
        android.util.Log.d("EditScheduleActivity", "Parsed " + result.meetings.size() + " meetings from scanner:");
        for (ScheduleTableParser.ParsedMeeting meeting : result.meetings) {
            android.util.Log.d("EditScheduleActivity", "Meeting: " + meeting);
        }

        int previousCount = timeSlotList.size();
        timeSlotList.clear();

        for (ScheduleTableParser.ParsedMeeting m : result.meetings) {
            TimeSlotItem item = new TimeSlotItem(
                    m.subject,
                    m.day != null ? m.day : "",
                    m.startTime,
                    m.endTime,
                    false,
                    colorHex(R.color.schedule_school)
            );
            item.setFlags(m.flags);
            timeSlotList.add(item);
        }

        if (timeSlotList.isEmpty()) {
            timeSlotList.add(new TimeSlotItem("", "", "", "", false,
                    colorHex(R.color.schedule_school)));
        }
        if (previousCount > 0) {
            adapter.notifyItemRangeRemoved(0, previousCount);
        }
        if (!timeSlotList.isEmpty()) {
            adapter.notifyItemRangeInserted(0, timeSlotList.size());
            recyclerTimeSlots.scrollToPosition(timeSlotList.size() - 1);
        }
        updateEmptyState();

        // Warning banner
        TextView tvScanWarning = findViewById(R.id.tvScanWarning);
        if (tvScanWarning != null) {
            if (result.unitsMismatch || !result.skippedLines.isEmpty()) {
                tvScanWarning.setVisibility(View.VISIBLE);
            } else {
                tvScanWarning.setVisibility(View.GONE);
            }
        }

        // Skipped lines section ("Lines we couldn't place")
        setupSkippedLinesSection(result.skippedLines);
    }

    private void showQualityGateDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Scan Unclear")
                .setMessage(message != null ? message : "We couldn't read this clearly. Try again in better light, or add your classes manually.")
                .setCancelable(false)
                .setPositiveButton("Retake", (dialog, which) -> {
                    String rawUriString = getIntent().getStringExtra("RAW_IMAGE_URI");
                    String imageUriString = getIntent().getStringExtra("CAPTURED_IMAGE_URI");
                    String rawUri = rawUriString == null ? imageUriString : rawUriString;
                    Intent cropIntent = new Intent(EditScheduleActivity.this, CropRotateActivity.class);
                    cropIntent.putExtra("RAW_IMAGE_URI", rawUri);
                    startActivity(cropIntent);
                    finish();
                })
                .setNegativeButton("Add manually", (dialog, which) -> {
                    timeSlotList.clear();
                    addEmptySlot();
                })
                .show();
    }

    private void setupSkippedLinesSection(List<String> skippedLines) {
        LinearLayout layoutSkippedSection = findViewById(R.id.layoutSkippedSection);
        LinearLayout layoutSkippedContainer = findViewById(R.id.layoutSkippedContainer);
        TextView tvSkippedHeader = findViewById(R.id.tvSkippedHeader);

        if (layoutSkippedSection == null || layoutSkippedContainer == null) return;

        if (skippedLines == null || skippedLines.isEmpty()) {
            layoutSkippedSection.setVisibility(View.GONE);
            return;
        }

        layoutSkippedSection.setVisibility(View.VISIBLE);
        layoutSkippedContainer.removeAllViews();

        if (tvSkippedHeader != null) {
            tvSkippedHeader.setOnClickListener(v -> {
                boolean isExpanded = layoutSkippedContainer.getVisibility() == View.VISIBLE;
                layoutSkippedContainer.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                tvSkippedHeader.setText(isExpanded ? "Lines we couldn't place ▸" : "Lines we couldn't place ▾");
            });
        }

        for (String line : skippedLines) {
            View rowView = getLayoutInflater().inflate(R.layout.item_skipped_line, layoutSkippedContainer, false);
            TextView tvSkippedText = rowView.findViewById(R.id.tvSkippedText);
            Button btnAddAsClass = rowView.findViewById(R.id.btnAddAsClass);

            if (tvSkippedText != null) tvSkippedText.setText(line);
            if (btnAddAsClass != null) {
                btnAddAsClass.setOnClickListener(v -> {
                    TimeSlotItem item = new TimeSlotItem(line, "", "", "", false, colorHex(R.color.schedule_school));
                    timeSlotList.add(item);
                    adapter.notifyItemInserted(timeSlotList.size() - 1);
                    layoutSkippedContainer.removeView(rowView);
                    if (layoutSkippedContainer.getChildCount() == 0) {
                        layoutSkippedSection.setVisibility(View.GONE);
                    }
                });
            }

            layoutSkippedContainer.addView(rowView);
        }
    }

    private void exportDebugOcrWords(List<ScheduleTableParser.Word> words) {
        boolean isDebug = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!isDebug || words == null || words.isEmpty()) return;
        try {
            java.io.File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir == null || !downloadsDir.exists()) {
                downloadsDir = getExternalCacheDir();
            }
            java.io.File file = new java.io.File(downloadsDir, "ocr_words_debug.json");
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write("[\n");
            for (int i = 0; i < words.size(); i++) {
                ScheduleTableParser.Word w = words.get(i);
                writer.write(String.format(Locale.US,
                        "  {\"text\": \"%s\", \"left\": %d, \"top\": %d, \"right\": %d, \"bottom\": %d}%s\n",
                        w.text.replace("\"", "\\\""), w.left, w.top, w.right, w.bottom,
                        (i < words.size() - 1 ? "," : "")
                ));
            }
            writer.write("]\n");
            writer.close();
            android.util.Log.d("EditScheduleActivity", "Exported debug OCR words to " + file.getAbsolutePath());
        } catch (IOException e) {
            android.util.Log.e("EditScheduleActivity", "Failed to export OCR words debug JSON", e);
        }
    }
}
