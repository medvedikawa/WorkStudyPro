package com.example.wspro;

import androidx.room.Entity;
import androidx.room.ColumnInfo;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "assignment_table", indices = {@Index(value = {"cloudId"}, unique = true)})
public class AssignmentItem {
    public static final String CATEGORY_SCHOOL = "School";
    public static final String CATEGORY_WORK = "Work";
    public static final String CATEGORY_OTHER = "Other";

    public static final String TYPE_SCHOOL = "SCHOOL";
    public static final String TYPE_WORK = "WORK";

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String title;
    private String subject;
    private String category; // Stores base category + "|" + importance (e.g. "School|High")
    private String dueDate;   // "YYYY-MM-DD" e.g. "2026-10-28"
    private String dueTime;   // "11:59 PM"
    private long dueTimestamp;
    private boolean isCompleted;
    private String cloudId;
    private boolean isDeleted;
    @ColumnInfo(defaultValue = "'School'")
    private String taskCategory = CATEGORY_SCHOOL; // "School", "Work", "Other"

    // Reminder & Recurrence fields
    private boolean hasReminder;
    private String reminderTime; // e.g. "17:00"
    private long reminderTimestamp;
    private boolean isRecurring;
    private String recurringDays; // e.g. "THU,FRI"

    @Ignore
    private boolean isSelected;

    @Ignore
    private double priorityScore;
    @Ignore
    private String priorityReason;
    @Ignore
    private boolean hasWorkCollision;

    @Ignore
    public AssignmentItem(String title, String subject, String category, String dueDate, String dueTime, long dueTimestamp, boolean isCompleted) {
        this(title, subject, category, dueDate, dueTime, dueTimestamp, isCompleted, CATEGORY_SCHOOL);
    }

    public AssignmentItem(String title, String subject, String category, String dueDate, String dueTime,
                          long dueTimestamp, boolean isCompleted, String taskCategory) {
        this.title = title;
        this.subject = subject;
        setCategory(category);
        this.dueDate = dueDate;
        this.dueTime = dueTime;
        this.dueTimestamp = dueTimestamp;
        this.isCompleted = isCompleted;
        setTaskCategory(taskCategory);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getRawCategory() {
        return category;
    }

    public void setRawCategory(String category) {
        this.category = category;
    }

    public String getCategory() {
        if (category != null && category.contains("|")) {
            return category.split("\\|")[0].trim();
        }
        if (category != null && !category.isEmpty()) {
            if (category.equalsIgnoreCase("Task Performance") || category.equalsIgnoreCase("Group Work")
                    || category.equalsIgnoreCase("Laboratory") || category.equalsIgnoreCase("Assignment")) {
                return CATEGORY_SCHOOL;
            }
            return category;
        }
        return CATEGORY_SCHOOL;
    }

    public void setCategory(String category) {
        if (category != null && category.contains("|")) {
            this.category = category;
            return;
        }
        String importance = getImportance();
        String base = category != null && !category.isEmpty() ? category : CATEGORY_SCHOOL;
        this.category = base + "|" + importance;
    }

    public String getImportance() {
        if (category != null && category.contains("|")) {
            String[] parts = category.split("\\|");
            if (parts.length > 1) {
                String imp = parts[1].trim();
                if (imp.equalsIgnoreCase("High") || imp.equalsIgnoreCase("Mid") || imp.equalsIgnoreCase("Low")) {
                    return imp;
                }
            }
        }
        return PriorityEngine.IMPORTANCE_MID;
    }

    public void setImportance(String importance) {
        String base = getCategory();
        String imp = (importance != null && !importance.isEmpty()) ? importance : PriorityEngine.IMPORTANCE_MID;
        this.category = base + "|" + imp;
    }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public String getDueTime() { return dueTime; }
    public void setDueTime(String dueTime) { this.dueTime = dueTime; }

    public long getDueTimestamp() { return dueTimestamp; }
    public void setDueTimestamp(long dueTimestamp) { this.dueTimestamp = dueTimestamp; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean isCompleted) { this.isCompleted = isCompleted; }

    public double getPriorityScore() { return priorityScore; }
    public void setPriorityScore(double priorityScore) { this.priorityScore = priorityScore; }

    public String getPriorityReason() { return priorityReason; }
    public void setPriorityReason(String priorityReason) { this.priorityReason = priorityReason; }

    public boolean isHasWorkCollision() { return hasWorkCollision; }
    public void setHasWorkCollision(boolean hasWorkCollision) { this.hasWorkCollision = hasWorkCollision; }

    public String getCloudId() { return cloudId; }
    public void setCloudId(String cloudId) { this.cloudId = cloudId; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean isDeleted) { this.isDeleted = isDeleted; }

    public String getTaskCategory() {
        if (taskCategory != null && (taskCategory.equalsIgnoreCase(CATEGORY_WORK) || taskCategory.equalsIgnoreCase("WORK"))) {
            return CATEGORY_WORK;
        } else if (taskCategory != null && (taskCategory.equalsIgnoreCase(CATEGORY_OTHER) || taskCategory.equalsIgnoreCase("OTHER"))) {
            return CATEGORY_OTHER;
        }
        return CATEGORY_SCHOOL;
    }

    public void setTaskCategory(String taskCategory) {
        if (taskCategory != null) {
            if (taskCategory.equalsIgnoreCase(CATEGORY_WORK) || taskCategory.equalsIgnoreCase("WORK")) {
                this.taskCategory = CATEGORY_WORK;
            } else if (taskCategory.equalsIgnoreCase(CATEGORY_OTHER) || taskCategory.equalsIgnoreCase("OTHER")) {
                this.taskCategory = CATEGORY_OTHER;
            } else {
                this.taskCategory = CATEGORY_SCHOOL;
            }
        } else {
            this.taskCategory = CATEGORY_SCHOOL;
        }
    }

    public String getTaskType() {
        return isWorkTask() ? TYPE_WORK : TYPE_SCHOOL;
    }

    public void setTaskType(String taskType) {
        if (TYPE_WORK.equalsIgnoreCase(taskType)) {
            setTaskCategory(CATEGORY_WORK);
        } else {
            setTaskCategory(CATEGORY_SCHOOL);
        }
    }

    public boolean isWorkTask() {
        return CATEGORY_WORK.equalsIgnoreCase(taskCategory) || CATEGORY_WORK.equalsIgnoreCase(getCategory());
    }

    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }

    public boolean isHasReminder() { return hasReminder; }
    public void setHasReminder(boolean hasReminder) { this.hasReminder = hasReminder; }

    public String getReminderTime() { return reminderTime; }
    public void setReminderTime(String reminderTime) { this.reminderTime = reminderTime; }

    public long getReminderTimestamp() { return reminderTimestamp; }
    public void setReminderTimestamp(long reminderTimestamp) { this.reminderTimestamp = reminderTimestamp; }

    public boolean isRecurring() { return isRecurring; }
    public void setRecurring(boolean recurring) { isRecurring = recurring; }

    public String getRecurringDays() { return recurringDays; }
    public void setRecurringDays(String recurringDays) { this.recurringDays = recurringDays; }
}
