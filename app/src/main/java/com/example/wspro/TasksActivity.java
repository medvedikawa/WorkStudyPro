package com.example.wspro;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.widget.TextViewCompat;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
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
    private TextView btnClearTaskFilters;
    private EditText etTaskSearch;
    private View taskSearchRow;

    // Redesigned Top Bars
    private View normalBar;
    private View selBar;
    private TextView tvSelCount;
    private ImageButton btnCancelSelection;
    private ImageButton btnSelectAllTasks;
    private ImageButton btnDeleteSelection;

    // Tabs
    private TextView tabActive;
    private TextView tabDone;

    // Filter Chips
    private LinearLayout chipTaskCategoryFilter;
    private TextView tvFilterCategoryText;
    private ImageView ivFilterCategoryChevron;

    private LinearLayout chipTaskSubjectFilter;
    private TextView tvFilterSubjectText;
    private ImageView ivFilterSubjectChevron;

    private LinearLayout chipTaskDueFilter;
    private TextView tvFilterDueText;
    private ImageView ivFilterDueChevron;

    private LinearLayout chipTaskPriorityFilter;
    private TextView tvFilterPriorityText;
    private ImageView ivFilterPriorityChevron;

    // Toast Bar
    private LinearLayout layoutTaskToast;
    private TextView tvToastMessage;
    private TextView btnToastUndo;
    private final Handler toastHandler = new Handler(Looper.getMainLooper());
    private Runnable toastDismissRunnable;
    private AssignmentItem lastToggledItem;

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
    private String priorityFilter = ""; // High, Mid, Low
    private String currentTab = "active"; // "active" or "done"
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
        View btnAddNewTaskHeader = findViewById(R.id.btnAddNewTaskHeader);
        Button btnEmptyAddTask = findViewById(R.id.btnEmptyAddTask);

        normalBar = findViewById(R.id.normalBar);
        selBar = findViewById(R.id.selBar);
        tvSelCount = findViewById(R.id.tvSelCount);
        btnCancelSelection = findViewById(R.id.btnCancelSelection);
        btnSelectAllTasks = findViewById(R.id.btnSelectAllTasks);
        btnDeleteSelection = findViewById(R.id.btnDeleteSelection);

        tabActive = findViewById(R.id.tabActive);
        tabDone = findViewById(R.id.tabDone);

        layoutTaskToast = findViewById(R.id.layoutTaskToast);
        tvToastMessage = findViewById(R.id.tvToastMessage);
        btnToastUndo = findViewById(R.id.btnToastUndo);

        btnAddNewTaskHeader.setOnClickListener(v -> showAssignmentDialog(null));
        btnEmptyAddTask.setOnClickListener(v -> showAssignmentDialog(null));

        setupTabs();
        setupTaskFilters();
        setupBulkActions();
        setupToastUndo();

        recyclerTasks.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AssignmentAdapter(this, new ArrayList<>(), new AssignmentAdapter.OnAssignmentActionListener() {
            @Override
            public void onToggleComplete(AssignmentItem item, boolean isCompleted) {
                item.setCompleted(isCompleted);
                lastToggledItem = item;
                showUndoToast(isCompleted ? "Marked as done" : "Moved back to active", true);

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
                        .setMessage("\"" + item.getTitle() + "\" will be permanently removed.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (dialog, which) -> new Thread(() -> {
                            LocalDataRepository.deleteAssignment(
                                    AppDatabase.getInstance(getApplicationContext()), item);
                            ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();
                            runOnUiThread(() -> showUndoToast("Task deleted", false));
                        }).start())
                        .show();
            }

            @Override
            public void onSelectionChanged(AssignmentItem item, boolean isSelected) {
                updateSelectionState();
            }

            @Override
            public void onStartSelection(AssignmentItem item) {
                adapter.setSelecting(true);
                updateSelectionState();
            }
        });
        recyclerTasks.setAdapter(adapter);

        setupBottomNav();
        setupReactiveObservers();
    }

    private void setupTabs() {
        tabActive.setOnClickListener(v -> {
            if ("active".equals(currentTab)) return;
            currentTab = "active";
            tabActive.setBackgroundResource(R.drawable.bg_task_tab_on);
            tabActive.setTextColor(ContextCompat.getColor(this, R.color.task_violet));
            tabDone.setBackgroundResource(android.R.color.transparent);
            tabDone.setTextColor(ContextCompat.getColor(this, R.color.task_muted));
            observeFilteredTasks();
        });

        tabDone.setOnClickListener(v -> {
            if ("done".equals(currentTab)) return;
            currentTab = "done";
            tabDone.setBackgroundResource(R.drawable.bg_task_tab_on);
            tabDone.setTextColor(ContextCompat.getColor(this, R.color.task_violet));
            tabActive.setBackgroundResource(android.R.color.transparent);
            tabActive.setTextColor(ContextCompat.getColor(this, R.color.task_muted));
            observeFilteredTasks();
        });
    }

    private void setupToastUndo() {
        btnToastUndo.setOnClickListener(v -> {
            if (lastToggledItem != null) {
                final AssignmentItem itemToUndo = lastToggledItem;
                lastToggledItem = null;
                itemToUndo.setCompleted(!itemToUndo.isCompleted());
                new Thread(() -> {
                    LocalDataRepository.updateAssignment(
                            AppDatabase.getInstance(getApplicationContext()), itemToUndo);
                    ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();
                }).start();
            }
            hideToast();
        });
    }

    private void showUndoToast(String message, boolean canUndo) {
        tvToastMessage.setText(message);
        btnToastUndo.setVisibility(canUndo ? View.VISIBLE : View.GONE);
        layoutTaskToast.setVisibility(View.VISIBLE);
        if (toastDismissRunnable != null) toastHandler.removeCallbacks(toastDismissRunnable);
        toastDismissRunnable = this::hideToast;
        toastHandler.postDelayed(toastDismissRunnable, 3500);
    }

    private void hideToast() {
        layoutTaskToast.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPrioritiesAndRender();
    }

    @Override
    protected void onDestroy() {
        cancelPendingSearch();
        if (toastDismissRunnable != null) toastHandler.removeCallbacks(toastDismissRunnable);
        super.onDestroy();
    }

    private void setupBottomNav() {
        BottomNavHelper.setup(this, BottomNavHelper.Tab.TASKS, scanButtonHelper);
    }

    private void setupBulkActions() {
        btnCancelSelection.setOnClickListener(v -> exitSelectionMode());

        btnSelectAllTasks.setOnClickListener(v -> {
            boolean allSelected = true;
            for (AssignmentItem item : visibleAssignments) {
                if (!item.isSelected()) {
                    allSelected = false;
                    break;
                }
            }
            boolean target = !allSelected;
            for (AssignmentItem item : visibleAssignments) {
                item.setSelected(target);
            }
            adapter.notifyDataSetChanged();
            updateSelectionState();
        });

        btnDeleteSelection.setOnClickListener(v -> {
            List<AssignmentItem> selectedItems = new ArrayList<>();
            for (AssignmentItem item : visibleAssignments) {
                if (item.isSelected()) selectedItems.add(item);
            }
            if (selectedItems.isEmpty()) return;

            new AlertDialog.Builder(this)
                    .setTitle("Delete selected tasks?")
                    .setMessage(selectedItems.size() + " task" + (selectedItems.size() > 1 ? "s" : "")
                            + " will be permanently removed.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (dialog, which) -> new Thread(() -> {
                        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                        for (AssignmentItem item : selectedItems) {
                            LocalDataRepository.deleteAssignment(db, item);
                        }
                        ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();
                        runOnUiThread(() -> {
                            exitSelectionMode();
                            showUndoToast("Deleted", false);
                        });
                    }).start())
                    .show();
        });
    }

    private void exitSelectionMode() {
        for (AssignmentItem item : visibleAssignments) {
            item.setSelected(false);
        }
        adapter.setSelecting(false);
        normalBar.setVisibility(View.VISIBLE);
        selBar.setVisibility(View.GONE);
        findViewById(R.id.layoutTaskTabs).setVisibility(View.VISIBLE);
        findViewById(R.id.scrollTaskFilters).setVisibility(View.VISIBLE);
    }

    private void updateSelectionState() {
        int selectedCount = 0;
        for (AssignmentItem item : visibleAssignments) {
            if (item.isSelected()) selectedCount++;
        }

        if (selectedCount == 0 && !adapter.isSelecting()) {
            normalBar.setVisibility(View.VISIBLE);
            selBar.setVisibility(View.GONE);
            findViewById(R.id.layoutTaskTabs).setVisibility(View.VISIBLE);
            findViewById(R.id.scrollTaskFilters).setVisibility(View.VISIBLE);
        } else {
            normalBar.setVisibility(View.GONE);
            selBar.setVisibility(View.VISIBLE);
            findViewById(R.id.layoutTaskTabs).setVisibility(View.GONE);
            findViewById(R.id.scrollTaskFilters).setVisibility(View.GONE);
            tvSelCount.setText(selectedCount + " selected");
        }
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
        tvFilterCategoryText = findViewById(R.id.tvFilterCategoryText);
        ivFilterCategoryChevron = findViewById(R.id.ivFilterCategoryChevron);

        chipTaskSubjectFilter = findViewById(R.id.chipTaskSubjectFilter);
        tvFilterSubjectText = findViewById(R.id.tvFilterSubjectText);
        ivFilterSubjectChevron = findViewById(R.id.ivFilterSubjectChevron);

        chipTaskDueFilter = findViewById(R.id.chipTaskDueFilter);
        tvFilterDueText = findViewById(R.id.tvFilterDueText);
        ivFilterDueChevron = findViewById(R.id.ivFilterDueChevron);

        chipTaskPriorityFilter = findViewById(R.id.chipTaskPriorityFilter);
        tvFilterPriorityText = findViewById(R.id.tvFilterPriorityText);
        ivFilterPriorityChevron = findViewById(R.id.ivFilterPriorityChevron);

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

        chipTaskCategoryFilter.setOnClickListener(this::showCategoryFilterMenu);
        chipTaskSubjectFilter.setOnClickListener(this::showSubjectFilterMenu);
        chipTaskDueFilter.setOnClickListener(this::showDueFilterMenu);
        chipTaskPriorityFilter.setOnClickListener(this::showPriorityFilterMenu);
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

        int status = "done".equals(currentTab) ? 1 : 0;

        currentTasksLiveData = db.assignmentDao().searchAssignmentsLiveData(
                searchQuery,
                categoryFilter,
                subjectFilter,
                status,
                dueFilter,
                nowTimestamp,
                dayStartTimestamp,
                tomorrowStartTimestamp,
                weekStart.getTimeInMillis(),
                nextWeekStart.getTimeInMillis());

        currentTasksObserver = assignments -> {
            List<AssignmentItem> filtered = new ArrayList<>();
            if (assignments != null) {
                for (AssignmentItem item : assignments) {
                    if (!priorityFilter.isEmpty()) {
                        String imp = item.getImportance();
                        if (imp == null || !imp.equalsIgnoreCase(priorityFilter)) {
                            continue;
                        }
                    }
                    filtered.add(item);
                }
            }
            visibleAssignments = filtered;
            applyPrioritiesAndRender();
            updateSelectionState();
        };
        currentTasksLiveData.observe(this, currentTasksObserver);
        updateClearAllVisibility();
    }

    private void applyPrioritiesAndRender() {
        List<AssignmentItem> prioritized = PriorityEngine.calculateAndSort(
                visibleAssignments, currentSchedules, AlgorithmConfig.load(this));

        adapter.updateData(prioritized);

        long activeCount = 0;
        for (AssignmentItem assignment : prioritized) {
            if (!assignment.isCompleted()) {
                activeCount++;
            }
        }
        tvTaskSubtitle.setText(activeCount + " active · sorted by urgency");

        if (prioritized.isEmpty()) {
            layoutEmptyTasks.setVisibility(View.VISIBLE);
            recyclerTasks.setVisibility(View.GONE);
            if (hasActiveFilters()) {
                tvEmptyTasksTitle.setText(R.string.tasks_no_matches);
                findViewById(R.id.btnEmptyAddTask).setVisibility(View.GONE);
            } else {
                tvEmptyTasksTitle.setText("done".equals(currentTab)
                        ? "Nothing finished yet."
                        : "All clear. Enjoy the free time!");
                findViewById(R.id.btnEmptyAddTask).setVisibility(
                        "done".equals(currentTab) ? View.GONE : View.VISIBLE);
            }
        } else {
            layoutEmptyTasks.setVisibility(View.GONE);
            recyclerTasks.setVisibility(View.VISIBLE);
        }
    }

    private void showCategoryFilterMenu(View anchor) {
        showFilterMenu(anchor, categoryFilter,
                Arrays.asList(AssignmentItem.CATEGORY_SCHOOL, AssignmentItem.CATEGORY_WORK, AssignmentItem.CATEGORY_OTHER),
                "Category",
                selected -> categoryFilter = selected);
    }

    private void showSubjectFilterMenu(View anchor) {
        List<String> subjects = new ArrayList<>(schoolSubjects);
        showFilterMenu(anchor, subjectFilter, subjects,
                "Subject",
                selected -> subjectFilter = selected);
    }

    private void showDueFilterMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        addFilterOption(menu, "Due", dueFilter == -1);
        addFilterOption(menu, "Overdue", dueFilter == 0);
        addFilterOption(menu, "Today", dueFilter == 1);
        addFilterOption(menu, "This week", dueFilter == 2);
        menu.setOnMenuItemClickListener(item -> {
            String selected = String.valueOf(item.getTitle());
            if (selected.equals("Due")) {
                dueFilter = -1;
            } else if (selected.equals("Overdue")) {
                dueFilter = 0;
            } else if (selected.equals("Today")) {
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

    private void showPriorityFilterMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        addFilterOption(menu, "Priority", priorityFilter.isEmpty());
        addFilterOption(menu, "Critical", "Critical".equalsIgnoreCase(priorityFilter));
        addFilterOption(menu, "High", "High".equalsIgnoreCase(priorityFilter));
        addFilterOption(menu, "Mid", "Mid".equalsIgnoreCase(priorityFilter));
        addFilterOption(menu, "Low", "Low".equalsIgnoreCase(priorityFilter));
        menu.setOnMenuItemClickListener(item -> {
            String selected = String.valueOf(item.getTitle());
            if (selected.equals("Priority")) {
                priorityFilter = "";
            } else {
                priorityFilter = selected;
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
        if (options.isEmpty() && "Subject".equals(anyLabel)) {
            menu.getMenu().add("No schedule subjects").setEnabled(false);
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
        setFilterChipState(chipTaskCategoryFilter, tvFilterCategoryText, ivFilterCategoryChevron,
                "Category", categoryFilter);
        setFilterChipState(chipTaskSubjectFilter, tvFilterSubjectText, ivFilterSubjectChevron,
                "Subject", subjectFilter);

        String dueLabel = dueFilter == 0 ? "Overdue"
                : dueFilter == 1 ? "Today"
                : dueFilter == 2 ? "This week" : "";
        setFilterChipState(chipTaskDueFilter, tvFilterDueText, ivFilterDueChevron,
                "Due", dueLabel);

        setFilterChipState(chipTaskPriorityFilter, tvFilterPriorityText, ivFilterPriorityChevron,
                "Priority", priorityFilter);

        updateClearAllVisibility();
    }

    private void setFilterChipState(LinearLayout container, TextView text, ImageView chevron,
                                    String defaultLabel, String selectedValue) {
        if (container == null || text == null) return;
        boolean selected = selectedValue != null && !selectedValue.isEmpty();
        text.setText(selected ? selectedValue : defaultLabel);

        container.setBackgroundResource(selected
                ? R.drawable.bg_task_filter_chip_on : R.drawable.bg_task_filter_chip);

        int textColor = ContextCompat.getColor(this,
                selected ? R.color.ws_surface : R.color.task_ink);
        text.setTextColor(textColor);

        if (chevron != null) {
            chevron.setColorFilter(textColor);
        }
    }

    private boolean hasActiveFilters() {
        String currentSearch = etTaskSearch == null
                ? searchQuery : etTaskSearch.getText().toString().trim();
        return !currentSearch.isEmpty() || !categoryFilter.isEmpty()
                || !subjectFilter.isEmpty() || dueFilter != -1 || !priorityFilter.isEmpty();
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
        priorityFilter = "";
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
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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

        Runnable updateCategoryChips = () -> {
            for (int i = 0; i < categoryChips.length; i++) {
                boolean selected = categoryNames[i].equalsIgnoreCase(selectedCategory[0]);
                categoryChips[i].setSelected(selected);
                categoryChips[i].setBackgroundResource(selected ? R.drawable.bg_toggle_selected : R.drawable.bg_toggle_unselected);
                categoryChips[i].setTextColor(ContextCompat.getColor(this, selected ? R.color.ws_on_brand : R.color.text_primary));
            }
            subjectSection.setVisibility(selectedCategory[0].equalsIgnoreCase(AssignmentItem.CATEGORY_SCHOOL) ? View.VISIBLE : View.GONE);
        };

        for (int i = 0; i < categoryChips.length; i++) {
            final int index = i;
            categoryChips[i].setOnClickListener(v -> {
                selectedCategory[0] = categoryNames[index];
                updateCategoryChips.run();
            });
        }
        updateCategoryChips.run();

        TextView[] importanceChips = {chipHigh, chipMid, chipLow};
        String[] importanceNames = {PriorityEngine.IMPORTANCE_HIGH, PriorityEngine.IMPORTANCE_MID, PriorityEngine.IMPORTANCE_LOW};

        Runnable updateImportanceChips = () -> {
            for (int i = 0; i < importanceChips.length; i++) {
                boolean selected = importanceNames[i].equalsIgnoreCase(selectedImportance[0]);
                importanceChips[i].setSelected(selected);
                importanceChips[i].setBackgroundResource(selected ? R.drawable.bg_toggle_selected : R.drawable.bg_toggle_unselected);
                importanceChips[i].setTextColor(ContextCompat.getColor(this, selected ? R.color.ws_on_brand : R.color.text_primary));
            }
        };

        for (int i = 0; i < importanceChips.length; i++) {
            final int index = i;
            importanceChips[i].setOnClickListener(v -> {
                selectedImportance[0] = importanceNames[index];
                updateImportanceChips.run();
            });
        }
        updateImportanceChips.run();

        if (editing != null) {
            String cat = editing.getCategory();
            if (cat != null && !cat.isEmpty()) {
                selectedCategory[0] = cat;
                updateCategoryChips.run();
            }
            String imp = editing.getImportance();
            if (imp != null && !imp.isEmpty()) {
                selectedImportance[0] = imp;
                updateImportanceChips.run();
            }
            if (editing.getSubject() != null && !editing.getSubject().isEmpty()) {
                etSubject.setText(editing.getSubject(), false);
            }

            cbEnableReminder.setChecked(editing.isHasReminder());
            layoutReminderDetails.setVisibility(editing.isHasReminder() ? View.VISIBLE : View.GONE);
            if (editing.getReminderTime() != null && !editing.getReminderTime().isEmpty()) {
                tvSelectedReminderTime.setText(TimeFormatPreference.formatTime(this, editing.getReminderTime()));
            }

            cbRepeatWeekly.setChecked(editing.isRecurring());
            layoutRecurringDays.setVisibility(editing.isRecurring() ? View.VISIBLE : View.GONE);
            if (editing.getRecurringDays() != null) {
                String days = editing.getRecurringDays();
                cbMon.setChecked(days.contains("MON"));
                cbTue.setChecked(days.contains("TUE"));
                cbWed.setChecked(days.contains("WED"));
                cbThu.setChecked(days.contains("THU"));
                cbFri.setChecked(days.contains("FRI"));
                cbSat.setChecked(days.contains("SAT"));
                cbSun.setChecked(days.contains("SUN"));
            }
        }

        cbEnableReminder.setOnCheckedChangeListener((bv, isChecked) ->
                layoutReminderDetails.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        cbRepeatWeekly.setOnCheckedChangeListener((bv, isChecked) ->
                layoutRecurringDays.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        btnPickDate.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Due Date")
                    .setSelection(selectedCal.getTimeInMillis())
                    .setCalendarConstraints(new CalendarConstraints.Builder()
                            .setValidator(DateValidatorPointForward.now())
                            .build())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                utcCal.setTimeInMillis(selection);
                selectedCal.set(Calendar.YEAR, utcCal.get(Calendar.YEAR));
                selectedCal.set(Calendar.MONTH, utcCal.get(Calendar.MONTH));
                selectedCal.set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH));
                tvSelectedDate.setText(displayDateFormat.format(selectedCal.getTime()));
            });

            datePicker.show(getSupportFragmentManager(), "TASK_DATE_PICKER");
        });

        btnPickTime.setOnClickListener(v -> {
            int timeFormatMode = TimeFormatPreference.is24Hour(this)
                    ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H;
            MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(timeFormatMode)
                    .setHour(selectedCal.get(Calendar.HOUR_OF_DAY))
                    .setMinute(selectedCal.get(Calendar.MINUTE))
                    .setTitleText("Select Due Time")
                    .build();

            timePicker.addOnPositiveButtonClickListener(dialogView1 -> {
                selectedCal.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                selectedCal.set(Calendar.MINUTE, timePicker.getMinute());
                tvSelectedTime.setText(timeFormat.format(selectedCal.getTime()));
            });

            timePicker.show(getSupportFragmentManager(), "TASK_TIME_PICKER");
        });

        btnPickReminderTime.setOnClickListener(v -> {
            int timeFormatMode = TimeFormatPreference.is24Hour(this)
                    ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H;
            MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(timeFormatMode)
                    .setHour(reminderCal.get(Calendar.HOUR_OF_DAY))
                    .setMinute(reminderCal.get(Calendar.MINUTE))
                    .setTitleText("Select Reminder Time")
                    .build();

            timePicker.addOnPositiveButtonClickListener(dialogView1 -> {
                reminderCal.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                reminderCal.set(Calendar.MINUTE, timePicker.getMinute());
                tvSelectedReminderTime.setText(timeFormat.format(reminderCal.getTime()));
            });

            timePicker.show(getSupportFragmentManager(), "REMINDER_TIME_PICKER");
        });

        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                etTitle.setError("Title is required");
                return;
            }

            String subject = etSubject.getText().toString().trim();
            if (activeSubjectAdapter != null && activeSubjectAdapter.isUseRow(subject)) {
                subject = activeSubjectAdapter.getUseSubject();
            }

            String formattedDate = dateFormat.format(selectedCal.getTime());
            String formattedTime = timeFormat.format(selectedCal.getTime());
            long timestamp = selectedCal.getTimeInMillis();

            boolean hasReminder = cbEnableReminder.isChecked();
            String reminderTimeStr = hasReminder ? timeFormat.format(reminderCal.getTime()) : null;
            long reminderTimestamp = hasReminder ? reminderCal.getTimeInMillis() : 0;

            boolean isRecurring = cbRepeatWeekly.isChecked();
            StringBuilder recDays = new StringBuilder();
            if (isRecurring) {
                if (cbMon.isChecked()) recDays.append("MON,");
                if (cbTue.isChecked()) recDays.append("TUE,");
                if (cbWed.isChecked()) recDays.append("WED,");
                if (cbThu.isChecked()) recDays.append("THU,");
                if (cbFri.isChecked()) recDays.append("FRI,");
                if (cbSat.isChecked()) recDays.append("SAT,");
                if (cbSun.isChecked()) recDays.append("SUN,");
                if (recDays.length() > 0) recDays.setLength(recDays.length() - 1);
            }

            AssignmentItem targetItem = editing != null ? editing : new AssignmentItem(
                    title,
                    subject,
                    selectedCategory[0],
                    formattedDate,
                    formattedTime,
                    timestamp,
                    false,
                    selectedCategory[0]
            );

            targetItem.setTitle(title);
            targetItem.setSubject(selectedCategory[0].equals(AssignmentItem.CATEGORY_SCHOOL) ? subject : "");
            targetItem.setCategory(selectedCategory[0]);
            targetItem.setImportance(selectedImportance[0]);
            targetItem.setTaskCategory(selectedCategory[0]);
            targetItem.setDueDate(formattedDate);
            targetItem.setDueTime(formattedTime);
            targetItem.setDueTimestamp(timestamp);
            targetItem.setHasReminder(hasReminder);
            targetItem.setReminderTime(reminderTimeStr);
            targetItem.setReminderTimestamp(reminderTimestamp);
            targetItem.setRecurring(isRecurring);
            targetItem.setRecurringDays(isRecurring ? recDays.toString() : null);

            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                if (editing != null) {
                    LocalDataRepository.updateAssignment(db, targetItem);
                } else {
                    LocalDataRepository.saveAssignment(db, targetItem);
                }
                ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();

                runOnUiThread(() -> {
                    Toast.makeText(TasksActivity.this,
                            editing != null ? "Task updated" : "Task added",
                            Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            }).start();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void configureSubjectDropdown(TextInputLayout layout,
                                          MaterialAutoCompleteTextView field,
                                          List<String> subjects) {
        if (field == null || layout == null) return;
        List<String> safeSubjects = subjects != null ? subjects : new ArrayList<>();
        if (safeSubjects.isEmpty()) {
            field.setAdapter(null);
            layout.setEndIconMode(TextInputLayout.END_ICON_NONE);
            return;
        }

        SubjectAdapter adapter = new SubjectAdapter(this, safeSubjects);
        field.setAdapter(adapter);
        layout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        activeSubjectAdapter = adapter;
    }

    private void showAllSubjects(MaterialAutoCompleteTextView field) {
        if (field == null) return;
        if (activeSubjectAdapter != null) {
            activeSubjectAdapter.showAllSubjects();
        }
        field.showDropDown();
    }

    private static class SubjectAdapter extends ArrayAdapter<String> implements Filterable {
        private final List<String> allSubjects;
        private List<String> pendingSubjects;
        private String useSubject;

        SubjectAdapter(Context context, List<String> subjects) {
            super(context, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(subjects));
            this.allSubjects = new ArrayList<>(subjects);
        }

        @NonNull
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
