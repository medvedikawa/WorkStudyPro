package com.example.wspro;

public class ConflictChecker {

    /**
     * Converts a "HH:MM" time string (24-hour format) into total minutes from midnight.
     * Example: "08:30" -> (8 * 60) + 30 = 510 minutes.
     */
    public static int timeToMinutes(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 0;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());
            java.util.Date date = sdf.parse(timeStr.trim().toUpperCase());
            if (date != null) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(date);
                return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } return 0;
    }

    /**
     * Checks if two time intervals overlap on the same day.
     *
     * @param startA Start time of event 1 (Format: "HH:MM")
     * @param endA   End time of event 1 (Format: "HH:MM")
     * @param startB Start time of event 2 (Format: "HH:MM")
     * @param endB   End time of event 2 (Format: "HH:MM")
     * @return true if there is a time clash/conflict, false otherwise.
     */
    public static boolean hasConflict(String dayA, String startA, String endA, String dayB, String startB, String endB) {

        if (!dayA.equalsIgnoreCase(dayB)) {return false;}

        int startMinA = timeToMinutes(startA);
        int endMinA = timeToMinutes(endA);
        int startMinB = timeToMinutes(startB);
        int endMinB = timeToMinutes(endB);

        if (startMinA == -1 || endMinA == -1 || startMinB == -1 || endMinB == -1) {
            return false;
        }

        // Two intervals overlap if Event A starts before Event B ends,
        // and Event B starts before Event A ends.
        return (startMinA < endMinB) && (startMinB < endMinA);

    }
}