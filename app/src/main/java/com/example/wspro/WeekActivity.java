package com.example.wspro;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wspro.adapters.HourTimelineAdapter;

import java.util.ArrayList;
import java.util.List;

public class WeekActivity extends AppCompatActivity {

    private RecyclerView recyclerWeekSchedule;
    private HourTimelineAdapter adapter;
    private String selectedDay = "";
    private ScanButtonHelper scanButtonHelper;
    private LiveData<List<ScheduleItem>> activeLiveData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_week);
        scanButtonHelper = new ScanButtonHelper(this);

        recyclerWeekSchedule = findViewById(R.id.recyclerWeekSchedule);
        recyclerWeekSchedule.setLayoutManager(new LinearLayoutManager(this));

        adapter = new HourTimelineAdapter(new ArrayList<>());
        recyclerWeekSchedule.setAdapter(adapter);

        LinearLayout dayContainer = findViewById(R.id.layoutDayContainer);
        DateScrollerHelper.setup(this, dayContainer, dayCode -> {
            loadSavedSchedules(dayCode);
        });

        TextView btnEditWeekSchedule = findViewById(R.id.btnEditWeekSchedule);
        btnEditWeekSchedule.setOnClickListener(v -> launchScheduleEditor());

        BottomNavHelper.setup(this, BottomNavHelper.Tab.WEEK, scanButtonHelper);
    }

    private void launchScheduleEditor() {
        Intent intent = new Intent(this, EditScheduleActivity.class);
        intent.putExtra(EditScheduleActivity.EXTRA_EDIT_MODE, true);
        startActivity(intent);
    }

    private void loadSavedSchedules(String day) {
        this.selectedDay = ScheduleTimeValidator.normalizeDay(day);
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());

        if (activeLiveData != null) {
            activeLiveData.removeObservers(this);
        }

        activeLiveData = db.scheduleDao().getAllSchedulesLiveData(this.selectedDay);
        activeLiveData.observe(this, savedData -> {
            List<ScheduleItem> list = savedData != null ? savedData : new ArrayList<>();
            adapter.updateData(list);

            if (list.isEmpty() && SupabaseClient.getInstance(getApplicationContext()).isLoggedIn()) {
                SupabaseClient.getInstance(getApplicationContext()).fetchSchedulesFromCloud(new SupabaseClient.DataCallback<>() {
                    @Override
                    public void onSuccess(List<ScheduleItem> cloudItems) {
                        if (cloudItems != null && !cloudItems.isEmpty()) {
                            new Thread(() -> LocalDataRepository.insertCloudSchedules(
                                    AppDatabase.getInstance(getApplicationContext()), cloudItems)).start();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                    }
                });
            }
        });
    }
}
