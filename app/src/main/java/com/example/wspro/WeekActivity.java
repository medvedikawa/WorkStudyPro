package com.example.wspro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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

        TextView btnEditCategory = findViewById(R.id.btnEditCategory);
        if (btnEditCategory != null) {
            btnEditCategory.setOnClickListener(v -> showCategoryFilterDialog());
        }

        // to clear the database
      /**  new Thread(() -> {
            AppDatabase.getInstance(getApplicationContext())
                    .scheduleDao()
                    .deleteAllSchedules();
        }).start();**/

        recyclerWeekSchedule = findViewById(R.id.recyclerWeekSchedule);
        recyclerWeekSchedule.setLayoutManager(new LinearLayoutManager(this));

        weeklyScheduleList = new ArrayList<>();
        adapter = new HourTimelineAdapter(weeklyScheduleList);
        recyclerWeekSchedule.setAdapter(adapter);

        LinearLayout dayContainer = findViewById(R.id.layoutDayContainer);
        DateScrollerHelper.setup(this, dayContainer, selectedDay -> {
            loadSavedSchedules(selectedDay);
        });


        // 2. Initialize Central FAB
        ImageButton fabScanSchedule = findViewById(R.id.fabScanSchedule);
        scanButtonHelper.attachToFab(fabScanSchedule);

        // 3. Initialize Bottom Navigation Bar Items
        TextView navToday = findViewById(R.id.navToday);
        TextView navTasks = findViewById(R.id.navTasks);
        TextView navYou = findViewById(R.id.navYou);

        if (navToday != null) {
            navToday.setOnClickListener(v -> {
                Intent intent = new Intent(WeekActivity.this, MainActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out); // Close WeekActivity to smoothly return to Today
            });
        }


        if (navTasks != null) {
            navTasks.setOnClickListener(v ->
                    Toast.makeText(this, "Opening Tasks...", Toast.LENGTH_SHORT).show()
            );
        }

        if (navYou != null) {
            navYou.setOnClickListener(v ->
                    Toast.makeText(this, "Opening Profile...", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void checkAndHighlightConflicts(List<ScheduleItem> daySchedules, String currentDay) {
        if (daySchedules == null || daySchedules.size() < 2) return;

        for (int i = 0; i < daySchedules.size(); i++) {
            for (int j = i + 1; j < daySchedules.size(); j++) {
                ScheduleItem event1 = daySchedules.get(i);
                ScheduleItem event2 = daySchedules.get(j);

                // Assuming ScheduleItem provides day, start time, and end time
                boolean hasClash = ConflictChecker.hasConflict(
                        currentDay, event1.getStartTime(), event1.getEndTime(),
                        currentDay, event2.getStartTime(), event2.getEndTime()
                );
                if (hasClash) {
                    // Trigger your warning banner or flag the conflicting items here
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

    private void showCategoryFilterDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.layout_edit_category, null);
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        dialog.setContentView(dialogView);

        // Option: Class
        View optionClass = dialogView.findViewById(R.id.optionClass);
        if (optionClass != null) {
            optionClass.setOnClickListener(v -> {
                openEditForCategory("Class");
                dialog.dismiss();
            });
        }

        // Option: Work
        View optionWork = dialogView.findViewById(R.id.optionWork);
        if (optionWork != null) {
            optionWork.setOnClickListener(v -> {
                openEditForCategory("Work");
                dialog.dismiss();
            });
        }

        // Option: Other
        View optionOther = dialogView.findViewById(R.id.optionOther);
        if (optionOther != null) {
            optionOther.setOnClickListener(v -> {
                openEditForCategory("Other");
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private void openEditForCategory(String category) {
        Intent intent = new Intent(WeekActivity.this, EditScheduleActivity.class);
        intent.putExtra("SELECTED_CATEGORY", category);
        startActivity(intent);
    }

    private void loadSavedSchedules(String day){
        this.selectedDay = day;
        new Thread (() -> {
            List<ScheduleItem> savedData = AppDatabase.getInstance(getApplicationContext())
                    .scheduleDao()
                    .getAllSchedules(day);

            runOnUiThread(() -> {

                adapter.updateData(savedData);
                // adapter.notifyDataSetChanged(); // You'll need an adapter here
            });
        }).start();
    }
}