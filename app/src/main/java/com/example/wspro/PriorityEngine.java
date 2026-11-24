package com.example.wspro;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class PriorityEngine {
    public static final String IMPORTANCE_HIGH = "High";
    public static final String IMPORTANCE_MID = "Mid";
    public static final String IMPORTANCE_LOW = "Low";

    public static final String CLASS_CRITICAL = "Critical";
    public static final String CLASS_URGENT = "Urgent";
    public static final String CLASS_NOT_URGENT = "Not Urgent";
    public static final String CLASS_MINOR = "Minor";

    public static final long URGENCY_THRESHOLD_HOURS = 48;

    public static String classifyTask(AssignmentItem item, long now) {
        long diffMillis = item.getDueTimestamp() - now;
        long diffHours = diffMillis / (1000 * 60 * 60);
        boolean isUrgent = diffMillis < 0 || diffHours <= URGENCY_THRESHOLD_HOURS;

        String importance = item.getImportance();
        if (importance == null) importance = IMPORTANCE_MID;

        if (isUrgent) {
            if (importance.equalsIgnoreCase(IMPORTANCE_HIGH) || importance.equalsIgnoreCase(IMPORTANCE_MID)) {
                return CLASS_CRITICAL;
            } else {
                return CLASS_URGENT;
            }
        } else {
            if (importance.equalsIgnoreCase(IMPORTANCE_HIGH) || importance.equalsIgnoreCase(IMPORTANCE_MID)) {
                return CLASS_NOT_URGENT;
            } else {
                return CLASS_MINOR;
            }
        }
    }

    public static int getClassificationOrder(String classification) {
        if (classification == null) return 3;
        switch (classification) {
            case CLASS_CRITICAL: return 0;
            case CLASS_URGENT: return 1;
            case CLASS_NOT_URGENT: return 2;
            case CLASS_MINOR: return 3;
            default: return 3;
        }
    }

    public static List<AssignmentItem> calculateAndSort(List<AssignmentItem> assignments,
                                                         List<ScheduleItem> schedules) {
        return calculateAndSort(assignments, schedules, AlgorithmConfig.defaults());
    }

    public static List<AssignmentItem> calculateAndSort(List<AssignmentItem> assignments,
                                                         List<ScheduleItem> schedules,
                                                         AlgorithmConfig config) {
        if (assignments == null) return new ArrayList<>();
        List<AssignmentItem> result = new ArrayList<>(assignments);
        long now = System.currentTimeMillis();

        for (AssignmentItem item : result) {
            if (item.isWorkTask()) {
                item.setHasWorkCollision(false);
                item.setPriorityScore(0);
                item.setPriorityReason(null);
                continue;
            }

            String classification = classifyTask(item, now);
            item.setPriorityScore(3 - getClassificationOrder(classification));

            StringBuilder reasonBuilder = new StringBuilder(classification);
            long diffMillis = item.getDueTimestamp() - now;
            long diffHours = diffMillis / (1000 * 60 * 60);
            if (diffMillis < 0) {
                reasonBuilder.append(" • Overdue");
            } else if (diffHours <= URGENCY_THRESHOLD_HOURS) {
                reasonBuilder.append(" • Due in ").append(diffHours).append(" hrs");
            } else {
                reasonBuilder.append(" • Due on ").append(item.getDueDate());
            }

            // Find nearest start time schedule conflict
            boolean collision = false;
            String dueDayCode = getDayCodeFromTimestamp(item.getDueTimestamp());
            ScheduleItem nearestCollision = null;
            int minTimeDiff = Integer.MAX_VALUE;
            int taskDueMin = ConflictChecker.timeToMinutes(item.getDueTime());

            if (schedules != null) {
                for (ScheduleItem s : schedules) {
                    if (s.isWork() && s.getDay() != null && s.getDay().equalsIgnoreCase(dueDayCode)) {
                        if (ConflictChecker.hasConflict(dueDayCode, item.getDueTime(), item.getDueTime(), s.getDay(), s.getStartTime(), s.getEndTime())) {
                            int schedStartMin = ConflictChecker.timeToMinutes(s.getStartTime());
                            int diff = Math.abs(schedStartMin - taskDueMin);
                            if (diff < minTimeDiff) {
                                minTimeDiff = diff;
                                nearestCollision = s;
                                collision = true;
                            }
                        }
                    }
                }
            }

            if (collision && nearestCollision != null) {
                reasonBuilder.append(" • Nearest work conflict: ").append(nearestCollision.getSubject())
                        .append(" (").append(nearestCollision.getStartTime()).append(" - ").append(nearestCollision.getEndTime()).append(")");
            }

            item.setHasWorkCollision(collision);
            item.setPriorityReason(reasonBuilder.toString());
        }

        Collections.sort(result, (a, b) -> {
            if (a.isCompleted() != b.isCompleted()) {
                return a.isCompleted() ? 1 : -1;
            }
            if (a.isWorkTask() != b.isWorkTask()) {
                return a.isWorkTask() ? 1 : -1;
            }
            String classA = classifyTask(a, now);
            String classB = classifyTask(b, now);
            int orderA = getClassificationOrder(classA);
            int orderB = getClassificationOrder(classB);
            if (orderA != orderB) {
                return Integer.compare(orderA, orderB);
            }
            // Within group, earliest due date first
            return Long.compare(a.getDueTimestamp(), b.getDueTimestamp());
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
