package com.example.wspro;

import androidx.lifecycle.LiveData;
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

    @Query("SELECT COUNT(*) FROM pending_sync_changes WHERE isOffline = 1")
    int getOfflinePendingCount();

    @Query("UPDATE pending_sync_changes SET isOffline = 1 WHERE isOffline = 0")
    void markPendingAsOffline();

    @Query("SELECT COUNT(*) FROM pending_sync_changes")
    LiveData<Integer> getPendingCountLiveData();

    @Query("DELETE FROM pending_sync_changes WHERE id = :changeId")
    void remove(long changeId);

    @Query("DELETE FROM pending_sync_changes")
    void clearAll();
}
