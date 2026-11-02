package com.example.wspro;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class TimeSlotItem {
    private static final AtomicLong NEXT_LOCAL_ID = new AtomicLong(-2);

    private int id;
    private long localId = NEXT_LOCAL_ID.getAndDecrement();
    private String subject;
    private String day;
    private String originalDay;
    private final Set<String> days = new LinkedHashSet<>();
    private String startTime;
    private String endTime;
    private boolean isWork;
    private String colorHex;
    private String groupId;
    private String subjectError;
    private String startTimeError;
    private String endTimeError;
    private String scheduleError;
    private Set<ScheduleTableParser.Flag> flags = new HashSet<>();

    public TimeSlotItem(String subject, String day, String startTime, String endTime, boolean isWork, String colorHex) {
        this.subject = subject;
        this.day = day;
        if (day != null && !day.trim().isEmpty()) {
            this.days.add(EditScheduleActivity.normalizeDay(day));
        }
        this.originalDay = this.day;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isWork = isWork;
        this.colorHex = colorHex;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public long getStableId() { return id > 0 ? id : localId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getDay() { return day; }
    public void setDay(String day) {
        this.day = day;
        this.days.clear();
        if (day != null && !day.trim().isEmpty()) this.days.add(EditScheduleActivity.normalizeDay(day));
    }
    public String getOriginalDay() { return originalDay; }
    public void setOriginalDay(String originalDay) {
        this.originalDay = EditScheduleActivity.normalizeDay(originalDay);
    }
    public Set<String> getDays() { return days; }
    public void setDays(Set<String> days) {
        this.days.clear();
        if (days != null) {
            for (String value : days) {
                String normalized = EditScheduleActivity.normalizeDay(value);
                if (!normalized.isEmpty()) this.days.add(normalized);
            }
        }
        this.day = this.days.isEmpty() ? "" : this.days.iterator().next();
    }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public boolean isWork() { return isWork; }
    public void setWork(boolean work) { this.isWork = work; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public String getSubjectError() { return subjectError; }
    public void setSubjectError(String subjectError) { this.subjectError = subjectError; }
    public String getStartTimeError() { return startTimeError; }
    public void setStartTimeError(String startTimeError) { this.startTimeError = startTimeError; }
    public String getEndTimeError() { return endTimeError; }
    public void setEndTimeError(String endTimeError) { this.endTimeError = endTimeError; }
    public String getScheduleError() { return scheduleError; }
    public void setScheduleError(String scheduleError) { this.scheduleError = scheduleError; }

    public Set<ScheduleTableParser.Flag> getFlags() { return flags; }
    public void setFlags(Set<ScheduleTableParser.Flag> flags) {
        this.flags = flags != null ? flags : new HashSet<>();
    }

    public String getFlagHintText() {
        if (flags == null || flags.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (ScheduleTableParser.Flag flag : flags) {
            String msg = null;
            switch (flag) {
                case DAY_MISSING:
                    msg = "Check this day";
                    break;
                case DAY_AMBIGUOUS:
                    msg = "Check day assignment";
                    break;

                case SUBJECT_GUESSED:
                case SUBJECT_UNUSUAL:
                case SUBJECT_MISSING:
                    msg = "Check this subject";
                    break;

                case TIME_AMBIGUOUS:
                    msg = "Check start/end time";
                    break;

                case END_BEFORE_START:
                    msg = "End time is before start time";
                    break;

                case OVERNIGHT:
                    msg = "Overnight shift detected";
                    break;

                case DURATION_OVER_8H:
                    msg = "Duration over 8 hours";
                    break;

                case OVERLAPS_ANOTHER:
                    msg = "Overlaps with another class";
                    break;

                case UNITS_MISMATCH:
                    msg = "Units mismatch detected";
                    break;
            }
            if (msg != null) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append("⚠️ ").append(msg);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    public TimeSlotItem copy() {
        TimeSlotItem copy = new TimeSlotItem(subject, day, startTime, endTime, isWork, colorHex);
        copy.id = id;
        copy.localId = localId;
        copy.originalDay = originalDay;
        copy.setDays(days);
        copy.groupId = groupId;
        copy.subjectError = subjectError;
        copy.startTimeError = startTimeError;
        copy.endTimeError = endTimeError;
        copy.scheduleError = scheduleError;
        copy.flags = new HashSet<>(flags);
        return copy;
    }
}
