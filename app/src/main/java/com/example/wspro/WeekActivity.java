package com.example.wspro;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class WeekActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_week);

        // 1. Initialize Quick Action inside Wednesday's Conflict Card
        Button btnWeekShiftSwap = findViewById(R.id.btnWeekShiftSwap);
        if (btnWeekShiftSwap != null) {
            btnWeekShiftSwap.setOnClickListener(v ->
                    Toast.makeText(this, "Shift Swap Template generated!", Toast.LENGTH_LONG).show()
            );
        }

        // 2. Initialize Central FAB
        ImageButton fabQuickAdd = findViewById(R.id.fabScanSchedule);
        if (fabQuickAdd != null) {
            fabQuickAdd.setOnClickListener(v ->
                    Toast.makeText(this, "Quick Add: Class or Work Shift", Toast.LENGTH_SHORT).show()
            );
        }

        // 3. Initialize Bottom Navigation Bar Items
        TextView navToday = findViewById(R.id.navToday);
        TextView navWeek = findViewById(R.id.navWeek);
        TextView navTasks = findViewById(R.id.navTasks);
        TextView navYou = findViewById(R.id.navYou);

        if (navToday != null) {
            navToday.setOnClickListener(v -> {
                Intent intent = new Intent(WeekActivity.this, MainActivity.class);
                startActivity(intent);
                finish(); // Close WeekActivity to smoothly return to Today
            });
        }

        if (navWeek != null) {
            navWeek.setOnClickListener(v ->
                    Toast.makeText(this, "Already viewing Week view", Toast.LENGTH_SHORT).show()
            );
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
}