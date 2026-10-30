package com.example.wspro;

public class ConflictChecker {

    /**
     * Converts a "h:mm a" time string into total minutes from midnight.
     * Example: "8:30 AM" -> 510 minutes. "7:00 PM" -> 1140 minutes.
     */
    public static int timeToMinutes(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return 0;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.US);
            sdf.setLenient(false);
            java.util.Date date = sdf.parse(timeStr.trim().toUpperCase(java.util.Locale.US));
            if (date != null) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(date);
                return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
            }
        } catch (Exception ignored) {}

        // Fallback for 24-hour HH:mm string if present
        try {
            String[] parts = timeStr.trim().split(":");
            if (parts.length == 2) {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                return h * 60 + m;
            }
        } catch (Exception ignored) {}

        return 0;
    }

    public static String getNextDay(String rawDay) {
        String d = EditScheduleActivity.normalizeDay(rawDay);
        switch (d) {
            case "MON": return "TUE";
            case "TUE": return "WED";
            case "WED": return "THU";
            case "THU": return "FRI";
            case "FRI": return "SAT";
            case "SAT": return "SUN";
            case "SUN": return "MON";
            default: return "MON";
        }
    }

    /**
     * Checks if two time intervals overlap on the same day or across midnight spillovers.
     */
    public static boolean hasConflict(String dayA, String startA, String endA, String dayB, String startB, String endB) {
        if (dayA == null || dayB == null) return false;

        String normDayA = EditScheduleActivity.normalizeDay(dayA);
        String normDayB = EditScheduleActivity.normalizeDay(dayB);

        int startMinA = timeToMinutes(startA);
        int endMinA = timeToMinutes(endA);
        if (endMinA <= startMinA) {
            endMinA += 1440; // overnight shift
        }

        int startMinB = timeToMinutes(startB);
        int endMinB = timeToMinutes(endB);
        if (endMinB <= startMinB) {
            endMinB += 1440; // overnight shift
        }

        // 1. Same base day
        if (normDayA.equalsIgnoreCase(normDayB)) {
            if (Math.max(startMinA, startMinB) < Math.min(endMinA, endMinB)) {
                return true;
            }
        }

        // 2. Event A spills over into Event B's base day
        if (endMinA > 1440) {
            String nextA = getNextDay(normDayA);
            if (nextA.equalsIgnoreCase(normDayB)) {
                int spillStartA = 0;
                int spillEndA = endMinA - 1440;
                if (Math.max(spillStartA, startMinB) < Math.min(spillEndA, endMinB)) {
                    return true;
                }
            }
        }

        // 3. Event B spills over into Event A's base day
        if (endMinB > 1440) {
            String nextB = getNextDay(normDayB);
            if (nextB.equalsIgnoreCase(normDayA)) {
                int spillStartB = 0;
                int spillEndB = endMinB - 1440;
                if (Math.max(startMinA, spillStartB) < Math.min(endMinA, spillEndB)) {
                    return true;
                }
            }
        }

        // 4. Both spill over into the same next day
        if (endMinA > 1440 && endMinB > 1440) {
            String nextA = getNextDay(normDayA);
            String nextB = getNextDay(normDayB);
            if (nextA.equalsIgnoreCase(nextB)) {
                int spillEndA = endMinA - 1440;
                int spillEndB = endMinB - 1440;
                if (Math.max(0, 0) < Math.min(spillEndA, spillEndB)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isTooClose(String dayA, String startA, String endA,
                                     String dayB, String startB, String endB, int bufferMinutes) {
        if (!dayA.equalsIgnoreCase(dayB)) { return false; }
        if (hasConflict(dayA, startA, endA, dayB, startB, endB)) return false;

        int startMinA = timeToMinutes(startA);
        int endMinA = timeToMinutes(endA);
        if (endMinA <= startMinA) endMinA += 1440;

        int startMinB = timeToMinutes(startB);
        int endMinB = timeToMinutes(endB);
        if (endMinB <= startMinB) endMinB += 1440;

        int gap1 = startMinB - endMinA;
        int gap2 = startMinA - endMinB;

        return (gap1 > 0 && gap1 <= bufferMinutes) || (gap2 > 0 && gap2 <= bufferMinutes);
    }
}
