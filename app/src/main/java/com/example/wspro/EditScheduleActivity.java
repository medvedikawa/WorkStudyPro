package com.example.wspro;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class EditScheduleActivity extends AppCompatActivity {
    public static final String EXTRA_EDIT_MODE = "EDIT_MODE";

    private RecyclerView recyclerTimeSlots;
    private Button btnSaveSchedule;
    private TextView tvEmptyState;
    private TimeSlotAdapter adapter;
    private SkippedLinesAdapter skippedAdapter;
    private List<TimeSlotItem> timeSlotList;
    private List<TimeSlotItem> originalEditItems;
    private ScheduleReviewViewModel reviewModel;
    private ScheduleEditorRepository scheduleRepository;
    private TextView btnSchoolTab;
    private TextView btnWorkTab;
    private TextView btnSaveScheduleLabel;
    private TextView tvScheduleSubtitle;
    private TextView tvConflictBanner;
    private TextView btnJumpToConflict;
    private TextView tvCrossTabWarning;
    private final AtomicInteger conflictRequestId = new AtomicInteger();
    private final Set<Long> conflictStableIds = new HashSet<>();
    private final Map<Long, String> conflictReasons = new HashMap<>();
    private final Map<Long, String> crossTabWarnings = new HashMap<>();
    private int firstConflictPosition = -1;
    private long pendingConflictScrollId = Long.MIN_VALUE;
    private boolean editMode;
    private boolean categoryWork;
    private boolean scheduleReady;
    private boolean saveInProgress;
    private boolean blockingConflicts;
    private boolean conflictsChecking;
    private int blockingConflictCount;
    private final Set<String> resolvedEditGroups = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_schedule);

        recyclerTimeSlots = findViewById(R.id.recyclerTimeSlots);
        btnSaveSchedule = findViewById(R.id.btnSaveSchedule);
        ImageButton btnReturnBack = findViewById(R.id.btnReturnBack);
        TextView tvScheduleTitle = findViewById(R.id.tvScheduleTitle);
        tvScheduleSubtitle = findViewById(R.id.tvScheduleSubtitle);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        btnSchoolTab = findViewById(R.id.btnSchoolTab);
        btnWorkTab = findViewById(R.id.btnWorkTab);
        btnSaveScheduleLabel = btnSaveSchedule;
        tvConflictBanner = findViewById(R.id.tvConflictBanner);
        btnJumpToConflict = findViewById(R.id.btnJumpToConflict);
        tvCrossTabWarning = findViewById(R.id.tvCrossTabWarning);

        LinearLayout previewCard = findViewById(R.id.layoutScannedPreviewCard);
        ImageView thumbnail = findViewById(R.id.ivScannedThumbnail);
        Button recropButton = findViewById(R.id.btnReCrop);
        reviewModel = new ViewModelProvider(this).get(ScheduleReviewViewModel.class);
        scheduleRepository = new ScheduleEditorRepository(getApplicationContext());
        editMode = getIntent().getBooleanExtra(EXTRA_EDIT_MODE, false);
        categoryWork = reviewModel.activeTab == 1;
        scheduleReady = false;
        timeSlotList = draftFor(categoryWork);
        originalEditItems = originalsFor(categoryWork);
        if (!editMode && !reviewModel.schoolLoaded) {
            reviewModel.schoolLoaded = true;
            timeSlotList.addAll(new ArrayList<>());
        }
        recyclerTimeSlots.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TimeSlotAdapter(this);
        adapter.setOnItemChangedListener(this::updateTimeSlotFromAdapter);
        adapter.setOnDeleteSlotListener(this::confirmDeleteSlot);
        adapter.setOnAddSlotListener(this::addTimeAfter);
        skippedAdapter = new SkippedLinesAdapter(getLayoutInflater());
        skippedAdapter.setListener(new SkippedLinesAdapter.Listener() {
            @Override
            public void onAddLine(String line) {
                TimeSlotItem item = new TimeSlotItem(line, "", "", "", categoryWork,
                        colorHex(categoryWork ? R.color.schedule_work : R.color.schedule_school));
                timeSlotList.add(item);
                adapter.setExpandedStableId(item.getStableId());
                reviewModel.skippedLines.remove(line);
                submitTimeSlots();
                updateEmptyState();
                setupSkippedLinesSection(reviewModel.skippedLines);
                recyclerTimeSlots.post(() -> recyclerTimeSlots.scrollToPosition(
                        Math.max(0, adapter.getItemCount() - 1)));
            }

            @Override
            public void onDismissLine(String line) {
                reviewModel.skippedLines.remove(line);
                setupSkippedLinesSection(reviewModel.skippedLines);
            }

            @Override
            public void onDismiss() {
                reviewModel.skippedLines.clear();
                setupSkippedLinesSection(reviewModel.skippedLines);
            }

            @Override
            public void onExpandedChanged(boolean expanded) {
                reviewModel.skippedExpanded = expanded;
                setupSkippedLinesSection(reviewModel.skippedLines);
            }
        });
        recyclerTimeSlots.setAdapter(new androidx.recyclerview.widget.ConcatAdapter(adapter, skippedAdapter));
        submitTimeSlots();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                requestBack();
            }
        });
        btnReturnBack.setOnClickListener(v -> requestBack());
        btnSaveSchedule.setOnClickListener(v -> saveSchedule());
        findViewById(R.id.btnAddScheduleEntry).setOnClickListener(v -> addEmptySlot());
        btnSchoolTab.setOnClickListener(v -> switchTab(false));
        btnWorkTab.setOnClickListener(v -> switchTab(true));
        btnJumpToConflict.setOnClickListener(v -> jumpToFirstConflict());
        setupFilters();

        if (editMode) {
            tvScheduleTitle.setText("Review schedule");
            previewCard.setVisibility(View.GONE);
            loadCategorySchedules(categoryWork);
        } else {
            initializeScanMode(previewCard, thumbnail, recropButton);
            scheduleReady = true;
            renderActiveState();
        }
        updateTabUi();
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
            thumbnail.setOnClickListener(v -> showScannedImage(imageUri));
            recropButton.setOnClickListener(v -> {
                Intent cropIntent = new Intent(EditScheduleActivity.this, CropRotateActivity.class);
                cropIntent.putExtra("RAW_IMAGE_URI", finalRaw);
                startActivity(cropIntent);
                finish();
            });
            if (!reviewModel.scanParsed) processImageWithMLKit(imageUri);
        } else {
            addEmptySlot();
        }
    }

    private List<TimeSlotItem> draftFor(boolean work) {
        return work ? reviewModel.workDraft : reviewModel.schoolDraft;
    }

    private List<TimeSlotItem> originalsFor(boolean work) {
        return work ? reviewModel.workOriginals : reviewModel.schoolOriginals;
    }

    private void switchTab(boolean work) {
        if (categoryWork == work) return;
        saveActiveDraft();
        categoryWork = work;
        reviewModel.activeTab = work ? 1 : 0;
        timeSlotList = draftFor(work);
        originalEditItems = originalsFor(work);
        scheduleReady = !editMode || (work ? reviewModel.workLoaded : reviewModel.schoolLoaded);
        conflictStableIds.clear();
        blockingConflicts = false;
        tvConflictBanner.setVisibility(View.GONE);
        btnJumpToConflict.setVisibility(View.GONE);
        tvCrossTabWarning.setVisibility(View.GONE);
        updateTabUi();
        if (editMode && !scheduleReady) {
            renderActiveState();
            loadCategorySchedules(work);
        } else {
            renderActiveState();
        }
    }

    private void saveActiveDraft() {
        List<TimeSlotItem> draft = draftFor(categoryWork);
        List<TimeSlotItem> draftSnapshot = copyTimeSlots(timeSlotList);
        draft.clear();
        draft.addAll(draftSnapshot);
        List<TimeSlotItem> originals = originalsFor(categoryWork);
        List<TimeSlotItem> originalSnapshot = copyTimeSlots(originalEditItems);
        originals.clear();
        originals.addAll(originalSnapshot);
    }

    private void updateTabUi() {
        btnSchoolTab.setBackgroundResource(categoryWork
                ? R.drawable.bg_schedule_tab_unselected : R.drawable.bg_schedule_tab_selected);
        btnSchoolTab.setTextColor(ContextCompat.getColor(this, categoryWork
                ? R.color.text_muted : R.color.ws_on_brand));
        btnSchoolTab.setCompoundDrawablesRelativeWithIntrinsicBounds(
                categoryWork ? R.drawable.ic_schedule_school : R.drawable.ic_schedule_school_selected,
                0, 0, 0);
        btnWorkTab.setBackgroundResource(categoryWork
                ? R.drawable.bg_schedule_tab_selected : R.drawable.bg_schedule_tab_unselected);
        btnWorkTab.setTextColor(ContextCompat.getColor(this, categoryWork
                ? R.color.ws_on_brand : R.color.text_muted));
        btnWorkTab.setCompoundDrawablesRelativeWithIntrinsicBounds(
                categoryWork ? R.drawable.ic_schedule_work_selected : R.drawable.ic_schedule_work,
                0, 0, 0);
        findViewById(R.id.btnSaveSchedule).setContentDescription(
                getString(categoryWork ? R.string.schedule_work : R.string.schedule_school));
        btnSaveScheduleLabel.setText(blockingConflicts
                ? "Fix " + conflictCount() + " conflicts to save"
                : categoryWork ? "Save work schedule" : "Save school schedule");
        tvScheduleSubtitle.setText(timeSlotList.size() + " classes found · "
                + attentionCount() + " need attention");
        View preview = findViewById(R.id.layoutScannedPreviewCard);
        boolean hasScanImage = getIntent().getStringExtra("CAPTURED_IMAGE_URI") != null;
        preview.setVisibility(!editMode && hasScanImage && !categoryWork ? View.VISIBLE : View.GONE);
    }

    private void loadCategorySchedules(boolean work) {
        if (work ? reviewModel.workLoaded : reviewModel.schoolLoaded) {
            scheduleReady = true;
            renderActiveState();
            return;
        }
        new Thread(() -> {
            List<TimeSlotItem> loadedItems = scheduleRepository.loadCategory(work);
            List<TimeSlotItem> loadedOriginals = copyTimeSlots(loadedItems);
            List<TimeSlotItem> targetDraft = draftFor(work);
            List<TimeSlotItem> targetOriginals = originalsFor(work);
            runOnUiThread(() -> {
                if (targetOriginals.isEmpty()) {
                    List<TimeSlotItem> pendingDraft = copyTimeSlots(targetDraft);
                    targetDraft.clear();
                    targetDraft.addAll(loadedItems);
                    for (TimeSlotItem pending : pendingDraft) {
                        boolean alreadyPresent = false;
                        for (TimeSlotItem loaded : targetDraft) {
                            if (loaded.getStableId() == pending.getStableId()) {
                                alreadyPresent = true;
                                break;
                            }
                        }
                        if (!alreadyPresent) targetDraft.add(pending);
                    }
                    targetOriginals.addAll(loadedOriginals);
                }
                if (work) reviewModel.workLoaded = true;
                else reviewModel.schoolLoaded = true;
                if (categoryWork == work) {
                    timeSlotList = targetDraft;
                    originalEditItems = targetOriginals;
                    scheduleReady = true;
                    renderActiveState();
                }
            });
        }).start();
    }

    private void addEmptySlot() {
        int colorResource = categoryWork ? R.color.schedule_work : R.color.schedule_school;
        TimeSlotItem item = new TimeSlotItem("", "", "", "",
                categoryWork, colorHex(colorResource));
        item.setGroupId(UUID.randomUUID().toString());
        timeSlotList.add(item);
        adapter.setExpandedStableId(item.getStableId());
        submitTimeSlots();
        recyclerTimeSlots.post(() -> {
            if (adapter.getItemCount() > 0) recyclerTimeSlots.scrollToPosition(adapter.getItemCount() - 1);
        });
        updateEmptyState();
    }

    private void addTimeAfter(TimeSlotItem after) {
        int colorResource = categoryWork ? R.color.schedule_work : R.color.schedule_school;
        TimeSlotItem item = new TimeSlotItem(after.getSubject(), "", after.getStartTime(),
                after.getEndTime(), categoryWork, colorHex(colorResource));
        item.setGroupId(UUID.randomUUID().toString());
        int index = timeSlotList.indexOf(after);
        timeSlotList.add(index < 0 ? timeSlotList.size() : index + 1, item);
        adapter.setExpandedStableId(item.getStableId());
        submitTimeSlots();
        recyclerTimeSlots.post(() -> recyclerTimeSlots.scrollToPosition(
                adapter.getCurrentList().size() - 1));
    }

    private void confirmDeleteSlot(TimeSlotItem item) {
        String itemName = item.getSubject() == null || item.getSubject().trim().isEmpty()
                ? getString(R.string.schedule_unnamed_item) : item.getSubject().trim();
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.schedule_delete_title, itemName))
                .setMessage(R.string.schedule_delete_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.schedule_delete_action, (dialogInterface, which) ->
                        removeDraftSlot(item))
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.ws_error)));
        dialog.show();
    }

    private void removeDraftSlot(TimeSlotItem item) {
        for (int i = 0; i < timeSlotList.size(); i++) {
            if (timeSlotList.get(i).getStableId() == item.getStableId()) {
                timeSlotList.remove(i);
                submitTimeSlots();
                updateEmptyState();
                return;
            }
        }
    }

    private TimeSlotItem findOriginalItem(int id) {
        for (TimeSlotItem original : originalEditItems) {
            if (original.getId() == id) return original;
        }
        return null;
    }

    private String colorHex(int colorResource) {
        int color = getColor(colorResource);
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private void updateEmptyState() {
        boolean isEmpty = timeSlotList.isEmpty();
        tvEmptyState.setText(isEmpty
                ? (categoryWork ? "No work entries yet. Add a time or save this empty schedule."
                : "No classes yet. Add a time or save this empty schedule.") : "");
        tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        tvEmptyState.setOnClickListener(isEmpty ? v -> addEmptySlot() : null);
    }

    private int attentionCount() {
        int count = 0;
        for (TimeSlotItem item : timeSlotList) {
            if (item.getFlagHintText() != null || item.getScheduleError() != null
                    || item.getSubjectError() != null || item.getStartTimeError() != null
                    || item.getEndTimeError() != null) count++;
        }
        return count;
    }

    private int conflictCount() {
        return blockingConflictCount;
    }

    private List<TimeSlotItem> copyTimeSlots(List<TimeSlotItem> items) {
        List<TimeSlotItem> copies = new ArrayList<>(items.size());
        for (TimeSlotItem item : items) copies.add(copyTimeSlot(item));
        return copies;
    }

    private void submitTimeSlots() {
        refreshScheduleValidation();
        submitFilteredTimeSlots();
        refreshConflicts();
    }

    private void submitFilteredTimeSlots() {
        List<TimeSlotItem> visibleItems = new ArrayList<>();
        for (TimeSlotItem item : timeSlotList) {
            boolean conflict = conflictStableIds.contains(item.getStableId());
            boolean flagged = item.getFlagHintText() != null
                    || item.getScheduleError() != null || item.getSubjectError() != null
                    || item.getStartTimeError() != null || item.getEndTimeError() != null;
            boolean include = reviewModel.activeFilter == 0
                    || (reviewModel.activeFilter == 1 && conflict)
                    || (reviewModel.activeFilter == 2 && !conflict && flagged)
                    || (reviewModel.activeFilter == 3 && !conflict && !flagged);
            if (include) {
                TimeSlotItem visible = copyTimeSlot(item);
                visible.setConflict(conflict);
                visible.setConflictReason(conflictReasons.get(item.getStableId()));
                visible.setCrossTabWarning(crossTabWarnings.get(item.getStableId()));
                visibleItems.add(visible);
            }
        }
        adapter.submitList(visibleItems, this::completeConflictScroll);
        updateFilterCounts();
        updateTabUi();
        updateSaveButtonEnabled();
    }

    private void updateFilterCounts() {
        int conflicts = 0;
        int check = 0;
        int ok = 0;
        for (TimeSlotItem item : timeSlotList) {
            if (conflictStableIds.contains(item.getStableId())) {
                conflicts++;
            } else if (item.getFlagHintText() != null || item.getScheduleError() != null
                    || item.getSubjectError() != null || item.getStartTimeError() != null
                    || item.getEndTimeError() != null) {
                check++;
            } else {
                ok++;
            }
        }
        ((TextView) findViewById(R.id.filterAll)).setText("All " + timeSlotList.size());
        ((TextView) findViewById(R.id.filterConflicts)).setText("Conflicts " + conflicts);
        ((TextView) findViewById(R.id.filterCheck)).setText("Check " + check);
        ((TextView) findViewById(R.id.filterOk)).setText("OK " + ok);
    }

    private void renderActiveState() {
        updateTabUi();
        updateEmptyState();
        setupSkippedLinesSection(reviewModel.skippedLines);
        syncFilterUi();
        submitTimeSlots();
    }

    private void setupFilters() {
        int[] ids = {R.id.filterAll, R.id.filterConflicts, R.id.filterCheck, R.id.filterOk};
        for (int i = 0; i < ids.length; i++) {
            final int filter = i;
            findViewById(ids[i]).setOnClickListener(v -> {
                reviewModel.activeFilter = filter;
                syncFilterUi();
                submitTimeSlots();
            });
        }
    }

    private void syncFilterUi() {
        int[] ids = {R.id.filterAll, R.id.filterConflicts, R.id.filterCheck, R.id.filterOk};
        for (int i = 0; i < ids.length; i++) {
            TextView chip = findViewById(ids[i]);
            boolean selected = i == reviewModel.activeFilter;
            chip.setBackgroundResource(selected
                    ? R.drawable.bg_schedule_filter_selected
                    : R.drawable.bg_schedule_filter_unselected);
            chip.setTextColor(ContextCompat.getColor(this,
                    selected ? R.color.ws_on_brand : R.color.text_muted));
        }
    }

    private void refreshConflicts() {
        final int requestId = conflictRequestId.incrementAndGet();
        final boolean work = categoryWork;
        final List<TimeSlotItem> draft = copyTimeSlots(timeSlotList);
        final boolean otherDraftLoaded = work ? reviewModel.schoolLoaded : reviewModel.workLoaded;
        final List<TimeSlotItem> otherDraft = copyTimeSlots(draftFor(!work));
        final List<Integer> deleted = ScheduleDraftMapper.findDeletedIds(timeSlotList, originalEditItems);
        conflictsChecking = true;
        updateSaveButtonEnabled();
        new Thread(() -> {
            try {
                List<ScheduleItem> proposed = ScheduleDraftMapper.toScheduleItems(draft, work);
                List<ScheduleItem> otherCategory = otherDraftLoaded
                        ? ScheduleDraftMapper.toScheduleItems(otherDraft, !work)
                        : scheduleRepository.loadCategorySchedules(!work);
                List<String> blocking = scheduleRepository.findConflicts(proposed, deleted);
                Map<Long, String> newCrossTabWarnings = new HashMap<>();
                for (ScheduleItem item : proposed) {
                    for (ScheduleItem existing : otherCategory) {
                        if (ConflictChecker.hasConflict(item.getDay(), item.getStartTime(), item.getEndTime(),
                                existing.getDay(), existing.getStartTime(), existing.getEndTime())) {
                            for (TimeSlotItem draftItem : draft) {
                                if (draftItem.getSubject().equals(item.getSubject())
                                        && draftItem.getDays().contains(item.getDay())
                                        && draftItem.getStartTime().equals(item.getStartTime())
                                        && draftItem.getEndTime().equals(item.getEndTime())) {
                                    newCrossTabWarnings.put(draftItem.getStableId(),
                                            "Overlaps with your " + (work ? "school class" : "work shift")
                                                    + ", " + ScheduleConflictText.displayDay(existing.getDay()) + " "
                                                    + existing.getStartTime() + " to " + existing.getEndTime());
                                }
                            }
                        }
                    }
                }
                runOnUiThread(() -> {
                    if (requestId != conflictRequestId.get() || categoryWork != work) return;
                    conflictsChecking = false;
                    conflictStableIds.clear();
                    conflictReasons.clear();
                    crossTabWarnings.clear();
                    crossTabWarnings.putAll(newCrossTabWarnings);
                    blockingConflicts = !blocking.isEmpty();
                    blockingConflictCount = blocking.size();
                    for (String conflict : blocking) {
                        for (TimeSlotItem item : timeSlotList) {
                            String subject = item.getSubject() == null ? "" : item.getSubject();
                            if (!subject.trim().isEmpty()
                                    && (conflict.contains(": " + subject + " (")
                                    || conflict.contains("overlaps " + subject + " ("))) {
                                conflictStableIds.add(item.getStableId());
                                String other = ScheduleConflictText.counterpart(conflict, subject.trim());
                                if (other != null) {
                                    conflictReasons.put(item.getStableId(), "Overlaps with " + other);
                                }
                            }
                        }
                    }
                    firstConflictPosition = -1;
                    for (int i = 0; i < timeSlotList.size(); i++) {
                        if (conflictStableIds.contains(timeSlotList.get(i).getStableId())) {
                            firstConflictPosition = i;
                            break;
                        }
                    }
                    tvConflictBanner.setVisibility(blocking.isEmpty() ? View.GONE : View.VISIBLE);
                    tvConflictBanner.setText(blocking.isEmpty() ? "" :
                            blocking.size() + " conflicts block saving");
                    btnJumpToConflict.setVisibility(blocking.isEmpty() ? View.GONE : View.VISIBLE);
                    tvCrossTabWarning.setVisibility(View.GONE);
                    submitFilteredTimeSlots();
                });
            } catch (RuntimeException e) {
                android.util.Log.e("EditScheduleActivity", "Could not refresh conflicts", e);
                runOnUiThread(() -> {
                    if (requestId == conflictRequestId.get()) {
                        conflictsChecking = false;
                        updateSaveButtonEnabled();
                    }
                });
            }
        }).start();
    }

    private void jumpToFirstConflict() {
        if (firstConflictPosition < 0 || firstConflictPosition >= timeSlotList.size()) return;
        TimeSlotItem item = timeSlotList.get(firstConflictPosition);
        adapter.setExpandedStableId(item.getStableId());
        pendingConflictScrollId = item.getStableId();
        if (reviewModel.activeFilter != 0) {
            reviewModel.activeFilter = 0;
            syncFilterUi();
            submitTimeSlots();
        } else {
            completeConflictScroll();
        }
    }

    private void completeConflictScroll() {
        if (pendingConflictScrollId == Long.MIN_VALUE) return;
        int position = -1;
        for (int i = 0; i < adapter.getCurrentList().size(); i++) {
            if (adapter.getCurrentList().get(i).getStableId() == pendingConflictScrollId) {
                position = i;
                break;
            }
        }
        if (position < 0) return;
        adapter.notifyItemChanged(position);
        recyclerTimeSlots.smoothScrollToPosition(position);
        pendingConflictScrollId = Long.MIN_VALUE;
    }

    private void reloadActiveCategoryAfterSave() {
        final boolean work = categoryWork;
        scheduleReady = false;
        updateSaveButtonEnabled();
        new Thread(() -> {
            List<TimeSlotItem> refreshed = scheduleRepository.loadCategory(work);
            runOnUiThread(() -> {
                List<TimeSlotItem> target = draftFor(work);
                List<TimeSlotItem> originals = originalsFor(work);
                target.clear();
                originals.clear();
                target.addAll(refreshed);
                originals.addAll(copyTimeSlots(refreshed));
                if (categoryWork == work) {
                    timeSlotList = target;
                    originalEditItems = originals;
                    scheduleReady = true;
                    resolvedEditGroups.clear();
                    renderActiveState();
                }
            });
        }).start();
    }

    private void refreshScheduleValidation() {
        ScheduleTimeValidator.updateErrors(timeSlotList);
    }

    private void updateSaveButtonEnabled() {
        boolean hasInvalidSchedule = false;
        for (TimeSlotItem item : timeSlotList) {
            if (item.getScheduleError() != null || item.getSubjectError() != null
                    || item.getStartTimeError() != null || item.getEndTimeError() != null) {
                hasInvalidSchedule = true;
                break;
            }
        }
        btnSaveSchedule.setEnabled(scheduleReady && !saveInProgress
                && !conflictsChecking && !hasInvalidSchedule && !blockingConflicts);
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
                    currentItem.setDays(updatedItem.getDays());
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
                View focus = getCurrentFocus();
                boolean isEditingText = focus instanceof EditText;

                if (!isEditingText) {
                    submitTimeSlots();
                } else {
                    refreshScheduleValidation();
                    updateFilterCounts();
                    updateTabUi();
                    updateSaveButtonEnabled();
                }
                updateEmptyState();
                return;
            }
        }
    }

    private void saveSchedule() {
        if (!validateEditRows()) return;
        if (editMode && isUnchanged()) {
            Toast.makeText(this, "No changes to save.", Toast.LENGTH_SHORT).show();
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
        List<ScheduleItem> itemsToSave =
                ScheduleDraftMapper.toScheduleItems(timeSlotList, categoryWork);
        List<Integer> deletedIds =
                ScheduleDraftMapper.findDeletedIds(timeSlotList, originalEditItems);

        new Thread(() -> {
            try {
                ScheduleEditorRepository.SaveCheck check =
                        scheduleRepository.checkBeforeSave(itemsToSave, deletedIds);
                runOnUiThread(() -> {
                    saveInProgress = false;
                    updateSaveButtonEnabled();
                    updateTabUi();
                    if (!check.conflicts.isEmpty()) {
                        StringBuilder summary = new StringBuilder();
                        for (String conflict : check.conflicts) summary.append(conflict).append('\n');
                        summary.append(String.format(Locale.US, "\nWeekly load: %.1f / %d hours",
                                check.load.totalHours, check.load.weeklyLimit));
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Schedule conflicts found")
                                .setMessage(summary.toString().trim())
                                .setPositiveButton("Review", null)
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
                    updateTabUi();
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
                scheduleRepository.save(editMode, itemsToSave, deletedIds);
                runOnUiThread(() -> {
                    saveInProgress = false;
                    if (editMode) {
                        Toast.makeText(this, "Schedule updated.", Toast.LENGTH_SHORT).show();
                        reloadActiveCategoryAfterSave();
                    } else {
                        Toast.makeText(this, "Schedule saved successfully.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            } catch (RuntimeException e) {
                android.util.Log.e("EditScheduleActivity", "Could not save schedule", e);
                runOnUiThread(() -> {
                    saveInProgress = false;
                    updateSaveButtonEnabled();
                    updateTabUi();
                    Toast.makeText(this, "Couldn't save your schedule. Please try again.",
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private boolean validateEditRows() {
        boolean valid = ScheduleTimeValidator.validateForSave(timeSlotList);
        if (!valid) {
            Toast.makeText(this, "Select at least one day and fill in all required fields.",
                    Toast.LENGTH_SHORT).show();
        }
        submitTimeSlots();
        return valid;
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
        if (!editMode || allDraftsUnchanged()) {
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

    private boolean allDraftsUnchanged() {
        return draftsEqual(reviewModel.schoolDraft, reviewModel.schoolOriginals)
                && draftsEqual(reviewModel.workDraft, reviewModel.workOriginals);
    }

    private boolean draftsEqual(List<TimeSlotItem> current, List<TimeSlotItem> originals) {
        if (current.size() != originals.size()) return false;
        for (int i = 0; i < current.size(); i++) {
            TimeSlotItem item = current.get(i);
            TimeSlotItem original = originals.get(i);
            if (item.getId() != original.getId()
                    || !Objects.equals(item.getSubject(), original.getSubject())
                    || !Objects.equals(item.getDays(), original.getDays())
                    || !Objects.equals(item.getStartTime(), original.getStartTime())
                    || !Objects.equals(item.getEndTime(), original.getEndTime())
                    || !Objects.equals(item.getGroupId(), original.getGroupId())
                    || !Objects.equals(item.getColorHex(), original.getColorHex())) return false;
        }
        return true;
    }

    private void processImageWithMLKit(Uri imageUri) {
        ScheduleScanProcessor.scan(this, imageUri, new ScheduleScanProcessor.Listener() {
            @Override
            public void onScanComplete(ScheduleTableParser.ParseResult result) {
                runOnUiThread(() -> applyScanResult(result));
            }

            @Override
            public void onScanFailed(Exception error) {
                android.util.Log.e("EditScheduleActivity", "Could not recognize schedule text", error);
                runOnUiThread(() -> {
                    Toast.makeText(EditScheduleActivity.this,
                            "Failed to read text: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    timeSlotList.add(new TimeSlotItem("", "MON", "", "", false,
                            colorHex(R.color.schedule_school)));
                    submitTimeSlots();
                    updateEmptyState();
                });
            }

            @Override
            public void onImageLoadFailed(java.io.IOException error) {
                android.util.Log.e("EditScheduleActivity",
                        "Could not load image for text recognition", error);
                Toast.makeText(EditScheduleActivity.this,
                        "Failed to read schedule image.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyScanResult(ScheduleTableParser.ParseResult result) {
        if (result.qualityGateFailed) {
            showQualityGateDialog(result.qualityGateMessage);
            return;
        }

        timeSlotList.clear();
        reviewModel.scanParsed = true;

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

        reviewModel.skippedLines.clear();
        if (result.skippedLines != null) reviewModel.skippedLines.addAll(result.skippedLines);
        setupSkippedLinesSection(reviewModel.skippedLines);
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
        skippedAdapter.submitState(skippedLines, reviewModel.skippedExpanded);
    }

    private void showScannedImage(Uri imageUri) {
        Dialog dialog = new Dialog(this);
        ImageView imageView = new ImageView(this);
        imageView.setImageURI(imageUri);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(Color.BLACK);
        imageView.setContentDescription("Scanned schedule photo. Tap to close.");
        imageView.setOnClickListener(v -> dialog.dismiss());

        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(Color.BLACK);
        container.addView(imageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        dialog.setContentView(container);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }
}
