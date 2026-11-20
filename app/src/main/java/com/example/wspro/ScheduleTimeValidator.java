package com.example.wspro;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ScheduleTimeValidator {
    private ScheduleTimeValidator() {
    }

    public static String normalizeDay(String rawDay) {
        if (rawDay == null || rawDay.trim().isEmpty()) return "";
        String day = rawDay.trim().toUpperCase(Locale.US);
        if (day.contains("CHOOSE") || day.contains("SELECT")) return "";
        if (day.startsWith("MO") || day.contains("MON") || day.contains("LUN")) return "MON";
        if (day.startsWith("TU") || day.contains("TUE") || day.contains("MAR")) return "TUE";
        if (day.startsWith("WE") || day.contains("WED") || day.contains("MIY")) return "WED";
        if (day.startsWith("TH") || day.contains("THU") || day.contains("HUW") || day.equals("R")) return "THU";
        if (day.startsWith("FR") || day.contains("FRI") || day.contains("BIY")) return "FRI";
        if (day.startsWith("SA") || day.contains("SAT") || day.contains("SAB")) return "SAT";
        if (day.startsWith("SU") || day.contains("SUN") || day.contains("LIN")) return "SUN";
        return day;
    }

    public static void updateErrors(List<TimeSlotItem> items) {
        // Validation only runs when Save is tapped (validateForSave), never while typing.
    }

    public static boolean validateForSave(List<TimeSlotItem> items) {
        SimpleDateFormat formatter = new SimpleDateFormat("h:mm a", Locale.US);
        formatter.setLenient(false);
        boolean valid = true;
        for (TimeSlotItem item : items) {
            item.setSubjectError(null);
            boolean startValid = isValidTime(item.getStartTime(), formatter);
            boolean endValid = isValidTime(item.getEndTime(), formatter);
            item.setStartTimeError(startValid ? null : "Enter a valid start time.");
            item.setEndTimeError(endValid ? null : "Enter a valid end time.");

            if (item.getDays().isEmpty()) {
                item.setScheduleError("Select at least one day.");
                valid = false;
            } else if (!startValid || !endValid) {
                item.setScheduleError("Enter valid start and end times.");
                valid = false;
            } else if (!item.isWork() && ConflictChecker.timeToMinutes(item.getEndTime())
                    <= ConflictChecker.timeToMinutes(item.getStartTime())) {
                item.setScheduleError("End time must be after start time.");
                item.setEndTimeError("End time must be after start time.");
                valid = false;
            } else {
                item.setScheduleError(null);
                item.setStartTimeError(null);
                item.setEndTimeError(null);
            }
        }
        return valid;
    }

    private static boolean isValidTime(String value, SimpleDateFormat formatter) {
        return parseTime(value, formatter) != null;
    }

    private static Date parseTime(String value, SimpleDateFormat formatter) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.US);
        ParsePosition position = new ParsePosition(0);
        Date parsed = formatter.parse(normalized, position);
        if (parsed != null && position.getIndex() == normalized.length()) return parsed;

        SimpleDateFormat twentyFourHour = new SimpleDateFormat("HH:mm", Locale.US);
        twentyFourHour.setLenient(false);
        position = new ParsePosition(0);
        parsed = twentyFourHour.parse(normalized, position);
        return parsed != null && position.getIndex() == normalized.length() ? parsed : null;
    }
}
