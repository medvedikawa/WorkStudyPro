package com.example.wspro;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "pending_sync_changes",
        indices = {@Index(value = {"entityType", "localId"}, unique = true)})
public class SyncChange {

    public static final String SCHEDULE = "schedule";
    public static final String ASSIGNMENT = "assignment";
    public static final String UPSERT = "upsert";
    public static final String DELETE = "delete";

    @PrimaryKey(autoGenerate = true)
    private long id;
    private String entityType;
    private int localId;
    private String operation;
    private long queuedAt;
    private boolean isOffline;

    public SyncChange(String entityType, int localId, String operation, long queuedAt) {
        this.entityType = entityType;
        this.localId = localId;
        this.operation = operation;
        this.queuedAt = queuedAt;
        this.isOffline = false;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getEntityType() { return entityType; }
    public int getLocalId() { return localId; }
    public String getOperation() { return operation; }
    public long getQueuedAt() { return queuedAt; }
    public boolean isOffline() { return isOffline; }
    public void setOffline(boolean offline) { isOffline = offline; }
}
