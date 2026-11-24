package com.example.wspro;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class TasksActivity extends AppCompatActivity {

    private RecyclerView recyclerTasks;
    private AssignmentAdapter adapter;
    private LinearLayout layoutEmptyTasks;
    private TextView tvTaskSubtitle;
    private TextView tvEmptyTasksTitle;
    private TextView tvEmptyTasksBody;
    private TextView btnClearTaskFilters;
    private EditText etTaskSearch;
    private View taskSearchRow;
    private TextView chipTaskCategoryFilter;
    private TextView chipTaskSubjectFilter;
    private TextView chipTaskDueFilter;
    private TextView chipTaskStatusFilter;
    private CheckBox cbSelectAllTasks;
    private Button btnBulkDelete;
    private View layoutBulkActions;

    private ScanButtonHelper scanButtonHelper;
    private TextInputLayout activeSubjectLayout;
    private MaterialAutoCompleteTextView activeSubjectField;
    private SubjectAdapter activeSubjectAdapter;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearchRunnable;
    private LiveData<List<AssignmentItem>> currentTasksLiveData;
    private Observer<List<AssignmentItem>> currentTasksObserver;
    private List<AssignmentItem> visibleAssignments = new ArrayList<>();
    private List<ScheduleItem> currentSchedules = new ArrayList<>();
    private List<String> schoolSubjects = new ArrayList<>();
    private String searchQuery = "";
    private String categoryFilter = "";
    private String subjectFilter = "";
    private int dueFilter = -1;
    private int statusFilter = -1;
    private boolean cloudFetchStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasks);

        scanButtonHelper = new ScanButtonHelper(this);

        recyclerTasks = findViewById(R.id.recyclerTasks);
        layoutEmptyTasks = findViewById(R.id.layoutEmptyTasks);
        tvTaskSubtitle = findViewById(R.id.tvTaskSubtitle);
        tvEmptyTasksTitle = findViewById(R.id.tvEmptyTasksTitle);
        tvEmptyTasksBody = findViewById(R.id.tvEmptyTasksBody);
        Button btnAddNewTaskHeader = findViewById(R.id.btnAddNewTaskHeader);
        Button btnEmptyAddTask = findViewById(R.id.btnEmptyAddTask);

        cbSelectAllTasks = findViewById(R.id.cbSelectAllTasks);
        btnBulkDelete = findViewById(R.id.btnBulkDelete);
        layoutBulkActions = findViewById(R.id.layoutBulkActions);

        btnAddNewTaskHeader.setOnClickListener(v -> showAssignmentDialog(null));
        btnEmptyAddTask.setOnClickListener(v -> showAssignmentDialog(null));
        setupTaskFilters();
        setupBulkActions();

        recyclerTasks.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AssignmentAdapter(this, new ArrayList<>(), new AssignmentAdapter.OnAssignmentActionListener() {
            @Override
            public void onToggleComplete(AssignmentItem item, boolean isCompleted) {
                item.setCompleted(isCompleted);
                new Thread(() -> {
                    LocalDataRepository.updateAssignment(
                            AppDatabase.getInstance(getApplicationContext()), item);
                    ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();
                }).start();
            }

            @Override
            public void onEdit(AssignmentItem item) {
                showAssignmentDialog(item);
            }

            @Override
            public void onDelete(AssignmentItem item) {
                new AlertDialog.Builder(TasksActivity.this)
                        .setTitle("Delete task?")
                        .setMessage("This task will be removed from your list.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (dialog, which) -> new Thread(() -> {
                            LocalDataRepository.deleteAssignment(
                                    AppDatabase.getInstance(getApplicationContext()), item);
                            ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();
                            runOnUiThread(() -> Toast.makeText(TasksActivity.this, "Task deleted", Toast.LENGTH_SHORT).show());
                        }).start())
                        .show();
            }

            @Override
            public void onSelectionChanged(AssignmentItem item, boolean isSelected) {
                updateBulkActionsVisibility();
            }
        });
        recyclerTasks.setAdapter(adapter);

        setupBottomNav();
        setupReactiveObservers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPrioritiesAndRender();
    }

    @Override
    protected void onDestroy() {
        cancelPendingSearch();
        super.onDestroy();
    }

    private void setupBottomNav() {
        BottomNavHelper.setup(this, BottomNavHelper.Tab.TASKS, scanButtonHelper);
    }

    private void setupBulkActions() {
        cbSelectAllTasks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (AssignmentItem item : visibleAssignments) {
                item.setSelected(isChecked);
            }
            adapter.notifyDataSetChanged();
            updateBulkActionsVisibility();
        });

        btnBulkDelete.setOnClickListener(v -> {
            List<AssignmentItem> selectedItems = new ArrayList<>();
            for (AssignmentItem item : visibleAssignments) {
                if (item.isSelected()) selectedItems.add(item);
            }
            if (selectedItems.isEmpty()) return;

            new AlertDialog.Builder(this)
                    .setTitle("Delete selected tasks?")
                    .setMessage("Selected tasks (" + selectedItems.size() + ") will be permanently removed.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (dialog, which) -> new Thread(() -> {
                        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                        for (AssignmentItem item : selectedItems) {
                            LocalDataRepository.deleteAssignment(db, item);
                        }
                        ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();
                        runOnUiThread(() -> {
                            cbSelectAllTasks.setChecked(false);
                            Toast.makeText(this, "Selected tasks deleted", Toast.LENGTH_SHORT).show();
                        });
                    }).start())
                    .show();
        });
    }

    private void updateBulkActionsVisibility() {
        if (visibleAssignments.isEmpty()) {
            layoutBulkActions.setVisibility(View.GONE);
            return;
        }
        layoutBulkActions.setVisibility(View.VISIBLE);
        boolean allSelected = true;
        boolean anySelected = false;
        for (AssignmentItem item : visibleAssignments) {
            if (item.isSelected()) {
                anySelected = true;
            } else {
                allSelected = false;
            }
        }
        cbSelectAllTasks.setOnCheckedChangeListener(null);
        cbSelectAllTasks.setChecked(allSelected && !visibleAssignments.isEmpty());
        setupBulkActions();
        btnBulkDelete.setEnabled(anySelected);
        btnBulkDelete.setAlpha(anySelected ? 1.0f : 0.5f);
    }

    private void setupReactiveObservers() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());

        db.scheduleDao().getAllLiveData().observe(this, schedules -> {
            currentSchedules = schedules != null ? schedules : new ArrayList<>();
            applyPrioritiesAndRender();
        });

        db.scheduleDao().getSchoolSubjectNames().observe(this, subjects -> {
            schoolSubjects = subjects != null ? subjects : new ArrayList<>();
            renderFilterChips();
            if (activeSubjectField != null && activeSubjectLayout != null) {
                configureSubjectDropdown(activeSubjectLayout, activeSubjectField, schoolSubjects);
            }
        });

        db.assignmentDao().getAllAssignmentsLiveData().observe(this, assignments -> {
            if (assignments != null && assignments.isEmpty() && !cloudFetchStarted
                    && SupabaseClient.getInstance(getApplicationContext()).isLoggedIn()) {
                cloudFetchStarted = true;
                SupabaseClient.getInstance(getApplicationContext()).fetchAssignmentsFromCloud(new SupabaseClient.DataCallback<>() {
                    @Override
                    public void onSuccess(List<AssignmentItem> cloudItems) {
                        if (cloudItems != null && !cloudItems.isEmpty()) {
                            new Thread(() -> LocalDataRepository.insertCloudAssignments(db, cloudItems)).start();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                    }
                });
            }
        });

        observeFilteredTasks();
    }

    private void setupTaskFilters() {
        ImageButton btnSearchTasks = findViewById(R.id.btnSearchTasks);
        ImageButton btnCloseTaskSearch = findViewById(R.id.btnCloseTaskSearch);
        taskSearchRow = findViewById(R.id.taskSearchRow);
        etTaskSearch = findViewById(R.id.etTaskSearch);
        chipTaskCategoryFilter = findViewById(R.id.chipTaskCategoryFilter);
        chipTaskSubjectFilter = findViewById(R.id.chipTaskSubjectFilter);
        chipTaskDueFilter = findViewById(R.id.chipTaskDueFilter);
        chipTaskStatusFilter = findViewById(R.id.chipTaskStatusFilter);
        btnClearTaskFilters = findViewById(R.id.btnClearTaskFilters);

        btnSearchTasks.setOnClickListener(v -> {
            taskSearchRow.setVisibility(View.VISIBLE);
            etTaskSearch.requestFocus();
            etTaskSearch.post(() -> {
                android.view.inputmethod.InputMethodManager inputMethodManager =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (inputMethodManager != null) {
                    inputMethodManager.showSoftInput(etTaskSearch,
                            android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });
        });
        btnCloseTaskSearch.setOnClickListener(v -> {
            etTaskSearch.setText("");
            searchQuery = "";
            cancelPendingSearch();
            taskSearchRow.setVisibility(View.GONE);
            hideSearchKeyboard();
            updateClearAllVisibility();
            observeFilteredTasks();
        });

        etTaskSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateClearAllVisibility();
                cancelPendingSearch();
                String query = s.toString().trim();
                pendingSearchRunnable = () -> {
                    searchQuery = query;
                    observeFilteredTasks();
                };
                searchHandler.postDelayed(pendingSearchRunnable, 300);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        chipTaskCategoryFilter.setOnClickListener(v -> showCategoryFilterMenu(v));
        chipTaskSubjectFilter.setOnClickListener(v -> showSubjectFilterMenu(v));
        chipTaskDueFilter.setOnClickListener(v -> showDueFilterMenu(v));
        chipTaskStatusFilter.setOnClickListener(v -> showStatusFilterMenu(v));
        btnClearTaskFilters.setOnClickListener(v -> clearTaskFilters());
        renderFilterChips();
    }

    private void observeFilteredTasks() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        if (currentTasksLiveData != null && currentTasksObserver != null) {
            currentTasksLiveData.removeObserver(currentTasksObserver);
        }

        Calendar now = Calendar.getInstance();
        long nowTimestamp = now.getTimeInMillis();
        Calendar dayStart = (Calendar) now.clone();
        dayStart.set(Calendar.HOUR_OF_DAY, 0);
        dayStart.set(Calendar.MINUTE, 0);
        dayStart.set(Calendar.SECOND, 0);
        dayStart.set(Calendar.MILLISECOND, 0);
        long dayStartTimestamp = dayStart.getTimeInMillis();

        Calendar tomorrowStart = (Calendar) dayStart.clone();
        tomorrowStart.add(Calendar.DAY_OF_YEAR, 1);
        long tomorrowStartTimestamp = tomorrowStart.getTimeInMillis();

        Calendar weekStart = (Calendar) dayStart.clone();
        int daysFromWeekStart = (weekStart.get(Calendar.DAY_OF_WEEK)
                - weekStart.getFirstDayOfWeek() + 7) % 7;
        weekStart.add(Calendar.DAY_OF_YEAR, -daysFromWeekStart);
        Calendar nextWeekStart = (Calendar) weekStart.clone();
        nextWeekStart.add(Calendar.DAY_OF_YEAR, 7);

        currentTasksLiveData = db.assignmentDao().searchAssignmentsLiveData(
                searchQuery,
                categoryFilter,
                subjectFilter,
                statusFilter,
                dueFilter,
                nowTimestamp,
                dayStartTimestamp,
                tomorrowStartTimestamp,
                weekStart.getTimeInMillis(),
                nextWeekStart.getTimeInMillis());
        currentTasksObserver = assignments -> {
            visibleAssignments = assignments != null ? assignments : new ArrayList<>();
            applyPrioritiesAndRender();
            updateBulkActionsVisibility();
        };
        currentTasksLiveData.observe(this, currentTasksObserver);
        updateClearAllVisibility();
    }

    private void applyPrioritiesAndRender() {
        List<AssignmentItem> prioritized = PriorityEngine.calculateAndSort(
                visibleAssignments, currentSchedules, AlgorithmConfig.load(this));

        adapter.updateData(prioritized);
        long pendingCount = 0;
        for (AssignmentItem assignment : prioritized) {
            if (!assignment.isCompleted()) {
                pendingCount++;
            }
        }
        tvTaskSubtitle.setText(getString(R.string.tasks_pending_subtitle, pendingCount));

        if (prioritized.isEmpty()) {
            layoutEmptyTasks.setVisibility(View.VISIBLE);
            recyclerTasks.setVisibility(View.GONE);
            layoutBulkActions.setVisibility(View.GONE);
            if (hasActiveFilters()) {
                tvEmptyTasksTitle.setText(R.string.tasks_no_matches);
                tvEmptyTasksBody.setVisibility(View.GONE);
                findViewById(R.id.btnEmptyAddTask).setVisibility(View.GONE);
            } else {
                tvEmptyTasksTitle.setText(R.string.tasks_empty_title);
                tvEmptyTasksBody.setText(R.string.tasks_empty_body);
                tvEmptyTasksBody.setVisibility(View.VISIBLE);
                findViewById(R.id.btnEmptyAddTask).setVisibility(View.VISIBLE);
            }
        } else {
            layoutEmptyTasks.setVisibility(View.GONE);
            recyclerTasks.setVisibility(View.VISIBLE);
            layoutBulkActions.setVisibility(View.VISIBLE);
        }
    }

    private void showCategoryFilterMenu(View anchor) {
        showFilterMenu(anchor, categoryFilter,
                Arrays.asList(AssignmentItem.CATEGORY_SCHOOL, AssignmentItem.CATEGORY_WORK, AssignmentItem.CATEGORY_OTHER),
                getString(R.string.tasks_filter_any_category),
                selected -> categoryFilter = selected);
    }

    private void showSubjectFilterMenu(View anchor) {
        List<String> subjects = new ArrayList<>(schoolSubjects);
        showFilterMenu(anchor, subjectFilter, subjects,
                getString(R.string.tasks_filter_any_subject),
                selected -> subjectFilter = selected);
    }

    private void showDueFilterMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        addFilterOption(menu, getString(R.string.tasks_filter_any_due), dueFilter == -1);
        addFilterOption(menu, getString(R.string.tasks_filter_overdue), dueFilter == 0);
        addFilterOption(menu, getString(R.string.tasks_filter_today), dueFilter == 1);
        addFilterOption(menu, getString(R.string.tasks_filter_this_week), dueFilter == 2);
        menu.setOnMenuItemClickListener(item -> {
            String selected = String.valueOf(item.getTitle());
            if (selected.equals(getString(R.string.tasks_filter_any_due))) {
                dueFilter = -1;
            } else if (selected.equals(getString(R.string.tasks_filter_overdue))) {
                dueFilter = 0;
            } else if (selected.equals(getString(R.string.tasks_filter_today))) {
                dueFilter = 1;
            } else {
                dueFilter = 2;
            }
            renderFilterChips();
            observeFilteredTasks();
            return true;
        });
        menu.show();
    }

    private void showStatusFilterMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        addFilterOption(menu, getString(R.string.tasks_filter_any_status), statusFilter == -1);
        addFilterOption(menu, getString(R.string.tasks_filter_pending), statusFilter == 0);
        addFilterOption(menu, getString(R.string.tasks_filter_completed), statusFilter == 1);
        menu.setOnMenuItemClickListener(item -> {
            String selected = String.valueOf(item.getTitle());
            if (selected.equals(getString(R.string.tasks_filter_any_status))) {
                statusFilter = -1;
            } else if (selected.equals(getString(R.string.tasks_filter_pending))) {
                statusFilter = 0;
            } else {
                statusFilter = 1;
            }
            renderFilterChips();
            observeFilteredTasks();
            return true;
        });
        menu.show();
    }

    private interface FilterSelection {
        void select(String value);
    }

    private void showFilterMenu(View anchor, String selectedValue, List<String> options,
                                String anyLabel, FilterSelection selection) {
        PopupMenu menu = new PopupMenu(this, anchor);
        addFilterOption(menu, anyLabel, selectedValue.isEmpty());
        for (String option : options) {
            addFilterOption(menu, option, selectedValue.equals(option));
        }
        if (options.isEmpty()) {
            menu.getMenu().add(getString(R.string.tasks_filter_no_subjects)).setEnabled(false);
        }
        menu.setOnMenuItemClickListener(item -> {
            String label = String.valueOf(item.getTitle());
            selection.select(label.equals(anyLabel) ? "" : label);
            renderFilterChips();
            observeFilteredTasks();
            return true;
        });
        menu.show();
    }

    private void addFilterOption(PopupMenu menu, String label, boolean selected) {
        menu.getMenu().add(label).setCheckable(true).setChecked(selected);
    }

    private void renderFilterChips() {
        setFilterChipText(chipTaskCategoryFilter, R.string.tasks_filter_category,
                categoryFilter);
        setFilterChipText(chipTaskSubjectFilter, R.string.tasks_filter_subject,
                subjectFilter);
        setFilterChipText(chipTaskDueFilter, R.string.tasks_filter_due,
                dueFilter == 0 ? getString(R.string.tasks_filter_overdue)
                        : dueFilter == 1 ? getString(R.string.tasks_filter_today)
                        : dueFilter == 2 ? getString(R.string.tasks_filter_this_week) : "");
        setFilterChipText(chipTaskStatusFilter, R.string.tasks_filter_status,
                statusFilter == 0 ? getString(R.string.tasks_filter_pending)
                        : statusFilter == 1 ? getString(R.string.tasks_filter_completed) : "");
        updateClearAllVisibility();
    }

    private void setFilterChipText(TextView chip, int defaultLabelResource, String selectedValue) {
        if (chip == null) return;
        boolean selected = !selectedValue.isEmpty();
        chip.setText(selected ? selectedValue : getString(defaultLabelResource));
        chip.setBackgroundResource(selected
                ? R.drawable.bg_toggle_selected : R.drawable.bg_toggle_unselected);
        chip.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.ws_on_brand : R.color.text_primary));
    }

    private boolean hasActiveFilters() {
        String currentSearch = etTaskSearch == null
                ? searchQuery : etTaskSearch.getText().toString().trim();
        return !currentSearch.isEmpty() || !categoryFilter.isEmpty()
                || !subjectFilter.isEmpty() || dueFilter != -1 || statusFilter != -1;
    }

    private void updateClearAllVisibility() {
        if (btnClearTaskFilters != null) {
            btnClearTaskFilters.setVisibility(hasActiveFilters() ? View.VISIBLE : View.GONE);
        }
    }

    private void clearTaskFilters() {
        categoryFilter = "";
        subjectFilter = "";
        dueFilter = -1;
        statusFilter = -1;
        etTaskSearch.setText("");
        searchQuery = "";
        cancelPendingSearch();
        renderFilterChips();
        observeFilteredTasks();
    }

    private void cancelPendingSearch() {
        if (pendingSearchRunnable != null) {
            searchHandler.removeCallbacks(pendingSearchRunnable);
            pendingSearchRunnable = null;
        }
    }

    private void hideSearchKeyboard() {
        android.view.inputmethod.InputMethodManager inputMethodManager =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(etTaskSearch.getWindowToken(), 0);
        }
    }

    private void showAssignmentDialog(AssignmentItem editing) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_assignment, null);
        builder.setView(dialogView);
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etTitle = dialogView.findViewById(R.id.etAssignmentTitle);
        TextView dialogTitle = dialogView.findViewById(R.id.tvAssignmentDialogTitle);
        TextView dialogSubtitle = dialogView.findViewById(R.id.tvAssignmentDialogSubtitle);
        TextInputLayout subjectLayout = dialogView.findViewById(R.id.tilAssignmentSubject);
        MaterialAutoCompleteTextView etSubject = dialogView.findViewById(R.id.etAssignmentSubject);
        configureSubjectDropdown(subjectLayout, etSubject, schoolSubjects);
        activeSubjectLayout = subjectLayout;
        activeSubjectField = etSubject;

        etSubject.setOnClickListener(v -> showAllSubjects(etSubject));
        subjectLayout.setEndIconOnClickListener(v -> showAllSubjects(etSubject));
        etSubject.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            if (activeSubjectAdapter != null && activeSubjectAdapter.isUseRow(selected)) {
                String customSubject = activeSubjectAdapter.getUseSubject();
                etSubject.setText(customSubject, false);
                etSubject.setSelection(etSubject.getText().length());
            }
        });
        etSubject.setOnDismissListener(() -> {
            if (activeSubjectField != etSubject || activeSubjectAdapter == null) return;
            SubjectAdapter dismissedAdapter = activeSubjectAdapter;
            dismissedAdapter.applyPendingSubjects(etSubject.getText().toString());
            if (dismissedAdapter.hasNoSubjects()) {
                etSubject.setAdapter(null);
                subjectLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
                activeSubjectAdapter = null;
            }
        });

        TextView chipSchool = dialogView.findViewById(R.id.chipCategorySchool);
        TextView chipWork = dialogView.findViewById(R.id.chipCategoryWork);
        TextView chipOther = dialogView.findViewById(R.id.chipCategoryOther);
        LinearLayout subjectSection = dialogView.findViewById(R.id.layoutSubjectSection);

        TextView chipHigh = dialogView.findViewById(R.id.chipImportanceHigh);
        TextView chipMid = dialogView.findViewById(R.id.chipImportanceMid);
        TextView chipLow = dialogView.findViewById(R.id.chipImportanceLow);

        CheckBox cbEnableReminder = dialogView.findViewById(R.id.cbEnableReminder);
        LinearLayout layoutReminderDetails = dialogView.findViewById(R.id.layoutReminderDetails);
        LinearLayout btnPickReminderTime = dialogView.findViewById(R.id.btnPickReminderTime);
        TextView tvSelectedReminderTime = dialogView.findViewById(R.id.tvSelectedReminderTime);
        CheckBox cbRepeatWeekly = dialogView.findViewById(R.id.cbRepeatWeekly);
        LinearLayout layoutRecurringDays = dialogView.findViewById(R.id.layoutRecurringDays);
        CheckBox cbMon = dialogView.findViewById(R.id.cbMon);
        CheckBox cbTue = dialogView.findViewById(R.id.cbTue);
        CheckBox cbWed = dialogView.findViewById(R.id.cbWed);
        CheckBox cbThu = dialogView.findViewById(R.id.cbThu);
        CheckBox cbFri = dialogView.findViewById(R.id.cbFri);
        CheckBox cbSat = dialogView.findViewById(R.id.cbSat);
        CheckBox cbSun = dialogView.findViewById(R.id.cbSun);

        LinearLayout btnPickDate = dialogView.findViewById(R.id.btnPickDate);
        TextView tvSelectedDate = dialogView.findViewById(R.id.tvSelectedDate);

        LinearLayout btnPickTime = dialogView.findViewById(R.id.btnPickTime);
        TextView tvSelectedTime = dialogView.findViewById(R.id.tvSelectedTime);

        Button btnSave = dialogView.findViewById(R.id.btnSaveAssignment);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelAssignment);

        if (editing != null) {
            dialogTitle.setText("Edit Task");
            dialogSubtitle.setText("Update the task details, importance, and reminders.");
            btnSave.setText("Save Changes");
            etTitle.setText(editing.getTitle());
        }

        final String[] selectedCategory = {AssignmentItem.CATEGORY_SCHOOL};
        final String[] selectedImportance = {PriorityEngine.IMPORTANCE_MID};
        final Calendar selectedCal = Calendar.getInstance();
        final Calendar reminderCal = Calendar.getInstance();
        reminderCal.set(Calendar.HOUR_OF_DAY, 17);
        reminderCal.set(Calendar.MINUTE, 0);

        if (editing != null && editing.getDueTimestamp() > 0) {
            selectedCal.setTimeInMillis(editing.getDueTimestamp());
        } else {
            selectedCal.add(Calendar.DAY_OF_YEAR, 1);
            selectedCal.set(Calendar.HOUR_OF_DAY, 23);
            selectedCal.set(Calendar.MINUTE, 59);
        }
        selectedCal.set(Calendar.SECOND, 0);
        selectedCal.set(Calendar.MILLISECOND, 0);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat displayDateFormat = new SimpleDateFormat("EEE, MMM dd", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat(
                TimeFormatPreference.pattern(this), Locale.getDefault());

        tvSelectedDate.setText(displayDateFormat.format(selectedCal.getTime()));
        tvSelectedTime.setText(timeFormat.format(selectedCal.getTime()));
        tvSelectedReminderTime.setText(timeFormat.format(reminderCal.getTime()));

        TextView[] categoryChips = {chipSchool, chipWork, chipOther};
        String[] categoryNames = {AssignmentItem.CATEGORY_SCHOOL, AssignmentItem.CATEGORY_WORK, AssignmentItem.CATEGORY_OTHER};

        View.OnClickListener categoryClick = v -> {
            for (int i = 0; i < categoryChips.length; i++) {
                if (categoryChips[i] == v) {
                    categoryChips[i].setBackgroundResource(R.drawable.bg_toggle_selected);
                    categoryChips[i].setTextColor(ContextCompat.getColor(this, R.color.ws_on_brand));
                    selectedCategory[0] = categoryNames[i];
                } else {
                    categoryChips[i].setBackgroundResource(R.drawable.bg_toggle_unselected);
                    categoryChips[i].setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                }
            }
            subjectSection.setVisibility(selectedCategory[0].equals(AssignmentItem.CATEGORY_SCHOOL) ? View.VISIBLE : View.GONE);
        };

        for (TextView chip : categoryChips) {
            chip.setOnClickListener(categoryClick);
        }

        TextView[] importanceChips = {chipHigh, chipMid, chipLow};
        String[] importanceNames = {PriorityEngine.IMPORTANCE_HIGH, PriorityEngine.IMPORTANCE_MID, PriorityEngine.IMPORTANCE_LOW};

        View.OnClickListener importanceClick = v -> {
            for (int i = 0; i < importanceChips.length; i++) {
                if (importanceChips[i] == v) {
                    importanceChips[i].setBackgroundResource(R.drawable.bg_toggle_selected);
                    importanceChips[i].setTextColor(ContextCompat.getColor(this, R.color.ws_on_brand));
                    selectedImportance[0] = importanceNames[i];
                } else {
                    importanceChips[i].setBackgroundResource(R.drawable.bg_toggle_unselected);
                    importanceChips[i].setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                }
            }
        };

        for (TextView chip : importanceChips) {
            chip.setOnClickListener(importanceClick);
        }

        if (editing != null) {
            String cat = editing.getCategory();
            for (int i = 0; i < categoryNames.length; i++) {
                if (categoryNames[i].equalsIgnoreCase(cat)) {
                    categoryClick.onClick(categoryChips[i]);
                    break;
                }
            }
            String imp = editing.getImportance();
            for (int i = 0; i < importanceNames.length; i++) {
                if (importanceNames[i].equalsIgnoreCase(imp)) {
                    importanceClick.onClick(importanceChips[i]);
                    break;
                }
            }
            if (editing.getSubject() != null && !editing.getSubject().isEmpty()) {
                etSubject.setText(editing.getSubject(), false);
            }

            cbEnableReminder.setChecked(editing.isHasReminder());
            layoutReminderDetails.setVisibility(editing.isHasReminder() ? View.VISIBLE : View.GONE);
            if (editing.getReminderTimestamp() > 0) {
                reminderCal.setTimeInMillis(editing.getReminderTimestamp());
                tvSelectedReminderTime.setText(timeFormat.format(reminderCal.getTime()));
            }
            cbRepeatWeekly.setChecked(editing.isRecurring());
            layoutRecurringDays.setVisibility(editing.isRecurring() ? View.VISIBLE : View.GONE);
            String days = editing.getRecurringDays();
            if (days != null) {
                cbMon.setChecked(days.contains("MON"));
                cbTue.setChecked(days.contains("TUE"));
                cbWed.setChecked(days.contains("WED"));
                cbThu.setChecked(days.contains("THU"));
                cbFri.setChecked(days.contains("FRI"));
                cbSat.setChecked(days.contains("SAT"));
                cbSun.setChecked(days.contains("SUN"));
            }
        }

        cbEnableReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutReminderDetails.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        cbRepeatWeekly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutRecurringDays.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        btnPickReminderTime.setOnClickListener(v -> {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTitleText("Select reminder time")
                    .setTimeFormat(TimeFormatPreference.is24Hour(this)
                            ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                    .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
                    .setHour(reminderCal.get(Calendar.HOUR_OF_DAY))
                    .setMinute(reminderCal.get(Calendar.MINUTE))
                    .build();
            picker.addOnPositiveButtonClickListener(view -> {
                reminderCal.set(Calendar.HOUR_OF_DAY, picker.getHour());
                reminderCal.set(Calendar.MINUTE, picker.getMinute());
                reminderCal.set(Calendar.SECOND, 0);
                reminderCal.set(Calendar.MILLISECOND, 0);
                tvSelectedReminderTime.setText(timeFormat.format(reminderCal.getTime()));
            });
            picker.show(getSupportFragmentManager(), "task_reminder_time_picker");
        });

        btnPickDate.setOnClickListener(v -> {
            Calendar today = Calendar.getInstance();
            long firstAllowedDate = utcDateMillis(today);
            Calendar initialDate = (Calendar) selectedCal.clone();
            if (isBeforeToday(initialDate, today)) initialDate = today;
            CalendarConstraints constraints = new CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.from(firstAllowedDate))
                    .build();
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select due date")
                    .setCalendarConstraints(constraints)
                    .setSelection(utcDateMillis(initialDate))
                    .build();
            picker.addOnPositiveButtonClickListener(selection -> {
                Calendar utcSelection = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                utcSelection.setTimeInMillis(selection);
                selectedCal.set(Calendar.YEAR, utcSelection.get(Calendar.YEAR));
                selectedCal.set(Calendar.MONTH, utcSelection.get(Calendar.MONTH));
                selectedCal.set(Calendar.DAY_OF_MONTH, utcSelection.get(Calendar.DAY_OF_MONTH));
                tvSelectedDate.setText(displayDateFormat.format(selectedCal.getTime()));
                tvSelectedTime.setText(timeFormat.format(selectedCal.getTime()));
            });
            picker.show(getSupportFragmentManager(), "task_due_date_picker");
        });

        btnPickTime.setOnClickListener(v -> {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTitleText("Select due time")
                    .setTimeFormat(TimeFormatPreference.is24Hour(this)
                            ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                    .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
                    .setHour(selectedCal.get(Calendar.HOUR_OF_DAY))
                    .setMinute(selectedCal.get(Calendar.MINUTE))
                    .build();
            picker.addOnPositiveButtonClickListener(view -> {
                selectedCal.set(Calendar.HOUR_OF_DAY, picker.getHour());
                selectedCal.set(Calendar.MINUTE, picker.getMinute());
                selectedCal.set(Calendar.SECOND, 0);
                selectedCal.set(Calendar.MILLISECOND, 0);
                tvSelectedTime.setText(timeFormat.format(selectedCal.getTime()));
            });
            picker.show(getSupportFragmentManager(), "task_due_time_picker");
        });

        Runnable discardAction = () -> {
            new AlertDialog.Builder(this)
                    .setTitle("Discard changes?")
                    .setMessage("Your changes will be discarded and you will return to the home screen.")
                    .setNegativeButton("Keep editing", null)
                    .setPositiveButton("Discard", (d, w) -> {
                        dialog.dismiss();
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    })
                    .show();
        };

        btnCancel.setOnClickListener(v -> discardAction.run());
        dialog.setOnDismissListener(dialogInterface -> {
            if (activeSubjectField == etSubject) {
                activeSubjectLayout = null;
                activeSubjectField = null;
                activeSubjectAdapter = null;
            }
        });

        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String category = selectedCategory[0];
            String importance = selectedImportance[0];
            String subject = category.equals(AssignmentItem.CATEGORY_SCHOOL) ? etSubject.getText().toString().trim() : "";

            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter a task title.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (category.equals(AssignmentItem.CATEGORY_SCHOOL) && subject.isEmpty()) {
                subject = "General Academic";
            }

            String dateStr = dateFormat.format(selectedCal.getTime());
            String timeStr = timeFormat.format(selectedCal.getTime());
            long timestamp = selectedCal.getTimeInMillis();

            boolean hasReminder = cbEnableReminder.isChecked();
            boolean isRecurring = hasReminder && cbRepeatWeekly.isChecked();
            String reminderTimeStr = hasReminder ? timeFormat.format(reminderCal.getTime()) : "";
            long reminderTs = hasReminder ? reminderCal.getTimeInMillis() : 0L;

            StringBuilder recurringDaysSb = new StringBuilder();
            if (isRecurring) {
                if (cbMon.isChecked()) recurringDaysSb.append("MON,");
                if (cbTue.isChecked()) recurringDaysSb.append("TUE,");
                if (cbWed.isChecked()) recurringDaysSb.append("WED,");
                if (cbThu.isChecked()) recurringDaysSb.append("THU,");
                if (cbFri.isChecked()) recurringDaysSb.append("FRI,");
                if (cbSat.isChecked()) recurringDaysSb.append("SAT,");
                if (cbSun.isChecked()) recurringDaysSb.append("SUN,");
            }
            String recurringDays = recurringDaysSb.toString();
            if (recurringDays.endsWith(",")) {
                recurringDays = recurringDays.substring(0, recurringDays.length() - 1);
            }

            AssignmentItem updatedItem = editing != null ? editing : new AssignmentItem(
                    title, subject, category, dateStr, timeStr, timestamp,
                    false, category.equals(AssignmentItem.CATEGORY_WORK) ? AssignmentItem.CATEGORY_WORK : AssignmentItem.CATEGORY_SCHOOL);
            updatedItem.setTitle(title);
            updatedItem.setSubject(subject);
            updatedItem.setCategory(category);
            updatedItem.setImportance(importance);
            updatedItem.setDueDate(dateStr);
            updatedItem.setDueTime(timeStr);
            updatedItem.setDueTimestamp(timestamp);
            updatedItem.setTaskCategory(category);
            updatedItem.setHasReminder(hasReminder);
            updatedItem.setReminderTime(reminderTimeStr);
            updatedItem.setReminderTimestamp(reminderTs);
            updatedItem.setRecurring(isRecurring);
            updatedItem.setRecurringDays(recurringDays);

            new Thread(() -> {
                AppDatabase database = AppDatabase.getInstance(getApplicationContext());
                if (editing == null) {
                    LocalDataRepository.saveAssignment(database, updatedItem);
                } else {
                    LocalDataRepository.updateAssignment(database, updatedItem);
                }
                ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();

                runOnUiThread(() -> {
                    String message = editing != null ? "Task updated" : "Task added";
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            }).start();
        });

        dialog.show();
    }

    private long utcDateMillis(Calendar localDate) {
        Calendar utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utcDate.clear();
        utcDate.set(localDate.get(Calendar.YEAR), localDate.get(Calendar.MONTH),
                localDate.get(Calendar.DAY_OF_MONTH));
        return utcDate.getTimeInMillis();
    }

    private boolean isBeforeToday(Calendar date, Calendar today) {
        Calendar dateOnly = (Calendar) date.clone();
        Calendar todayOnly = (Calendar) today.clone();
        dateOnly.set(Calendar.HOUR_OF_DAY, 0);
        dateOnly.set(Calendar.MINUTE, 0);
        dateOnly.set(Calendar.SECOND, 0);
        dateOnly.set(Calendar.MILLISECOND, 0);
        todayOnly.set(Calendar.HOUR_OF_DAY, 0);
        todayOnly.set(Calendar.MINUTE, 0);
        todayOnly.set(Calendar.SECOND, 0);
        todayOnly.set(Calendar.MILLISECOND, 0);
        return dateOnly.before(todayOnly);
    }

    private void configureSubjectDropdown(TextInputLayout layout,
                                          MaterialAutoCompleteTextView field,
                                          List<String> subjects) {
        String currentText = field.getText().toString();
        if (subjects == null || subjects.isEmpty()) {
            if (activeSubjectAdapter != null && field.isPopupShowing()) {
                activeSubjectAdapter.updateSubjects(new ArrayList<>(), currentText, true);
                return;
            }
            activeSubjectAdapter = null;
            field.setAdapter(null);
            layout.setEndIconMode(TextInputLayout.END_ICON_NONE);
            return;
        }

        layout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        if (activeSubjectAdapter == null || field.getAdapter() != activeSubjectAdapter) {
            activeSubjectAdapter = new SubjectAdapter(this, subjects);
            field.setAdapter(activeSubjectAdapter);
            field.setThreshold(0);
        } else {
            activeSubjectAdapter.updateSubjects(subjects, currentText, field.isPopupShowing());
        }
    }

    private void showAllSubjects(MaterialAutoCompleteTextView field) {
        if (activeSubjectAdapter == null) return;
        activeSubjectAdapter.showAllSubjects();
        field.showDropDown();
    }

    private static class SubjectAdapter extends ArrayAdapter<String> implements Filterable {
        private final List<String> allSubjects = new ArrayList<>();
        private List<String> pendingSubjects;
        private String useSubject;

        SubjectAdapter(TasksActivity context, List<String> subjects) {
            super(context, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(subjects));
            allSubjects.addAll(subjects);
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    String query = constraint == null ? "" : constraint.toString().trim();
                    String normalizedQuery = query.toLowerCase(Locale.ROOT);
                    List<String> matches = new ArrayList<>();
                    for (String subject : allSubjects) {
                        if (subject.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                            matches.add(subject);
                        }
                    }

                    String fallback = null;
                    if (!query.isEmpty() && matches.isEmpty()) {
                        fallback = query;
                        matches.add("Use '" + query + "'");
                    }

                    FilterResults results = new FilterResults();
                    results.values = matches;
                    results.count = matches.size();
                    useSubject = fallback;
                    return results;
                }

                @Override
                @SuppressWarnings("unchecked")
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    clear();
                    if (results.values != null) {
                        addAll((List<String>) results.values);
                    }
                    if (results.count > 0) {
                        notifyDataSetChanged();
                    } else {
                        notifyDataSetInvalidated();
                    }
                }
            };
        }

        void updateSubjects(List<String> subjects, String query, boolean deferWhileShowing) {
            if (pendingSubjects != null && pendingSubjects.equals(subjects)) return;
            if (allSubjects.equals(subjects)) {
                pendingSubjects = null;
                return;
            }
            if (deferWhileShowing) {
                pendingSubjects = new ArrayList<>(subjects);
                return;
            }
            replaceSubjects(subjects, query);
        }

        void applyPendingSubjects(String query) {
            if (pendingSubjects == null) return;
            List<String> subjects = pendingSubjects;
            pendingSubjects = null;
            replaceSubjects(subjects, query);
        }

        private void replaceSubjects(List<String> subjects, String query) {
            allSubjects.clear();
            allSubjects.addAll(subjects);
            String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String subject : allSubjects) {
                if (subject.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                    matches.add(subject);
                }
            }
            useSubject = !normalizedQuery.isEmpty() && matches.isEmpty() ? query.trim() : null;
            if (useSubject != null) matches.add("Use '" + useSubject + "'");
            clear();
            addAll(matches);
            notifyDataSetChanged();
        }

        void showAllSubjects() {
            useSubject = null;
            if (getCount() == allSubjects.size()) {
                boolean allRowsVisible = true;
                for (int i = 0; i < allSubjects.size(); i++) {
                    if (!allSubjects.get(i).equals(getItem(i))) {
                        allRowsVisible = false;
                        break;
                    }
                }
                if (allRowsVisible) return;
            }
            clear();
            addAll(allSubjects);
            notifyDataSetChanged();
        }

        boolean hasNoSubjects() {
            return allSubjects.isEmpty();
        }

        boolean isUseRow(String value) {
            return useSubject != null && value.equals("Use '" + useSubject + "'");
        }

        String getUseSubject() {
            return useSubject;
        }
    }
}
