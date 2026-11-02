package com.example.wspro;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
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
import java.util.UUID;

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
    private boolean deletingSlot;
    private boolean scheduleReady;
    private boolean saveInProgress;
    private final Set<String> resolvedEditGroups = new HashSet<>();

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
        scheduleReady = !editMode;

        timeSlotList = new ArrayList<>();
        recyclerTimeSlots.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TimeSlotAdapter(this, editMode);
        adapter.setOnItemChangedListener(this::updateTimeSlotFromAdapter);
        adapter.setOnDeleteSlotListener(this::confirmDeleteSlot);
        recyclerTimeSlots.setAdapter(adapter);
        submitTimeSlots();
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
            List<TimeSlotItem> loadedItems = new ArrayList<>();
            List<TimeSlotItem> loadedOriginals = new ArrayList<>();
            for (ScheduleItem schedule : saved) {
                TimeSlotItem item = new TimeSlotItem(schedule.getSubject(), schedule.getDay(),
                        schedule.getStartTime(), schedule.getEndTime(), categoryWork,
                        schedule.getColorHex());
                item.setId(schedule.getId());
                item.setGroupId(schedule.getGroupId());
                loadedItems.add(item);
                loadedOriginals.add(copyTimeSlot(item));
            }
            runOnUiThread(() -> {
                timeSlotList.addAll(loadedItems);
                originalEditItems.addAll(loadedOriginals);
                submitTimeSlots();
                updateEmptyState();
                scheduleReady = true;
                updateSaveButtonEnabled();
            });
        }).start();
    }

    private void addEmptySlot() {
        int colorResource = editMode
                ? (categoryWork ? R.color.schedule_work : R.color.schedule_school)
                : R.color.schedule_school;
        TimeSlotItem item = new TimeSlotItem("", "", "9:00 AM", "5:00 PM",
                editMode && categoryWork, colorHex(colorResource));
        item.setGroupId(UUID.randomUUID().toString());
        timeSlotList.add(item);
        submitTimeSlots();
        recyclerTimeSlots.scrollToPosition(timeSlotList.size() - 1);
        updateEmptyState();
    }

    private void confirmDeleteSlot(TimeSlotItem item) {
        String itemName = item.getSubject() == null || item.getSubject().trim().isEmpty()
                ? getString(R.string.schedule_unnamed_item) : item.getSubject().trim();
        if (item.getId() > 0 && item.getGroupId() != null && !item.getGroupId().isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.schedule_delete_title, itemName))
                    .setMessage(R.string.schedule_delete_message)
                    .setNeutralButton("This day only", (dialog, which) -> deleteSlot(item))
                    .setPositiveButton("All days", (dialog, which) -> deleteGroup(item))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.schedule_delete_title, itemName))
                .setMessage(R.string.schedule_delete_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.schedule_delete_action, (dialogInterface, which) ->
                        deleteSlot(item))
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.ws_error)));
        dialog.show();
    }

    private void deleteGroup(TimeSlotItem item) {
        if (deletingSlot) return;
        deletingSlot = true;
        updateSaveButtonEnabled();
        recyclerTimeSlots.setEnabled(false);
        List<TimeSlotItem> currentItems = copyTimeSlots(timeSlotList);
        List<TimeSlotItem> originalItems = copyTimeSlots(originalEditItems);
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                List<ScheduleItem> stored = db.scheduleDao().getByGroupId(item.getGroupId());
                if (stored.isEmpty()) throw new IllegalStateException("Schedule group no longer exists");
                List<TimeSlotItem> removedItems = new ArrayList<>();
                List<TimeSlotItem> removedBaselines = new ArrayList<>();
                for (ScheduleItem schedule : stored) {
                    for (TimeSlotItem candidate : currentItems) {
                        if (candidate.getId() == schedule.getId()) {
                            removedItems.add(candidate);
                        }
                    }
                    TimeSlotItem baseline = null;
                    for (TimeSlotItem candidate : originalItems) {
                        if (candidate.getId() == schedule.getId()) {
                            baseline = candidate;
                            break;
                        }
                    }
                    if (baseline != null) {
                        removedBaselines.add(copyTimeSlot(baseline));
                    }
                }
                LocalDataRepository.deleteSchedules(db, stored);
                ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();
                runOnUiThread(() -> {
                    Set<Integer> removedIds = new HashSet<>();
                    for (ScheduleItem schedule : stored) removedIds.add(schedule.getId());
                    for (int i = timeSlotList.size() - 1; i >= 0; i--) {
                        if (removedIds.contains(timeSlotList.get(i).getId())) timeSlotList.remove(i);
                    }
                    originalEditItems.removeIf(original -> removedIds.contains(original.getId()));
                    submitTimeSlots();
                    updateEmptyState();
                    deletingSlot = false;
                    updateSaveButtonEnabled();
                    recyclerTimeSlots.setEnabled(true);
                    showGroupDeleteUndo(removedItems, removedBaselines, stored);
                });
            } catch (RuntimeException e) {
                android.util.Log.e("EditScheduleActivity", "Could not delete schedule group", e);
                runOnUiThread(() -> {
                    deletingSlot = false;
                    updateSaveButtonEnabled();
                    recyclerTimeSlots.setEnabled(true);
                    Toast.makeText(this, R.string.schedule_delete_failed, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showGroupDeleteUndo(List<TimeSlotItem> removedItems,
                                     List<TimeSlotItem> removedBaselines,
                                     List<ScheduleItem> stored) {
        Snackbar.make(findViewById(R.id.scrollContent), R.string.schedule_deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo, view -> new Thread(() -> {
                    try {
                        LocalDataRepository.restoreSchedules(
                                AppDatabase.getInstance(getApplicationContext()), stored);
                        ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();
                        runOnUiThread(() -> {
                            for (TimeSlotItem item : removedItems) timeSlotList.add(copyTimeSlot(item));
                            for (TimeSlotItem baseline : removedBaselines) {
                                originalEditItems.add(copyTimeSlot(baseline));
                            }
                            submitTimeSlots();
                            updateEmptyState();
                        });
                    } catch (RuntimeException e) {
                        android.util.Log.e("EditScheduleActivity", "Could not undo schedule group deletion", e);
                        runOnUiThread(() -> Toast.makeText(this,
                                R.string.schedule_restore_failed, Toast.LENGTH_LONG).show());
                    }
                }).start())
                .show();
    }

    private void deleteSlot(TimeSlotItem item) {
        TimeSlotItem editorItem = copyTimeSlot(item);
        TimeSlotItem originalItem = item.getId() > 0 ? findOriginalItem(item.getId()) : null;
        int originalPosition = originalItem == null ? -1 : originalEditItems.indexOf(originalItem);
        TimeSlotItem baseline = originalItem == null ? null : copyTimeSlot(originalItem);
        if (item.getId() <= 0) {
            int removedPosition = removeTimeSlot(item.getStableId());
            if (removedPosition >= 0) {
                showDeleteUndo(editorItem, baseline, originalPosition, removedPosition, null);
            }
            return;
        }

        if (deletingSlot) return;
        deletingSlot = true;
        updateSaveButtonEnabled();
        recyclerTimeSlots.setEnabled(false);
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                ScheduleItem storedItem = db.scheduleDao().findById(item.getId());
                if (storedItem == null || storedItem.isDeleted()) {
                    runOnUiThread(() -> {
                        deletingSlot = false;
                        updateSaveButtonEnabled();
                        recyclerTimeSlots.setEnabled(true);
                        Toast.makeText(this, R.string.schedule_delete_failed, Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                LocalDataRepository.deleteSchedule(db, storedItem);
                ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();
                runOnUiThread(() -> {
                    deletingSlot = false;
                    updateSaveButtonEnabled();
                    recyclerTimeSlots.setEnabled(true);
                    int removedPosition = removeTimeSlot(item.getStableId());
                    TimeSlotItem removedBaseline = findOriginalItem(item.getId());
                    int removedBaselinePosition = originalEditItems.indexOf(removedBaseline);
                    if (removedBaseline != null) originalEditItems.remove(removedBaseline);
                    if (removedPosition >= 0) {
                        showDeleteUndo(editorItem,
                                removedBaseline == null ? baseline : copyTimeSlot(removedBaseline),
                                removedBaselinePosition >= 0 ? removedBaselinePosition : originalPosition,
                                removedPosition, storedItem);
                    }
                });
            } catch (RuntimeException e) {
                android.util.Log.e("EditScheduleActivity", "Could not delete schedule item", e);
                runOnUiThread(() -> {
                    deletingSlot = false;
                    updateSaveButtonEnabled();
                    recyclerTimeSlots.setEnabled(true);
                    Toast.makeText(this, R.string.schedule_delete_failed, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private TimeSlotItem findOriginalItem(int id) {
        for (TimeSlotItem original : originalEditItems) {
            if (original.getId() == id) return original;
        }
        return null;
    }

    private void showDeleteUndo(TimeSlotItem editorItem, TimeSlotItem baseline,
                                int baselinePosition, int itemPosition, ScheduleItem storedItem) {
        Snackbar snackbar = Snackbar.make(findViewById(R.id.scrollContent),
                R.string.schedule_deleted, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.action_undo, view -> {
            if (storedItem != null) {
                new Thread(() -> {
                    try {
                        int restoredId = LocalDataRepository.restoreSchedule(
                                AppDatabase.getInstance(getApplicationContext()), storedItem);
                        ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();
                        runOnUiThread(() -> {
                            editorItem.setId(restoredId);
                            if (baseline != null) baseline.setId(restoredId);
                            restoreDeletedEditorItem(
                                    editorItem, baseline, baselinePosition, itemPosition);
                        });
                    } catch (RuntimeException e) {
                        android.util.Log.e("EditScheduleActivity", "Could not undo schedule deletion", e);
                        runOnUiThread(() ->
                                Toast.makeText(this, R.string.schedule_restore_failed, Toast.LENGTH_LONG).show());
                    }
                }).start();
            } else {
                restoreDeletedEditorItem(editorItem, baseline, baselinePosition, itemPosition);
            }
        });
        snackbar.show();
    }

    private void restoreDeletedEditorItem(TimeSlotItem editorItem, TimeSlotItem baseline,
                                          int baselinePosition, int itemPosition) {
        int insertAt = Math.max(0, Math.min(itemPosition, timeSlotList.size()));
        timeSlotList.add(insertAt, copyTimeSlot(editorItem));
        if (baseline != null) {
            int baselineInsertAt = Math.max(0, Math.min(baselinePosition, originalEditItems.size()));
            originalEditItems.add(baselineInsertAt, baseline);
        }
        submitTimeSlots();
        updateEmptyState();
    }

    private int removeTimeSlot(long stableId) {
        for (int i = 0; i < timeSlotList.size(); i++) {
            if (timeSlotList.get(i).getStableId() == stableId) {
                timeSlotList.remove(i);
                submitTimeSlots();
                updateEmptyState();
                return i;
            }
        }
        return -1;
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

    private List<TimeSlotItem> copyTimeSlots(List<TimeSlotItem> items) {
        List<TimeSlotItem> copies = new ArrayList<>(items.size());
        for (TimeSlotItem item : items) copies.add(copyTimeSlot(item));
        return copies;
    }

    private void submitTimeSlots() {
        refreshScheduleValidation();
        adapter.submitList(copyTimeSlots(timeSlotList));
        updateSaveButtonEnabled();
    }

    private void refreshScheduleValidation() {
        SimpleDateFormat formatter = new SimpleDateFormat("h:mm a", Locale.US);
        formatter.setLenient(false);
        for (TimeSlotItem item : timeSlotList) {
            if (item.getDays().isEmpty()) {
                item.setScheduleError("Select at least one day.");
                continue;
            }
            Date start = parseTime(item.getStartTime(), formatter);
            Date end = parseTime(item.getEndTime(), formatter);
            if (start == null || end == null) {
                item.setScheduleError("Enter valid start and end times.");
            } else if (ConflictChecker.timeToMinutes(item.getEndTime())
                    <= ConflictChecker.timeToMinutes(item.getStartTime())) {
                item.setScheduleError("End time must be after start time.");
            } else {
                item.setScheduleError(null);
            }
        }
    }

    private void updateSaveButtonEnabled() {
        boolean hasInvalidSchedule = false;
        for (TimeSlotItem item : timeSlotList) {
            if (item.getScheduleError() != null) {
                hasInvalidSchedule = true;
                break;
            }
        }
        btnSaveSchedule.setEnabled(scheduleReady && !deletingSlot && !saveInProgress
                && !timeSlotList.isEmpty() && !hasInvalidSchedule);
    }

    private void updateTimeSlotFromAdapter(TimeSlotItem originalItem, TimeSlotItem updatedItem) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> updateTimeSlotFromAdapter(originalItem, updatedItem));
            return;
        }
        long stableId = originalItem.getStableId();
        for (int i = 0; i < timeSlotList.size(); i++) {
            if (timeSlotList.get(i).getStableId() == stableId) {
                TimeSlotItem currentItem = timeSlotList.get(i);
                if (!Objects.equals(originalItem.getSubject(), updatedItem.getSubject())) {
                    currentItem.setSubject(updatedItem.getSubject());
                }
                if (!Objects.equals(originalItem.getDays(), updatedItem.getDays())) {
                    Set<String> mergedDays = new HashSet<>(currentItem.getDays());
                    Set<String> removedDays = new HashSet<>(originalItem.getDays());
                    removedDays.removeAll(updatedItem.getDays());
                    mergedDays.removeAll(removedDays);
                    Set<String> addedDays = new HashSet<>(updatedItem.getDays());
                    addedDays.removeAll(originalItem.getDays());
                    mergedDays.addAll(addedDays);
                    currentItem.setDays(mergedDays);
                    if (!addedDays.isEmpty()) {
                        for (TimeSlotItem otherItem : timeSlotList) {
                            if (otherItem == currentItem) continue;
                            Set<String> otherDays = new HashSet<>(otherItem.getDays());
                            if (otherDays.removeAll(addedDays)) otherItem.setDays(otherDays);
                        }
                    }
                }
                if (!Objects.equals(originalItem.getStartTime(), updatedItem.getStartTime())) {
                    currentItem.setStartTime(updatedItem.getStartTime());
                }
                if (!Objects.equals(originalItem.getEndTime(), updatedItem.getEndTime())) {
                    currentItem.setEndTime(updatedItem.getEndTime());
                }
                if (originalItem.isWork() != updatedItem.isWork()) {
                    currentItem.setWork(updatedItem.isWork());
                }
                if (!Objects.equals(originalItem.getColorHex(), updatedItem.getColorHex())) {
                    currentItem.setColorHex(updatedItem.getColorHex());
                }
                if (!Objects.equals(originalItem.getSubjectError(), updatedItem.getSubjectError())) {
                    currentItem.setSubjectError(updatedItem.getSubjectError());
                }
                if (!Objects.equals(originalItem.getStartTimeError(), updatedItem.getStartTimeError())) {
                    currentItem.setStartTimeError(updatedItem.getStartTimeError());
                }
                if (!Objects.equals(originalItem.getEndTimeError(), updatedItem.getEndTimeError())) {
                    currentItem.setEndTimeError(updatedItem.getEndTimeError());
                }
                submitTimeSlots();
                updateEmptyState();
                return;
            }
        }
    }

    private void saveSchedule() {
        if (timeSlotList.isEmpty()) {
            Toast.makeText(this, "Please add at least one time block.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!validateEditRows()) return;
        if (editMode && isUnchanged()) {
            Toast.makeText(this, "No changes to save.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (askGroupedEditScope()) return;
        checkConflictsAndSave();
    }

    private boolean askGroupedEditScope() {
        for (TimeSlotItem item : timeSlotList) {
            String groupId = item.getGroupId();
            if (item.getId() <= 0 || groupId == null || groupId.isEmpty()
                    || resolvedEditGroups.contains(groupId) || !isEdited(item)) continue;
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Apply changes to grouped days?")
                    .setMessage("Choose whether this change applies to every day in this schedule group.")
                    .setPositiveButton("All days", (dialog, which) -> {
                        for (TimeSlotItem sibling : timeSlotList) {
                            if (groupId.equals(sibling.getGroupId())) {
                                sibling.setSubject(item.getSubject());
                                sibling.setStartTime(item.getStartTime());
                                sibling.setEndTime(item.getEndTime());
                                sibling.setWork(item.isWork());
                                sibling.setColorHex(item.getColorHex());
                            }
                        }
                        resolvedEditGroups.add(groupId);
                        submitTimeSlots();
                        saveSchedule();
                    })
                    .setNeutralButton("This day only", (dialog, which) -> {
                        Set<String> singleDay = new HashSet<>();
                        if (!item.getOriginalDay().isEmpty()) singleDay.add(item.getOriginalDay());
                        item.setDays(singleDay);
                        item.setGroupId(null);
                        resolvedEditGroups.add(groupId);
                        submitTimeSlots();
                        saveSchedule();
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return true;
        }
        return false;
    }

    private boolean isEdited(TimeSlotItem item) {
        TimeSlotItem original = findOriginalItem(item.getId());
        return original != null && (!Objects.equals(item.getSubject(), original.getSubject())
                || !Objects.equals(item.getStartTime(), original.getStartTime())
                || !Objects.equals(item.getEndTime(), original.getEndTime())
                || item.isWork() != original.isWork()
                || !Objects.equals(item.getColorHex(), original.getColorHex())
                || !Objects.equals(item.getDays(), original.getDays()));
    }

    private void checkConflictsAndSave() {
        saveInProgress = true;
        updateSaveButtonEnabled();
        btnSaveSchedule.setText(R.string.schedule_saving);
        List<ScheduleItem> itemsToSave = createScheduleItems();
        submitTimeSlots();
        List<Integer> deletedIds = findDeletedIds();

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                List<String> conflicts = findConflicts(db, itemsToSave, deletedIds);
                List<ScheduleItem> loadSchedules = new ArrayList<>();
                Set<Integer> replaced = new HashSet<>(deletedIds);
                for (ScheduleItem item : itemsToSave) if (item.getId() > 0) replaced.add(item.getId());
                for (ScheduleItem existing : db.scheduleDao().getAll()) {
                    if (!replaced.contains(existing.getId())) loadSchedules.add(existing);
                }
                loadSchedules.addAll(itemsToSave);
                LoadCalculator.LoadResult load = LoadCalculator.calculate(loadSchedules,
                        SupabaseClient.getInstance(getApplicationContext()).getWeeklyHoursLimit());
                runOnUiThread(() -> {
                    saveInProgress = false;
                    updateSaveButtonEnabled();
                    btnSaveSchedule.setText(R.string.schedule_save);
                    if (!conflicts.isEmpty()) {
                        StringBuilder summary = new StringBuilder();
                        for (String conflict : conflicts) summary.append(conflict).append('\n');
                        summary.append(String.format(Locale.US, "\nWeekly load: %.1f / %d hours",
                                load.totalHours, load.weeklyLimit));
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Schedule conflicts found")
                                .setMessage(summary.toString().trim())
                                .setNegativeButton("Review", null)
                                .setPositiveButton("Save anyway", (dialog, which) ->
                                        persistSchedules(itemsToSave, deletedIds))
                                .show();
                    } else {
                        persistSchedules(itemsToSave, deletedIds);
                    }
                });
            } catch (RuntimeException e) {
                android.util.Log.e("EditScheduleActivity", "Could not check schedule conflicts", e);
                runOnUiThread(() -> {
                    saveInProgress = false;
                    updateSaveButtonEnabled();
                    btnSaveSchedule.setText(R.string.schedule_save);
                    Toast.makeText(this, "Couldn't check schedule conflicts. Please try again.",
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void persistSchedules(List<ScheduleItem> itemsToSave, List<Integer> deletedIds) {
        saveInProgress = true;
        updateSaveButtonEnabled();
        btnSaveSchedule.setText(R.string.schedule_saving);
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                if (editMode) LocalDataRepository.saveEditedSchedules(db, itemsToSave, deletedIds);
                else LocalDataRepository.saveSchedules(db, itemsToSave);
                SupabaseClient.getInstance(getApplicationContext()).setHasUnsyncedChanges(true);
                ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();
                runOnUiThread(() -> {
                    Toast.makeText(this, editMode ? "Schedule updated." : "Schedule saved successfully.",
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (RuntimeException e) {
                android.util.Log.e("EditScheduleActivity", "Could not save schedule", e);
                runOnUiThread(() -> {
                    saveInProgress = false;
                    updateSaveButtonEnabled();
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
                if (endMin <= startMin) {
                    item.setEndTimeError("End time must be after start time.");
                }
            }
        }
        boolean valid = areRowsValid();
        if (!valid) {
            Toast.makeText(this, "Select at least one day and fill in all required fields.",
                    Toast.LENGTH_SHORT).show();
        }
        submitTimeSlots();
        return valid;
    }

    private boolean areRowsValid() {
        for (TimeSlotItem item : timeSlotList) {
            if (item.getDays().isEmpty()
                    || item.getScheduleError() != null
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
            if (item.getGroupId() == null && (item.getId() <= 0 || item.getDays().size() > 1)) {
                item.setGroupId(UUID.randomUUID().toString());
            }
            for (String day : item.getDays()) {
                ScheduleItem schedule = new ScheduleItem(item.getSubject().trim(), day,
                        item.getStartTime(), item.getEndTime(),
                        editMode ? categoryWork : item.isWork(), item.getColorHex());
                if (day.equals(item.getOriginalDay())) schedule.setId(item.getId());
                schedule.setGroupId(item.getGroupId());
                result.add(schedule);
            }
        }
        return result;
    }

    private List<Integer> findDeletedIds() {
        Set<Integer> retainedIds = new HashSet<>();
        for (TimeSlotItem item : timeSlotList) {
            if (item.getId() > 0 && item.getDays().contains(item.getOriginalDay())) {
                retainedIds.add(item.getId());
            }
        }
        List<Integer> deletedIds = new ArrayList<>();
        for (TimeSlotItem original : originalEditItems) {
            if (!retainedIds.contains(original.getId())) deletedIds.add(original.getId());
        }
        return deletedIds;
    }

    private List<String> findConflicts(AppDatabase db, List<ScheduleItem> proposed,
                                       List<Integer> deletedIds) {
        Set<Integer> replacedIds = new HashSet<>(deletedIds);
        for (ScheduleItem item : proposed) if (item.getId() > 0) replacedIds.add(item.getId());
        List<ScheduleItem> existingSchedules = db.scheduleDao().getAll();
        List<ScheduleItem> checked = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        for (ScheduleItem item : proposed) {
            for (ScheduleItem existing : existingSchedules) {
                if (replacedIds.contains(existing.getId())) continue;
                if (ConflictChecker.hasConflict(item.getDay(), item.getStartTime(), item.getEndTime(),
                        existing.getDay(), existing.getStartTime(), existing.getEndTime())) {
                    conflicts.add(formatConflict(item, existing));
                }
            }
            for (ScheduleItem previous : checked) {
                if (ConflictChecker.hasConflict(item.getDay(), item.getStartTime(), item.getEndTime(),
                        previous.getDay(), previous.getStartTime(), previous.getEndTime())) {
                    conflicts.add(formatConflict(item, previous));
                }
            }
            checked.add(item);
        }
        return conflicts;
    }

    private String formatConflict(ScheduleItem first, ScheduleItem second) {
        return first.getDay() + ": " + first.getSubject() + " (" + first.getStartTime() + " - "
                + first.getEndTime() + ") overlaps " + second.getSubject() + " ("
                + second.getStartTime() + " - " + second.getEndTime() + ")";
    }

    private TimeSlotItem copyTimeSlot(TimeSlotItem item) {
        return item.copy();
    }

    private boolean isUnchanged() {
        if (timeSlotList.size() != originalEditItems.size()) return false;
        Iterator<TimeSlotItem> originals = originalEditItems.iterator();
        for (TimeSlotItem current : timeSlotList) {
            TimeSlotItem original = originals.next();
            if (current.getId() != original.getId()
                    || !Objects.equals(current.getSubject(), original.getSubject())
                    || !Objects.equals(current.getDays(), original.getDays())
                    || !Objects.equals(current.getStartTime(), original.getStartTime())
                    || !Objects.equals(current.getEndTime(), original.getEndTime())
                    || current.isWork() != original.isWork()
                    || !Objects.equals(current.getColorHex(), original.getColorHex())
                    || !Objects.equals(current.getGroupId(), original.getGroupId())) return false;
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

    private void processImageWithMLKit(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(this::parseScannedText)
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to read text: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        runOnUiThread(() -> {
                            timeSlotList.add(new TimeSlotItem("", "MON", "", "", false,
                                    colorHex(R.color.schedule_school)));
                            submitTimeSlots();
                            updateEmptyState();
                        });
                    });
        } catch (IOException e) {
            android.util.Log.e("EditScheduleActivity", "Could not load image for text recognition", e);
            Toast.makeText(this, "Failed to read schedule image.", Toast.LENGTH_SHORT).show();
        }
    }

    private void parseScannedText(Text visionText) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> parseScannedText(visionText));
            return;
        }
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
        submitTimeSlots();
        if (!timeSlotList.isEmpty()) {
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
                    submitTimeSlots();
                    updateEmptyState();
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
