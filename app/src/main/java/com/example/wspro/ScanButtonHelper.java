package com.example.wspro;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;

public class ScanButtonHelper {

    private final AppCompatActivity activity;
    private Uri photoUri;

    private final ActivityResultLauncher<String> cameraPermissionLauncher;
    private final ActivityResultLauncher<Intent> cameraLauncher;
    private final ActivityResultLauncher<String> galleryLauncher;

    public ScanButtonHelper(AppCompatActivity activity) {
        this.activity = activity;

        // Register camera permission launcher
        this.cameraPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openScheduleCamera();
                    } else {
                        Toast.makeText(activity, "Camera permission is required to scan schedules.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // Register camera result launcher -> Opens Crop & Rotate Screen
        this.cameraLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == ComponentActivity.RESULT_OK) {
                        Uri uri = getOrCreatePhotoUri();
                        if (uri != null) {
                            openCropRotateScreen(uri);
                        } else {
                            Toast.makeText(activity, "Could not locate captured photo.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // Register gallery picker launcher -> Opens Crop & Rotate Screen
        this.galleryLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        openCropRotateScreen(uri);
                    }
                }
        );
    }

    private void openCropRotateScreen(Uri uri) {
        Intent intent = new Intent(activity, CropRotateActivity.class);
        intent.putExtra("RAW_IMAGE_URI", uri.toString());
        activity.startActivity(intent);
    }

    public void attachToFab(ImageButton fabScanSchedule) {
        if (fabScanSchedule != null) {
            fabScanSchedule.setOnClickListener(v -> showScanOptionsDialog());
        }
    }

    private void showScanOptionsDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(activity);

        android.view.View sheetView = activity.getLayoutInflater().inflate(R.layout.layout_scan_options, null);
        bottomSheetDialog.setContentView(sheetView);

        sheetView.findViewById(R.id.btnOptionCamera).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            checkCameraPermissionAndScan();
        });

        sheetView.findViewById(R.id.btnOptionGallery).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            openGalleryPicker();
        });

        bottomSheetDialog.show();
    }

    private void checkCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openScheduleCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openScheduleCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        File photoFile = new File(activity.getFilesDir(), "schedule_capture.jpg");
        photoUri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileProvider",
                photoFile);

        // Save URI to SharedPreferences so if orientation changes, it is safely preserved
        activity.getSharedPreferences("ScanPrefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_photo_uri", photoUri.toString())
                .apply();

        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
        takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        try {
            cameraLauncher.launch(takePictureIntent);
        } catch (Exception e) {
            Toast.makeText(activity, "Camera not available: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Uri getOrCreatePhotoUri() {
        if (photoUri != null) return photoUri;
        String saved = activity.getSharedPreferences("ScanPrefs", Context.MODE_PRIVATE)
                .getString("last_photo_uri", null);
        if (saved != null) {
            return Uri.parse(saved);
        }
        File photoFile = new File(activity.getFilesDir(), "schedule_capture.jpg");
        if (photoFile.exists()) {
            return FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileProvider", photoFile);
        }
        return null;
    }

    private void openGalleryPicker() {
        galleryLauncher.launch("image/*");
    }
}
