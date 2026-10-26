package com.example.wspro;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SyncChangeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void enqueue(SyncChange change);

    @Query("SELECT * FROM pending_sync_changes ORDER BY queuedAt, id")
    List<SyncChange> getPendingChanges();

    @Query("SELECT COUNT(*) FROM pending_sync_changes")
    int getPendingCount();

    @Query("DELETE FROM pending_sync_changes WHERE id = :changeId")
    void remove(long changeId);
}
