package com.example.wspro;

public class TimeSlotItem {
    private String subject;
    private String day;
    private String startTime;
    private String endTime;

    public TimeSlotItem(String subject, String day, String startTime, String endTime) {
        this.subject = subject;
        this.day = day;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getSubject(){return subject;}
    public void setSubject(String subject){this.subject = subject;}
    public String getDay() { return day; }
    public void setDay(String day) { this.day = day; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}