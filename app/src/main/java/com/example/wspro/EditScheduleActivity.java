package com.example.wspro;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EditScheduleActivity extends AppCompatActivity {

    private EditText etEditTitle;
    private RecyclerView recyclerTimeSlots;
    private TextView btnAddTimeSlot;
    private Button btnSaveSchedule;
    private ImageButton btnReturnBack;
    private TimeSlotAdapter adapter;
    private List<TimeSlotItem> timeSlotList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_schedule);

        etEditTitle = findViewById(R.id.etEditTitle);
        recyclerTimeSlots = findViewById(R.id.recyclerTimeSlots);
        btnAddTimeSlot = findViewById(R.id.btnAddTimeSlot);
        btnSaveSchedule = findViewById(R.id.btnSaveSchedule);
        btnReturnBack = findViewById(R.id.btnReturnBack);


        if (btnReturnBack != null){
            btnReturnBack.setOnClickListener(v -> finish());
        }

        timeSlotList = new ArrayList<>();
        recyclerTimeSlots.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TimeSlotAdapter(timeSlotList);
        recyclerTimeSlots.setAdapter(adapter);

        // Check if an image Uri was passed from the camera/gallery capture
        String imageUriString = getIntent().getStringExtra("CAPTURED_IMAGE_URI");
        if (imageUriString != null) {
            Uri imageUri = Uri.parse(imageUriString);
            processImageWithMLKit(imageUri);
        } else {
            // Fallback empty row if opened manually
            timeSlotList.add(new TimeSlotItem("", "", ""));
            adapter.notifyDataSetChanged();
        }

        btnAddTimeSlot.setOnClickListener(v -> {
            timeSlotList.add(new TimeSlotItem("", "", ""));
            adapter.notifyItemInserted(timeSlotList.size() - 1);
            recyclerTimeSlots.scrollToPosition(timeSlotList.size() - 1);
        });

        btnSaveSchedule.setOnClickListener(v -> {
            // Trigger ConflictChecker verification here
            Toast.makeText(this, "Schedule saved!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
    private void processImageWithMLKit(Uri imageUri) {
        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        parseScannedText(visionText);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to read text: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        timeSlotList.add(new TimeSlotItem("", "", ""));
                        adapter.notifyDataSetChanged();
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseScannedText(Text visionText) {
        // Simple heuristic parser for demo & capstone workflow
        // In practice, ML Kit extracts text blocks; we look for lines resembling days and times.
        boolean foundData = false;

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String text = line.getText();

                if (text.toUpperCase().contains("AM") || text.toUpperCase().contains("PM")){
                    String detectDay = extractDay(text);
                    String[] times = extractTimeRange(text);

                    String  startTime = times.length > 0 ? times[0] : text;
                    String endTime = times.length > 1 ? times[1] : "";

                    timeSlotList.add(new TimeSlotItem(detectDay, startTime, endTime));
                    foundData = true;

                }
            }
        }

        if (!foundData) {
            // If OCR couldn't auto-detect structured rows, add a blank one for manual typing
            timeSlotList.add(new TimeSlotItem("", "", ""));
            Toast.makeText(this, "Please verify and adjust fields manually.", Toast.LENGTH_LONG).show();
        }

        adapter.notifyDataSetChanged();
        etEditTitle.setText("Scanned Schedule");
    }

    private String extractDay(String line){
        String upper = line.toUpperCase();
        if (upper.contains("MON")) return "Mo";
        if (upper.contains("TUE")) return "Tu";
        if (upper.contains("WED")) return "We";
        if (upper.contains("THU")) return "Th";
        if (upper.contains("FRI")) return "Fr";
        if (upper.contains("SAT")) return "Sa";
        if (upper.contains("SUN")) return "Su";
        return "";
    }

    private String[] extractTimeRange(String line) {
        String[] parts = line.split("[-–]|(?i)\\bto\\b");
        if (parts.length >= 2) {
            return new String[]{parts[0].trim(), parts[1].trim()};
        }
        return new String[]{line.trim()};
    }
}