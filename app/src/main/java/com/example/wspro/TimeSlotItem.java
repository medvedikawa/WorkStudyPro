package com.example.wspro;

import java.util.HashSet;
import java.util.Set;

public class TimeSlotItem {
    private int id;
    private String subject;
    private String day;
    private String startTime;
    private String endTime;
    private boolean isWork;
    private String colorHex;
    private String subjectError;
    private String startTimeError;
    private String endTimeError;
    private Set<ScheduleTableParser.Flag> flags = new HashSet<>();

    public TimeSlotItem(String subject, String day, String startTime, String endTime, boolean isWork, String colorHex) {
        this.subject = subject;
        this.day = day;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isWork = isWork;
        this.colorHex = colorHex;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getDay() { return day; }
    public void setDay(String day) { this.day = day; }
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
}
