package com.example.wspro;

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
}
