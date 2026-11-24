package com.example.wspro;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class PriorityEngine {

    public static List<AssignmentItem> calculateAndSort(List<AssignmentItem> assignments,
                                                         List<ScheduleItem> schedules) {
        return calculateAndSort(assignments, schedules, AlgorithmConfig.defaults());
    }

    public static List<AssignmentItem> calculateAndSort(List<AssignmentItem> assignments,
                                                         List<ScheduleItem> schedules,
                                                         AlgorithmConfig config) {
        if (assignments == null) return new ArrayList<>();
        AlgorithmConfig effectiveConfig = config != null ? config : AlgorithmConfig.defaults();

        List<AssignmentItem> result = new ArrayList<>(assignments);
        long now = System.currentTimeMillis();

        for (AssignmentItem item : result) {
            if (item.isWorkTask()) {
                item.setHasWorkCollision(false);
                item.setPriorityScore(0);
                item.setPriorityReason(null);
                continue;
            }

            double score = 0;
            StringBuilder reasonBuilder = new StringBuilder();

            String category = item.getCategory() != null ? item.getCategory().trim() : "";
            int categoryWeight;
            if (category.equalsIgnoreCase("Task Performance")) {
                categoryWeight = effectiveConfig.getWeight(AlgorithmConfig.TASK_PERFORMANCE);
                reasonBuilder.append("Major Task Performance");
            } else if (category.equalsIgnoreCase("Group Work")) {
                categoryWeight = effectiveConfig.getWeight(AlgorithmConfig.GROUP_WORK);
                reasonBuilder.append("Group Project Coordination");
            } else if (category.equalsIgnoreCase("Laboratory")) {
                categoryWeight = effectiveConfig.getWeight(AlgorithmConfig.LABORATORY);
                reasonBuilder.append("Hands-on Laboratory");
            } else {
                categoryWeight = effectiveConfig.getWeight(AlgorithmConfig.ASSIGNMENT);
                reasonBuilder.append("Academic Assignment");
            }
            score += categoryWeight;

            long diffMillis = item.getDueTimestamp() - now;
            long diffHours = diffMillis / (1000 * 60 * 60);
            double deadlineFactor;
            if (diffMillis < 0) {
                deadlineFactor = 1.0;
                reasonBuilder.insert(0, "Overdue • ");
            } else if (diffHours <= 12) {
                deadlineFactor = 0.85;
                reasonBuilder.insert(0, "Due in under 12 hrs • ");
            } else if (diffHours <= 24) {
                deadlineFactor = 0.65;
                reasonBuilder.insert(0, "Due in under 24 hrs • ");
            } else if (diffHours <= 48) {
                deadlineFactor = 0.45;
                reasonBuilder.insert(0, "Due in 2 days • ");
            } else if (diffHours <= 72) {
                deadlineFactor = 0.30;
                reasonBuilder.insert(0, "Due in 3 days • ");
            } else {
                deadlineFactor = 0.10;
                reasonBuilder.insert(0, "Due on " + item.getDueDate() + " • ");
            }
            score += effectiveConfig.getWeight(AlgorithmConfig.DEADLINE) * deadlineFactor;

            // 3. Work Shift Collision on Due Date
            boolean collision = false;
            String dueDayCode = getDayCodeFromTimestamp(item.getDueTimestamp());

            if (schedules != null) {
                for (ScheduleItem s : schedules) {
                    if (s.isWork() && s.getDay() != null && s.getDay().equalsIgnoreCase(dueDayCode)) {
                        collision = true;
                        score += effectiveConfig.getWeight(AlgorithmConfig.WORK_SHIFT_COLLISION);
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
            if (a.isWorkTask() != b.isWorkTask()) {
                return a.isWorkTask() ? 1 : -1;
            }
            if (a.isWorkTask()) {
                return Long.compare(a.getDueTimestamp(), b.getDueTimestamp());
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
