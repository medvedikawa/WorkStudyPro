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
        SimpleDateFormat formatter = new SimpleDateFormat("h:mm a", Locale.US);
        formatter.setLenient(false);
        for (TimeSlotItem item : items) {
            item.setSubjectError(item.getSubject() == null || item.getSubject().trim().isEmpty()
                    ? "Subject is required." : null);
            item.setStartTimeError(isValidTime(item.getStartTime(), formatter)
                    ? null : "Enter a valid start time.");
            item.setEndTimeError(isValidTime(item.getEndTime(), formatter)
                    ? null : "Enter a valid end time.");
            if (item.getDays().isEmpty()) {
                item.setScheduleError("Select at least one day.");
                continue;
            }

            Date start = parseTime(item.getStartTime(), formatter);
            Date end = parseTime(item.getEndTime(), formatter);
            if (start == null || end == null) {
                item.setScheduleError("Enter valid start and end times.");
            } else if (ConflictChecker.timeToMinutes(item.getEndTime())
                    <= ConflictChecker.timeToMinutes(item.getStartTime())) {
                item.setScheduleError("End time must be after start time.");
            } else {
                item.setScheduleError(null);
            }
        }
    }

    public static boolean validateForSave(List<TimeSlotItem> items) {
        updateErrors(items);
        SimpleDateFormat formatter = new SimpleDateFormat("h:mm a", Locale.US);
        formatter.setLenient(false);
        boolean valid = true;
        for (TimeSlotItem item : items) {
            Date start = parseTime(item.getStartTime(), formatter);
            Date end = parseTime(item.getEndTime(), formatter);
            if (start != null && end != null
                    && ConflictChecker.timeToMinutes(item.getEndTime())
                    <= ConflictChecker.timeToMinutes(item.getStartTime())) {
                item.setEndTimeError("End time must be after start time.");
            }
            if (item.getDays().isEmpty() || item.getScheduleError() != null
                    || item.getSubjectError() != null || item.getStartTimeError() != null
                    || item.getEndTimeError() != null) {
                valid = false;
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
