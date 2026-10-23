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
    private String category;   // Changed from boolean comment to: e.g., "Class", "Work", or "Other"

    public ScheduleItem(String subject, String day, String startTime, String endTime, String category) {
        this.subject = subject;
        this.day = day;
        this.startTime = startTime;
        this.endTime = endTime;
        this.category = category;
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
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean isWork() { return "Work".equalsIgnoreCase(category); }
}