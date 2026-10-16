package com.example.wspro;

public class ConflictChecker {

    /**
     * Converts a "HH:MM" time string (24-hour format) into total minutes from midnight.
     * Example: "08:30" -> (8 * 60) + 30 = 510 minutes.
     */
    public static int timeToMinutes(String timeStr) {
        try {
            timeStr = timeStr.trim().toUpperCase();
            boolean isPM = timeStr.contains("PM");
            boolean isAM = timeStr.contains("AM");

            timeStr = timeStr.replaceAll("[^0-9:]", "");
            String[] parts = timeStr.split(":");
            int hours = Integer.parseInt(parts[0].trim());
            int minutes = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

            if (isPM && hours < 12) hours += 12;
            if (isAM && hours == 12) hours = 0;
            return (hours * 60) + minutes;
        } catch (Exception e) {
            return -1;
        }
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