package com.example.wspro;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ScheduleScanProcessor {
    private static final String TAG = "ScheduleScanProcessor";

    public interface Listener {
        void onScanComplete(ScheduleTableParser.ParseResult result);
        void onScanFailed(Exception error);
        void onImageLoadFailed(IOException error);
    }

    private ScheduleScanProcessor() {
    }

    public static void scan(Context context, Uri imageUri, Listener listener) {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        try {
                            List<ScheduleTableParser.Word> words = toWords(text);
                            exportDebugWords(context, words);
                            ScheduleTableParser.ParseResult result = ScheduleTableParser.parse(words);
                            Log.d(TAG, "Parsed " + result.meetings.size() + " meetings from scanner:");
                            for (ScheduleTableParser.ParsedMeeting meeting : result.meetings) {
                                Log.d(TAG, "Meeting: " + meeting);
                            }
                            listener.onScanComplete(result);
                        } finally {
                            recognizer.close();
                        }
                    })
                    .addOnFailureListener(error -> {
                        recognizer.close();
                        listener.onScanFailed(error);
                    });
        } catch (IOException error) {
            recognizer.close();
            listener.onImageLoadFailed(error);
        }
    }

    private static List<ScheduleTableParser.Word> toWords(Text visionText) {
        List<ScheduleTableParser.Word> words = new ArrayList<>();
        if (visionText == null) return words;
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    Rect box = element.getBoundingBox();
                    if (box != null) {
                        words.add(new ScheduleTableParser.Word(
                                element.getText(), box.left, box.top, box.right, box.bottom));
                    }
                }
            }
        }
        return words;
    }

    private static void exportDebugWords(Context context, List<ScheduleTableParser.Word> words) {
        boolean isDebug = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!isDebug || words.isEmpty()) return;

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir == null || !downloadsDir.exists()) {
            downloadsDir = context.getExternalCacheDir();
        }
        if (downloadsDir == null) return;

        File file = new File(downloadsDir, "ocr_words_debug.json");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("[\n");
            for (int i = 0; i < words.size(); i++) {
                ScheduleTableParser.Word word = words.get(i);
                writer.write(String.format(Locale.US,
                        "  {\"text\": \"%s\", \"left\": %d, \"top\": %d, \"right\": %d, \"bottom\": %d}%s\n",
                        word.text.replace("\"", "\\\""), word.left, word.top, word.right, word.bottom,
                        i < words.size() - 1 ? "," : ""));
            }
            writer.write("]\n");
            Log.d(TAG, "Exported debug OCR words to " + file.getAbsolutePath());
        } catch (IOException error) {
            Log.e(TAG, "Failed to export OCR words debug JSON", error);
        }
    }
}
