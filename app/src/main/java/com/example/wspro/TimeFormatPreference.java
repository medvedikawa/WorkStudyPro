package com.example.wspro;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class TimeFormatPreference {
    private static final String PREFERENCES = "app_settings";
    private static final String KEY_24_HOUR = "use_24_hour_time";

    private TimeFormatPreference() {}

    public static boolean is24Hour(Context context) {
        return preferences(context).getBoolean(KEY_24_HOUR, false);
    }

    public static String pattern(Context context) {
        return is24Hour(context) ? "HH:mm" : "h:mm a";
    }

    public static String formatTime(Context context, String value) {
        if (value == null || value.trim().isEmpty()) return value == null ? "" : value;
        String normalized = value.trim().toUpperCase(Locale.US);
        for (String pattern : new String[]{"h:mm a", "H:mm", "HH:mm"}) {
            SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
            parser.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = parser.parse(normalized, position);
            if (parsed != null && position.getIndex() == normalized.length()) {
                return new SimpleDateFormat(pattern(context), Locale.getDefault()).format(parsed);
            }
        }
        return value;
    }

    public static String formatTime(Context context, int hourOfDay, int minute) {
        if (is24Hour(context)) {
            return String.format(Locale.US, "%02d:%02d", hourOfDay, minute);
        }
        int hour12 = hourOfDay % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format(Locale.US, "%d:%02d %s", hour12, minute,
                hourOfDay < 12 ? "AM" : "PM");
    }

    public static String formatTimestamp(Context context, long timestamp, String datePattern) {
        return new SimpleDateFormat(datePattern + pattern(context), Locale.getDefault())
                .format(new Date(timestamp));
    }

    public static void bind(Context context, Spinner spinner) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                context, R.array.time_format_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(is24Hour(context) ? 1 : 0, false);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent,
                                       android.view.View view, int position, long id) {
                preferences(context).edit().putBoolean(KEY_24_HOUR, position == 1).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
