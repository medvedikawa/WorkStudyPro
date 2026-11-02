package com.example.wspro;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

import java.util.Arrays;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

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

        btnAddNewTaskHeader.setOnClickListener(v -> showAddAssignmentDialog());
        btnEmptyAddTask.setOnClickListener(v -> showAddAssignmentDialog());
        setupTaskFilters();

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
            public void onDelete(AssignmentItem item) {
                new Thread(() -> {
                    LocalDataRepository.deleteAssignment(
                            AppDatabase.getInstance(getApplicationContext()), item);
                    ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();
                }).start();
            }
        });
        recyclerTasks.setAdapter(adapter);

        setupBottomNav();
        setupReactiveObservers();
    }

    @Override
    protected void onDestroy() {
        cancelPendingSearch();
        super.onDestroy();
    }

    private void setupBottomNav() {
        BottomNavHelper.setup(this, BottomNavHelper.Tab.TASKS, scanButtonHelper);
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
                SupabaseClient.getInstance(getApplicationContext()).fetchAssignmentsFromCloud(new SupabaseClient.DataCallback<List<AssignmentItem>>() {
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
        };
        currentTasksLiveData.observe(this, currentTasksObserver);
        updateClearAllVisibility();
    }

    private void applyPrioritiesAndRender() {
        List<AssignmentItem> prioritized =
                PriorityEngine.calculateAndSort(visibleAssignments, currentSchedules);

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
        }
    }

    private void showCategoryFilterMenu(View anchor) {
        showFilterMenu(anchor, categoryFilter,
                Arrays.asList("Task Performance", "Group Work", "Laboratory", "Assignment"),
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
                selected ? R.color.ws_on_brand : R.color.text_muted));
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

    private void showAddAssignmentDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_assignment, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etTitle = dialogView.findViewById(R.id.etAssignmentTitle);
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

        TextView chipTaskPerformance = dialogView.findViewById(R.id.chipTaskPerformance);
        TextView chipGroupWork = dialogView.findViewById(R.id.chipGroupWork);
        TextView chipLaboratory = dialogView.findViewById(R.id.chipLaboratory);
        TextView chipAssignment = dialogView.findViewById(R.id.chipAssignment);

        LinearLayout btnPickDate = dialogView.findViewById(R.id.btnPickDate);
        TextView tvSelectedDate = dialogView.findViewById(R.id.tvSelectedDate);

        LinearLayout btnPickTime = dialogView.findViewById(R.id.btnPickTime);
        TextView tvSelectedTime = dialogView.findViewById(R.id.tvSelectedTime);

        Button btnSave = dialogView.findViewById(R.id.btnSaveAssignment);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelAssignment);

        final String[] selectedCategory = {"Task Performance"};
        final Calendar selectedCal = Calendar.getInstance();
        selectedCal.add(Calendar.DAY_OF_YEAR, 1);
        selectedCal.set(Calendar.HOUR_OF_DAY, 23);
        selectedCal.set(Calendar.MINUTE, 59);
        selectedCal.set(Calendar.SECOND, 0);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat displayDateFormat = new SimpleDateFormat("EEE, MMM dd", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        tvSelectedDate.setText(displayDateFormat.format(selectedCal.getTime()));
        tvSelectedTime.setText(timeFormat.format(selectedCal.getTime()));

        TextView[] chips = {chipTaskPerformance, chipGroupWork, chipLaboratory, chipAssignment};
        String[] catNames = {"Task Performance", "Group Work", "Laboratory", "Assignment"};

        View.OnClickListener categoryClick = v -> {
            for (int i = 0; i < chips.length; i++) {
                if (chips[i] == v) {
                    chips[i].setBackgroundResource(R.drawable.bg_toggle_selected);
                    chips[i].setTextColor(ContextCompat.getColor(this, R.color.ws_on_brand));
                    selectedCategory[0] = catNames[i];
                } else {
                    chips[i].setBackgroundResource(R.drawable.bg_toggle_unselected);
                    chips[i].setTextColor(ContextCompat.getColor(this, R.color.text_muted));
                }
            }
        };

        for (TextView chip : chips) {
            chip.setOnClickListener(categoryClick);
        }

        btnPickDate.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                selectedCal.set(Calendar.YEAR, year);
                selectedCal.set(Calendar.MONTH, month);
                selectedCal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                tvSelectedDate.setText(displayDateFormat.format(selectedCal.getTime()));
            }, selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar.DAY_OF_MONTH));
            dpd.show();
        });

        btnPickTime.setOnClickListener(v -> {
            TimePickerDialog tpd = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                selectedCal.set(Calendar.HOUR_OF_DAY, hourOfDay);
                selectedCal.set(Calendar.MINUTE, minute);
                tvSelectedTime.setText(timeFormat.format(selectedCal.getTime()));
            }, selectedCal.get(Calendar.HOUR_OF_DAY), selectedCal.get(Calendar.MINUTE), false);
            tpd.show();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(dialogInterface -> {
            if (activeSubjectField == etSubject) {
                activeSubjectLayout = null;
                activeSubjectField = null;
                activeSubjectAdapter = null;
            }
        });

        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String subject = etSubject.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter an assignment title.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (subject.isEmpty()) {
                subject = "General Academic";
            }

            String dateStr = dateFormat.format(selectedCal.getTime());
            String timeStr = timeFormat.format(selectedCal.getTime());
            long timestamp = selectedCal.getTimeInMillis();

            AssignmentItem newItem = new AssignmentItem(
                    title,
                    subject,
                    selectedCategory[0],
                    dateStr,
                    timeStr,
                    timestamp,
                    false
            );

            new Thread(() -> {
                LocalDataRepository.saveAssignment(
                        AppDatabase.getInstance(getApplicationContext()), newItem);
                ((WorkStudyProApplication) getApplication()).onLocalChangesQueued();

                runOnUiThread(() -> {
                    Toast.makeText(this, "Task added & prioritized!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            }).start();
        });

        dialog.show();
    }

    private void configureSubjectDropdown(TextInputLayout layout,
                                          MaterialAutoCompleteTextView field,
                                          List<String> subjects) {
        String currentText = field.getText().toString();
        if (subjects == null || subjects.isEmpty()) {
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
            activeSubjectAdapter.updateSubjects(subjects, currentText);
        }
    }

    private void showAllSubjects(MaterialAutoCompleteTextView field) {
        if (activeSubjectAdapter == null) return;
        activeSubjectAdapter.showAllSubjects();
        field.showDropDown();
    }

    private static class SubjectAdapter extends ArrayAdapter<String> implements Filterable {
        private final List<String> allSubjects = new ArrayList<>();
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

        void updateSubjects(List<String> subjects, String query) {
            allSubjects.clear();
            allSubjects.addAll(subjects);
            getFilter().filter(query);
        }

        void showAllSubjects() {
            useSubject = null;
            clear();
            addAll(allSubjects);
            notifyDataSetChanged();
        }

        boolean isUseRow(String value) {
            return useSubject != null && value.equals("Use '" + useSubject + "'");
        }

        String getUseSubject() {
            return useSubject;
        }
    }
}
