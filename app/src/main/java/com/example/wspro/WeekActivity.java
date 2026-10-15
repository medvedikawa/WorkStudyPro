package com.example.wspro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

public class WeekActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openScheduleCamera();
                } else {
                    Toast.makeText(this, "Camera permission is required to scan schedules.", Toast.LENGTH_SHORT).show();
                }
            }
    );
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Toast.makeText(this, "Schedule captured! Extracting text...", Toast.LENGTH_LONG).show();
                    // TODO: Pass image to OCR / Text Recognition engine here
                }
            }
    );
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
        ImageButton fabScanSchedule = findViewById(R.id.fabScanSchedule);
        if (fabScanSchedule != null) {
            fabScanSchedule.setOnClickListener(v -> checkCameraPermissionAndScan());
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
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out); // Close WeekActivity to smoothly return to Today
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

    private void checkCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openScheduleCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openScheduleCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(takePictureIntent);
        } else {
            Toast.makeText(this, "No camera app found on device.", Toast.LENGTH_SHORT).show();
        }
    }
}