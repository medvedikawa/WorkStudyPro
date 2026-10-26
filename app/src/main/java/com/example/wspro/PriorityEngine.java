package com.example.wspro;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PriorityEngine {

    public static List<AssignmentItem> calculateAndSort(List<AssignmentItem> assignments, List<ScheduleItem> schedules) {
        if (assignments == null) return new ArrayList<>();

        List<AssignmentItem> result = new ArrayList<>(assignments);
        long now = System.currentTimeMillis();

        for (AssignmentItem item : result) {
            double score = 0;
            StringBuilder reasonBuilder = new StringBuilder();

            // 1. Category Base Weight
            String cat = item.getCategory() != null ? item.getCategory().trim() : "";
            double catWeight = 15;
            if (cat.equalsIgnoreCase("Task Performance")) {
                catWeight = 40;
                reasonBuilder.append("Major Task Performance");
            } else if (cat.equalsIgnoreCase("Group Work")) {
                catWeight = 30;
                reasonBuilder.append("Group Project Coordination");
            } else if (cat.equalsIgnoreCase("Laboratory")) {
                catWeight = 25;
                reasonBuilder.append("Hands-on Laboratory");
            } else {
                catWeight = 15;
                reasonBuilder.append("Academic Assignment");
            }
            score += catWeight;

            // 2. Deadline Proximity
            long diffMillis = item.getDueTimestamp() - now;
            long diffHours = diffMillis / (1000 * 60 * 60);

            if (diffMillis < 0) {
                score += 65;
                reasonBuilder.insert(0, "Overdue • ");
            } else if (diffHours <= 12) {
                score += 50;
                reasonBuilder.insert(0, "Due in under 12 hrs • ");
            } else if (diffHours <= 24) {
                score += 38;
                reasonBuilder.insert(0, "Due in under 24 hrs • ");
            } else if (diffHours <= 48) {
                score += 24;
                reasonBuilder.insert(0, "Due in 2 days • ");
            } else if (diffHours <= 72) {
                score += 14;
                reasonBuilder.insert(0, "Due in 3 days • ");
            } else {
                score += 5;
                reasonBuilder.insert(0, "Due on " + item.getDueDate() + " • ");
            }

            // 3. Work Shift Collision on Due Date
            boolean collision = false;
            String dueDayCode = getDayCodeFromTimestamp(item.getDueTimestamp());

            if (schedules != null) {
                for (ScheduleItem s : schedules) {
                    if (s.isWork() && s.getDay() != null && s.getDay().equalsIgnoreCase(dueDayCode)) {
                        collision = true;
                        score += 35; // Significant collision boost
                        reasonBuilder.append(" • Work shift collision on ").append(dueDayCode)
                                .append(" (").append(s.getStartTime()).append(" - ").append(s.getEndTime()).append(")");
                        break;
                    }
                }
            }

            item.setHasWorkCollision(collision);
            item.setPriorityScore(score);
            item.setPriorityReason(reasonBuilder.toString());
        }

        // Sort: Incomplete tasks first sorted by Priority Score descending, completed tasks at the bottom
        Collections.sort(result, (a, b) -> {
            if (a.isCompleted() != b.isCompleted()) {
                return a.isCompleted() ? 1 : -1;
            }
            return Double.compare(b.getPriorityScore(), a.getPriorityScore());
        });

        return result;
    }

    public static String getDayCodeFromTimestamp(long timestamp) {
        if (timestamp <= 0) return "MON";
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        switch (dayOfWeek) {
            case Calendar.MONDAY: return "MON";
            case Calendar.TUESDAY: return "TUE";
            case Calendar.WEDNESDAY: return "WED";
            case Calendar.THURSDAY: return "THU";
            case Calendar.FRIDAY: return "FRI";
            case Calendar.SATURDAY: return "SAT";
            case Calendar.SUNDAY: return "SUN";
            default: return "MON";
        }
    }
}
