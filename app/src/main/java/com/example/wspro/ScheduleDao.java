package com.example.wspro;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ScheduleDao {

    @Insert
    void insertAll(List<ScheduleItem> schedules);

    @Insert
    void insert(ScheduleItem schedule);

    @Query("SELECT * FROM schedule_table WHERE day = :selectedDay ")

    List<ScheduleItem> getAllSchedules(String selectedDay);

    @Query("DELETE FROM schedule_table")
    void deleteAllSchedules();
}