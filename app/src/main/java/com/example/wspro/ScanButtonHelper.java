package com.example.wspro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
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

        // Register camera result launcher
        this.cameraLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == ComponentActivity.RESULT_OK) {
                        Toast.makeText(activity, "Schedule captured! Opening editor...", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(activity, EditScheduleActivity.class);
                        if (photoUri != null) {
                            intent.putExtra("CAPTURED_IMAGE_URI", photoUri.toString());
                        }
                        activity.startActivity(intent);
                    }
                }
        );

        // Register gallery picker launcher
        this.galleryLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        Toast.makeText(activity, "Image selected! Opening editor...", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(activity, EditScheduleActivity.class);
                        intent.putExtra("CAPTURED_IMAGE_URI", uri.toString());
                        activity.startActivity(intent);
                    }
                }
        );
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

        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

        if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
            cameraLauncher.launch(takePictureIntent);
        } else {
            Toast.makeText(activity, "No camera app found on device.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGalleryPicker() {
        galleryLauncher.launch("image/*");
    }
}