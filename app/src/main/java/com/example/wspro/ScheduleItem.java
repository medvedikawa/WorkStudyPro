package com.example.wspro;

public class ScheduleItem {
    private String title;
    private String day;       // e.g., "WED"
    private String startTime; // e.g., "14:00"
    private String endTime;   // e.g., "20:00"
    private boolean isWork;   // true for work shift, false for class

    public ScheduleItem(String title, String day, String startTime, String endTime, boolean isWork) {
        this.title = title;
        this.day = day;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isWork = isWork;
    }

    public String getTitle() { return title; }
    public String getDay() { return day; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public boolean isWork() { return isWork; }
}