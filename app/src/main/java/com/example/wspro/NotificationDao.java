package com.example.wspro;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(NotificationItem notification);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    List<Long> insertAll(List<NotificationItem> notifications);

    @Update
    void update(NotificationItem notification);

    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    LiveData<List<NotificationItem>> getAllNotificationsLiveData();

    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    List<NotificationItem> getAllNotificationsSync();

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    LiveData<Integer> getUnreadCountLiveData();

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    int getUnreadCountSync();

    @Query("SELECT COUNT(*) > 0 FROM notifications WHERE dedupeKey = :dedupeKey LIMIT 1")
    boolean existsByDedupeKey(String dedupeKey);

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    void markAsRead(int id);

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    void markAllAsRead();

    @Query("DELETE FROM notifications WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM notifications")
    void clearAll();
}
