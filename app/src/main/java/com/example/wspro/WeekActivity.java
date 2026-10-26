package com.example.wspro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wspro.adapters.HourTimelineAdapter;

import java.util.ArrayList;
import java.util.List;

public class WeekActivity extends AppCompatActivity {

    private RecyclerView recyclerWeekSchedule;
    private List<ScheduleItem> weeklyScheduleList;
    private HourTimelineAdapter adapter;
    private String selectedDay = "";
    private ScanButtonHelper scanButtonHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_week);
        scanButtonHelper = new ScanButtonHelper(this);

        recyclerWeekSchedule = findViewById(R.id.recyclerWeekSchedule);
        recyclerWeekSchedule.setLayoutManager(new LinearLayoutManager(this));

        weeklyScheduleList = new ArrayList<>();
        adapter = new HourTimelineAdapter(weeklyScheduleList);
        recyclerWeekSchedule.setAdapter(adapter);

        LinearLayout dayContainer = findViewById(R.id.layoutDayContainer);
        DateScrollerHelper.setup(this, dayContainer, dayCode -> {
            loadSavedSchedules(dayCode);
        });

        TextView btnEditWeekSchedule = findViewById(R.id.btnEditWeekSchedule);
        btnEditWeekSchedule.setOnClickListener(v -> showScheduleCategoryChooser());

        BottomNavHelper.setup(this, BottomNavHelper.Tab.WEEK, scanButtonHelper);
    }

    private void showScheduleCategoryChooser() {
        View chooser = getLayoutInflater().inflate(R.layout.dialog_schedule_category, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(chooser)
                .setNegativeButton("Cancel", null)
                .create();

        chooser.findViewById(R.id.btnEditSchoolSchedule).setOnClickListener(v -> {
            dialog.dismiss();
            launchScheduleEditor(false);
        });
        chooser.findViewById(R.id.btnEditWorkSchedule).setOnClickListener(v -> {
            dialog.dismiss();
            launchScheduleEditor(true);
        });
        dialog.show();
    }

    private void launchScheduleEditor(boolean isWork) {
        Intent intent = new Intent(this, EditScheduleActivity.class);
        intent.putExtra(EditScheduleActivity.EXTRA_EDIT_MODE, true);
        intent.putExtra(EditScheduleActivity.EXTRA_CATEGORY, isWork ? "work" : "school");
        startActivity(intent);
    }

    private void checkAndHighlightConflicts(List<ScheduleItem> daySchedules, String currentDay) {
        if (daySchedules == null || daySchedules.size() < 2) return;

        for (int i = 0; i < daySchedules.size(); i++) {
            for (int j = i + 1; j < daySchedules.size(); j++) {
                ScheduleItem event1 = daySchedules.get(i);
                ScheduleItem event2 = daySchedules.get(j);

                boolean hasClash = ConflictChecker.hasConflict(
                        currentDay, event1.getStartTime(), event1.getEndTime(),
                        currentDay, event2.getStartTime(), event2.getEndTime()
                );
                if (hasClash) {
                    String warning = "Conflict detected between " + event1.getSubject() + " and " + event2.getSubject();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (selectedDay != null && !selectedDay.isEmpty()) {
            loadSavedSchedules(selectedDay);
        }
    }

    private void loadSavedSchedules(String day) {
        this.selectedDay = EditScheduleActivity.normalizeDay(day);
        new Thread(() -> {
            List<ScheduleItem> savedData = AppDatabase.getInstance(getApplicationContext())
                    .scheduleDao()
                    .getAllSchedules(this.selectedDay);

            runOnUiThread(() -> adapter.updateData(savedData));

            // If local Room DB is empty, try fetching from Supabase cloud
            if (savedData.isEmpty() && SupabaseClient.getInstance(getApplicationContext()).isLoggedIn()) {
                SupabaseClient.getInstance(getApplicationContext()).fetchSchedulesFromCloud(new SupabaseClient.DataCallback<List<ScheduleItem>>() {
                    @Override
                    public void onSuccess(List<ScheduleItem> cloudItems) {
                        if (cloudItems != null && !cloudItems.isEmpty()) {
                            new Thread(() -> {
                                LocalDataRepository.insertCloudSchedules(
                                        AppDatabase.getInstance(getApplicationContext()), cloudItems);
                                List<ScheduleItem> refreshed = AppDatabase.getInstance(getApplicationContext())
                                        .scheduleDao()
                                        .getAllSchedules(selectedDay);
                                runOnUiThread(() -> adapter.updateData(refreshed));
                            }).start();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        // Silent fallback
                    }
                });
            }
        }).start();
    }
}
