package com.example.wspro;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "notifications",
        indices = {@Index(value = {"dedupeKey"}, unique = true)}
)
public class NotificationItem {

    public static final String TYPE_DEADLINE = "deadline";
    public static final String TYPE_CONFLICT = "conflict";
    public static final String TYPE_WORKLOAD = "workload";

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String type;
    private String title;
    private String message;
    private long createdAt;
    private boolean isRead;
    private String relatedId;
    private String dedupeKey;

    public NotificationItem() {
    }

    @Ignore
    public NotificationItem(String type, String title, String message, long createdAt, boolean isRead, String relatedId, String dedupeKey) {
        this.type = type;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt;
        this.isRead = isRead;
        this.relatedId = relatedId;
        this.dedupeKey = dedupeKey;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public String getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(String relatedId) {
        this.relatedId = relatedId;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }
}
