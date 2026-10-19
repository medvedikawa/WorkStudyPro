package com.example.wspro;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class ProfileSetupActivity extends AppCompatActivity {

    private EditText etProfileName, etProfileCourse;

    // 1. Media picker launcher for photos and videos from device albums
    private final ActivityResultLauncher<String> mediaPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    Toast.makeText(this, "Profile photo selected from albums!", Toast.LENGTH_SHORT).show();
                }
            }
    );

    // 2. Permission launcher for photo/video access dialog
    private final ActivityResultLauncher<String> requestMediaPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openDeviceAlbums();
                } else {
                    Toast.makeText(this, "Permission denied to access albums.", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        etProfileName = findViewById(R.id.etProfileName);
        etProfileCourse = findViewById(R.id.etProfileCourse);
        Button btnSelectAlbumMedia = findViewById(R.id.btnSelectAlbumMedia);
        Button btnCompleteSetup = findViewById(R.id.btnCompleteSetup);

        // Request Push Notification permissions immediately upon profile setup entry (Android 13+)
        checkAndRequestNotificationPermission();

        // Handle button click to access device albums
        btnSelectAlbumMedia.setOnClickListener(v -> verifyAndRequestMediaPermission());

        // Handle profile completion
        btnCompleteSetup.setOnClickListener(v -> {
            String name = etProfileName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter your full name.", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "Profile created successfully!", Toast.LENGTH_SHORT).show();
            // Proceed to Dashboard/Main activity next
        });

        // Initialize the return button in WeekActivity.java
        ImageButton btnReturnBack = findViewById(R.id.btnReturnBack);
        if (btnReturnBack != null) {
            btnReturnBack.setOnClickListener(v -> finish());
        }
    }

    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // This triggers the native system dialog: "Allow WorkStudyPro to send you notifications?"
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void verifyAndRequestMediaPermission() {
        String mediaPermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ separates images and videos
            mediaPermission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            mediaPermission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, mediaPermission) == PackageManager.PERMISSION_GRANTED) {
            openDeviceAlbums();
        } else {
            // This triggers the native system dialog: "Allow this app to access albums?"
            requestMediaPermissionLauncher.launch(mediaPermission);
        }
    }

    private void openDeviceAlbums() {
        // Opens local device storage allowing selection of both images and videos
        mediaPickerLauncher.launch("image/* video/*");
    }
}