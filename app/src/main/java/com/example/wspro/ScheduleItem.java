package com.example.wspro;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "schedule_table", indices = {@Index(value = {"cloudId"}, unique = true)})
public class ScheduleItem {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String subject;
    private String day;       // e.g., "WED"
    private String startTime; // e.g., "14:00"
    private String endTime;   // e.g., "20:00"
    private boolean isWork;   // true for work shift, false for class
    private String colorHex;  // Hex color code e.g., "#4F46E5"
    private String cloudId;
    private boolean isDeleted;

    public ScheduleItem(String subject, String day, String startTime, String endTime, boolean isWork, String colorHex) {
        this.subject = subject;
        this.day = day;
        this.startTime = startTime;
        this.endTime = endTime;
        this.isWork = isWork;
        this.colorHex = (colorHex != null && !colorHex.isEmpty()) ? colorHex : (isWork ? "#EA580C" : "#4F46E5");
    }

    @Ignore
    public ScheduleItem(String subject, String day, String startTime, String endTime, boolean isWork) {
        this(subject, day, startTime, endTime, isWork, isWork ? "#EA580C" : "#4F46E5");
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
    public void setWork(boolean work) { isWork = work; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public String getCloudId() { return cloudId; }
    public void setCloudId(String cloudId) { this.cloudId = cloudId; }
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}
