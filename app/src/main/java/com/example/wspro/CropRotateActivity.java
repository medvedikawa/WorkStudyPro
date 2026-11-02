package com.example.wspro;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CropRotateActivity extends AppCompatActivity {

    private CropImageView cropImageView;
    private Button btnConfirmScan, btnHeaderScan;
    private String rawUriString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop_rotate);

        cropImageView = findViewById(R.id.cropImageView);
        btnConfirmScan = findViewById(R.id.btnConfirmScan);
        btnHeaderScan = findViewById(R.id.btnHeaderScan);
        Button btnRotateLeft = findViewById(R.id.btnRotateLeft);
        Button btnRotateRight = findViewById(R.id.btnRotateRight);
        Button btnResetCrop = findViewById(R.id.btnResetCrop);
        ImageButton btnCancelCrop = findViewById(R.id.btnCancelCrop);

        rawUriString = getIntent().getStringExtra("RAW_IMAGE_URI");
        if (rawUriString == null || rawUriString.isEmpty()) {
            Toast.makeText(this, "No image provided to crop.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnCancelCrop.setOnClickListener(v -> finish());
        btnRotateLeft.setOnClickListener(v -> cropImageView.rotateCounterClockwise());
        btnRotateRight.setOnClickListener(v -> cropImageView.rotateClockwise());
        btnResetCrop.setOnClickListener(v -> cropImageView.resetCrop());

        btnConfirmScan.setOnClickListener(v -> processAndProceed());
        btnHeaderScan.setOnClickListener(v -> processAndProceed());

        loadImageAsync(Uri.parse(rawUriString));
    }

    private void loadImageAsync(Uri uri) {
        new Thread(() -> {
            try {
                Bitmap bitmap = loadBitmapWithExif(this, uri);
                runOnUiThread(() -> {
                    if (bitmap != null) {
                        cropImageView.setImageBitmap(bitmap);
                    } else {
                        Toast.makeText(this, "Failed to load image for scanning.", Toast.LENGTH_SHORT).show();
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

    private void processAndProceed() {
        CropImageView.CropData cropData = cropImageView.getCropData();
        btnConfirmScan.setEnabled(false);
        btnHeaderScan.setEnabled(false);
        btnConfirmScan.setText(R.string.crop_processing);

        new Thread(() -> {
            try {
                Bitmap cropped = CropImageView.createCroppedBitmap(cropData);
                if (cropped == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Failed to crop image.", Toast.LENGTH_SHORT).show();
                        btnConfirmScan.setEnabled(true);
                        btnHeaderScan.setEnabled(true);
                    });
                    return;
                }

                File croppedFile = new File(getFilesDir(), "schedule_cropped.jpg");
                try (FileOutputStream out = new FileOutputStream(croppedFile)) {
                    cropped.compress(Bitmap.CompressFormat.JPEG, 92, out);
                }

                Uri croppedUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileProvider",
                        croppedFile
                );

                runOnUiThread(() -> {
                    Intent intent = new Intent(CropRotateActivity.this, EditScheduleActivity.class);
                    intent.putExtra("CAPTURED_IMAGE_URI", croppedUri.toString());
                    intent.putExtra("RAW_IMAGE_URI", rawUriString);
                    startActivity(intent);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Failed to save cropped image: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    btnConfirmScan.setEnabled(true);
                    btnHeaderScan.setEnabled(true);
                    btnConfirmScan.setText(R.string.crop_scan_now);
                });
            }
        }).start();
    }

    public static Bitmap loadBitmapWithExif(Context context, Uri uri) throws Exception {
        int orientation = ExifInterface.ORIENTATION_NORMAL;

        // Try reading EXIF directly from local file first if accessible
        File localFile = new File(context.getFilesDir(), "schedule_capture.jpg");
        if (localFile.exists() && localFile.length() > 0) {
            try {
                ExifInterface exif = new ExifInterface(localFile.getAbsolutePath());
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            } catch (Exception ignored) {}
        }

        if (orientation == ExifInterface.ORIENTATION_NORMAL) {
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    ExifInterface exif = new ExifInterface(in);
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                }
            } catch (Exception ignored) {}
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, options);
        }

        int maxDim = Math.max(options.outWidth, options.outHeight);
        int sampleSize = 1;
        while (maxDim / sampleSize > 2048) {
            sampleSize *= 2;
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }

        if (bitmap == null) return null;

        int rotation = 0;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotation = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotation = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotation = 270;
                break;
        }

        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
                bitmap = rotated;
            }
        }

        return bitmap;
    }
}
