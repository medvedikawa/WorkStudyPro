package com.example.wspro;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "assignment_table", indices = {@Index(value = {"cloudId"}, unique = true)})
public class AssignmentItem {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String title;
    private String subject;
    private String category; // "Task Performance", "Group Work", "Laboratory", "Assignment"
    private String dueDate;   // "YYYY-MM-DD" e.g. "2026-10-28"
    private String dueTime;   // "11:59 PM"
    private long dueTimestamp;
    private boolean isCompleted;
    private String cloudId;
    private boolean isDeleted;

    @Ignore
    private double priorityScore;
    @Ignore
    private String priorityReason;
    @Ignore
    private boolean hasWorkCollision;

    public AssignmentItem(String title, String subject, String category, String dueDate, String dueTime, long dueTimestamp, boolean isCompleted) {
        this.title = title;
        this.subject = subject;
        this.category = category;
        this.dueDate = dueDate;
        this.dueTime = dueTime;
        this.dueTimestamp = dueTimestamp;
        this.isCompleted = isCompleted;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public String getDueTime() { return dueTime; }
    public void setDueTime(String dueTime) { this.dueTime = dueTime; }

    public long getDueTimestamp() { return dueTimestamp; }
    public void setDueTimestamp(long dueTimestamp) { this.dueTimestamp = dueTimestamp; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }

    public double getPriorityScore() { return priorityScore; }
    public void setPriorityScore(double priorityScore) { this.priorityScore = priorityScore; }

    public String getPriorityReason() { return priorityReason; }
    public void setPriorityReason(String priorityReason) { this.priorityReason = priorityReason; }

    public boolean isHasWorkCollision() { return hasWorkCollision; }
    public void setHasWorkCollision(boolean hasWorkCollision) { this.hasWorkCollision = hasWorkCollision; }
    public String getCloudId() { return cloudId; }
    public void setCloudId(String cloudId) { this.cloudId = cloudId; }
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}
