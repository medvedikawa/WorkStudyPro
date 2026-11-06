package com.example.wspro;

import java.util.List;
import java.util.Locale;

public final class ScheduleConflictText {
    private ScheduleConflictText() {
    }

    public static String format(ScheduleItem first, ScheduleItem second) {
        return first.getDay() + ": " + first.getSubject() + " (" + first.getStartTime() + " - "
                + first.getEndTime() + ") overlaps " + second.getSubject() + " ("
                + second.getStartTime() + " - " + second.getEndTime() + ")";
    }

    public static String counterpart(String conflict, String subject) {
        int delimiter = conflict.indexOf(": ");
        int overlaps = conflict.indexOf(" overlaps ");
        if (delimiter < 0 || overlaps < 0) return null;
        String first = conflict.substring(delimiter + 2, overlaps);
        String second = conflict.substring(overlaps + " overlaps ".length());
        int firstTimes = first.indexOf(" (");
        int secondTimes = second.indexOf(" (");
        String firstName = firstTimes < 0 ? first : first.substring(0, firstTimes);
        String secondName = secondTimes < 0 ? second : second.substring(0, secondTimes);
        if (firstName.equals(subject)) return secondName;
        if (secondName.equals(subject)) return firstName;
        return null;
    }

    public static String joinDisplayConflicts(List<String> conflicts) {
        StringBuilder text = new StringBuilder();
        for (String conflict : conflicts) {
            if (text.length() > 0) text.append('\n');
            text.append("• ").append(displayConflict(conflict));
        }
        return text.toString();
    }

    public static String displayConflict(String conflict) {
        int delimiter = conflict.indexOf(": ");
        int overlaps = conflict.indexOf(" overlaps ");
        if (delimiter < 0 || overlaps < 0) return conflict;
        String day = displayDay(conflict.substring(0, delimiter));
        String first = conflict.substring(delimiter + 2, overlaps);
        String second = conflict.substring(overlaps + " overlaps ".length());
        int firstTimes = first.indexOf(" (");
        int secondTimes = second.indexOf(" (");
        if (firstTimes < 0 || secondTimes < 0) return conflict;
        String names = first.substring(0, firstTimes) + " and " + second.substring(0, secondTimes);
        String range = first.substring(firstTimes + 2).replace(")", "");
        String[] times = range.split(" - ", 2);
        return times.length == 2
                ? names + " overlap on " + day + ", " + times[0] + " to " + times[1]
                : conflict;
    }

    public static String displayDay(String day) {
        if (day == null || day.isEmpty()) return day;
        return day.substring(0, 1) + day.substring(1).toLowerCase(Locale.US);
    }
}
