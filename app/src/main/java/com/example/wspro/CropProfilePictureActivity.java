package com.example.wspro;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

public class CropProfilePictureActivity extends AppCompatActivity {

    private CropImageView cropImageView;
    private Button btnSaveCrop, btnHeaderSave;
    private String rawUriString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop_profile_picture);

        cropImageView = findViewById(R.id.cropImageView);
        cropImageView.setCircular(true);

        btnSaveCrop = findViewById(R.id.btnSaveCrop);
        btnHeaderSave = findViewById(R.id.btnHeaderSave);
        Button btnRotateLeft = findViewById(R.id.btnRotateLeft);
        Button btnRotateRight = findViewById(R.id.btnRotateRight);
        Button btnResetCrop = findViewById(R.id.btnResetCrop);
        ImageButton btnCancelCrop = findViewById(R.id.btnCancelCrop);

        rawUriString = getIntent().getStringExtra("RAW_IMAGE_URI");
        if (rawUriString == null || rawUriString.isEmpty()) {
            Toast.makeText(this, "No image selected to crop.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnCancelCrop.setOnClickListener(v -> finish());
        btnRotateLeft.setOnClickListener(v -> cropImageView.rotateCounterClockwise());
        btnRotateRight.setOnClickListener(v -> cropImageView.rotateClockwise());
        btnResetCrop.setOnClickListener(v -> cropImageView.resetCrop());

        btnSaveCrop.setOnClickListener(v -> processAndSave());
        btnHeaderSave.setOnClickListener(v -> processAndSave());

        loadImageAsync(Uri.parse(rawUriString));
    }

    private void loadImageAsync(Uri uri) {
        new Thread(() -> {
            try {
                Bitmap bitmap = CropRotateActivity.loadBitmapWithExif(this, uri);
                runOnUiThread(() -> {
                    if (bitmap != null) {
                        cropImageView.setImageBitmap(bitmap);
                        cropImageView.setCircular(true);
                    } else {
                        Toast.makeText(this, "Failed to load image.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading image: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    private void processAndSave() {
        CropImageView.CropData cropData = cropImageView.getCropData();
        btnSaveCrop.setEnabled(false);
        btnHeaderSave.setEnabled(false);
        btnSaveCrop.setText("Processing...");

        new Thread(() -> {
            try {
                Bitmap circularCropped = CropImageView.createCircularCroppedBitmap(cropData);
                if (circularCropped == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to crop image.", Toast.LENGTH_SHORT).show();
                        btnSaveCrop.setEnabled(true);
                        btnHeaderSave.setEnabled(true);
                        btnSaveCrop.setText("Save Photo");
                    });
                    return;
                }

                File avatarFile = new File(getFilesDir(), "profile_avatar_cropped.jpg");
                try (FileOutputStream out = new FileOutputStream(avatarFile)) {
                    circularCropped.compress(Bitmap.CompressFormat.JPEG, 92, out);
                }

                Uri croppedUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileProvider",
                        avatarFile
                );

                runOnUiThread(() -> {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("CROPPED_IMAGE_URI", croppedUri.toString());
                    resultIntent.putExtra("CROPPED_FILE_PATH", avatarFile.getAbsolutePath());
                    setResult(RESULT_OK, resultIntent);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to save photo: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    btnSaveCrop.setEnabled(true);
                    btnHeaderSave.setEnabled(true);
                    btnSaveCrop.setText("Save Photo");
                });
            }
        }).start();
    }
}
