package com.example.wspro;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private ScanButtonHelper scanButtonHelper;

    private List<AssignmentItem> rawAssignments = new ArrayList<>();
    private List<ScheduleItem> currentSchedules = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasks);

        scanButtonHelper = new ScanButtonHelper(this);

        recyclerTasks = findViewById(R.id.recyclerTasks);
        layoutEmptyTasks = findViewById(R.id.layoutEmptyTasks);
        tvTaskSubtitle = findViewById(R.id.tvTaskSubtitle);
        Button btnAddNewTaskHeader = findViewById(R.id.btnAddNewTaskHeader);
        Button btnEmptyAddTask = findViewById(R.id.btnEmptyAddTask);

        btnAddNewTaskHeader.setOnClickListener(v -> showAddAssignmentDialog());
        btnEmptyAddTask.setOnClickListener(v -> showAddAssignmentDialog());

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

    private void setupBottomNav() {
        BottomNavHelper.setup(this, BottomNavHelper.Tab.TASKS, scanButtonHelper);
    }

    private void setupReactiveObservers() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());

        db.scheduleDao().getAllLiveData().observe(this, schedules -> {
            currentSchedules = schedules != null ? schedules : new ArrayList<>();
            applyPrioritiesAndRender(rawAssignments);
        });

        db.assignmentDao().getAllAssignmentsLiveData().observe(this, assignments -> {
            rawAssignments = assignments != null ? assignments : new ArrayList<>();
            if (rawAssignments.isEmpty() && SupabaseClient.getInstance(getApplicationContext()).isLoggedIn()) {
                SupabaseClient.getInstance(getApplicationContext()).fetchAssignmentsFromCloud(new SupabaseClient.DataCallback<List<AssignmentItem>>() {
                    @Override
                    public void onSuccess(List<AssignmentItem> cloudItems) {
                        if (cloudItems != null && !cloudItems.isEmpty()) {
                            new Thread(() -> LocalDataRepository.insertCloudAssignments(db, cloudItems)).start();
                        } else {
                            applyPrioritiesAndRender(rawAssignments);
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        applyPrioritiesAndRender(rawAssignments);
                    }
                });
            } else {
                applyPrioritiesAndRender(rawAssignments);
            }
        });
    }

    private void applyPrioritiesAndRender(List<AssignmentItem> items) {
        List<AssignmentItem> prioritized = PriorityEngine.calculateAndSort(items, currentSchedules);

        adapter.updateData(prioritized);
        if (prioritized.isEmpty()) {
            layoutEmptyTasks.setVisibility(View.VISIBLE);
            recyclerTasks.setVisibility(View.GONE);
            tvTaskSubtitle.setText("0 pending academic tasks");
        } else {
            layoutEmptyTasks.setVisibility(View.GONE);
            recyclerTasks.setVisibility(View.VISIBLE);

            long pendingCount = 0;
            for (AssignmentItem a : prioritized) {
                if (!a.isCompleted()) pendingCount++;
            }
            tvTaskSubtitle.setText(pendingCount + " active tasks • Sorted by urgency & work collision");
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
        EditText etSubject = dialogView.findViewById(R.id.etAssignmentSubject);

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
}
