package com.example.wspro;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "schedule_table")
public class ScheduleItem {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String subject;
    private String day;       // e.g., "WED"
    private String startTime; // e.g., "14:00"
    private String endTime;   // e.g., "20:00"
    private boolean isWork;   // true for work shift, false for class

    public ScheduleItem(String subject, String day, String startTime, String endTime, boolean isWork) {
        this.subject = subject;
        this.day = day;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isWork = isWork;
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
}