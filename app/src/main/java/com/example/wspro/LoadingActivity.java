package com.example.wspro;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

public class LoadingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        // Show loading screen for 1 second (1000 milliseconds) then transition to WeekActivity
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(LoadingActivity.this, WeekActivity.class);
            startActivity(intent);
            finish(); // Close loading activity so pressing back returns to Dashboard
        }, 1000);
    }
}