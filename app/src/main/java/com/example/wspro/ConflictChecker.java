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

        // Two intervals overlap if Event A starts before Event B ends,
        // and Event B starts before Event A ends.
        return Math.max(startMinA, startMinB) < Math.min(endMinA, endMinB);
    }

    public static boolean isTooClose(String dayA, String startA, String endA,
                                     String dayB, String startB, String endB, int bufferMinutes){
        if (!dayA.equalsIgnoreCase(dayB)) {return false;}

        if (hasConflict(dayA, startA, endA, dayB, startB, endB)) return false;

        int startMinA = timeToMinutes(startA);
        int endMinA = timeToMinutes(endA);
        int startMinB = timeToMinutes(startB);
        int endMinB = timeToMinutes(endB);

        int gap1 = startMinB - endMinA;
        int gap2 = startMinA - endMinB;

        return (gap1 > 0 && gap1 <= bufferMinutes) || (gap2 > 0 && gap2 <= bufferMinutes);
        }
    }
