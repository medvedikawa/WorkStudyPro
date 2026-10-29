package com.example.wspro;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ScheduleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    List<Long> insertAll(List<ScheduleItem> schedules);

    @Insert
    long insert(ScheduleItem schedule);

    @Update
    void update(ScheduleItem schedule);

    @Query("SELECT * FROM schedule_table WHERE day = :selectedDay AND isDeleted = 0")
    List<ScheduleItem> getAllSchedules(String selectedDay);

    @Query("SELECT * FROM schedule_table WHERE day = :selectedDay AND isDeleted = 0")
    LiveData<List<ScheduleItem>> getAllSchedulesLiveData(String selectedDay);

    @Query("SELECT * FROM schedule_table WHERE isDeleted = 0")
    List<ScheduleItem> getAll();

    @Query("SELECT * FROM schedule_table WHERE isDeleted = 0")
    LiveData<List<ScheduleItem>> getAllLiveData();

    @Query("SELECT * FROM schedule_table WHERE isWork = :isWork AND isDeleted = 0 "
            + "ORDER BY CASE day WHEN 'MON' THEN 1 WHEN 'TUE' THEN 2 WHEN 'WED' THEN 3 "
            + "WHEN 'THU' THEN 4 WHEN 'FRI' THEN 5 WHEN 'SAT' THEN 6 WHEN 'SUN' THEN 7 ELSE 8 END, startTime")
    List<ScheduleItem> getSchedulesByWork(boolean isWork);

    @Query("SELECT * FROM schedule_table")
    List<ScheduleItem> getAllIncludingDeleted();

    @Query("SELECT * FROM schedule_table WHERE cloudId = :cloudId LIMIT 1")
    ScheduleItem findByCloudId(String cloudId);

    @Query("SELECT * FROM schedule_table WHERE id = :id LIMIT 1")
    ScheduleItem findById(int id);

    @Query("UPDATE schedule_table SET isDeleted = 1 WHERE id = :id")
    void markDeleted(int id);

    @Query("UPDATE schedule_table SET isDeleted = 1 WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM schedule_table WHERE id = :id")
    void deletePermanently(int id);

    @Query("DELETE FROM schedule_table")
    void deleteAllSchedules();
}
