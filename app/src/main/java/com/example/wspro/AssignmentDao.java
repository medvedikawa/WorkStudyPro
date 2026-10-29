package com.example.wspro;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AssignmentDao {

    @Insert
    long insert(AssignmentItem assignment);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    List<Long> insertAll(List<AssignmentItem> assignments);

    @Update
    void update(AssignmentItem assignment);

    @Delete
    void delete(AssignmentItem assignment);

    @Query("SELECT * FROM assignment_table WHERE isDeleted = 0 ORDER BY isCompleted ASC, dueTimestamp ASC")
    List<AssignmentItem> getAllAssignments();

    @Query("SELECT * FROM assignment_table WHERE isDeleted = 0 ORDER BY isCompleted ASC, dueTimestamp ASC")
    LiveData<List<AssignmentItem>> getAllAssignmentsLiveData();

    @Query("SELECT * FROM assignment_table WHERE isDeleted = 0 AND isCompleted = 0 ORDER BY dueTimestamp ASC")
    List<AssignmentItem> getPendingAssignments();

    @Query("SELECT * FROM assignment_table WHERE isDeleted = 0 AND isCompleted = 0 ORDER BY dueTimestamp ASC")
    LiveData<List<AssignmentItem>> getPendingAssignmentsLiveData();

    @Query("SELECT * FROM assignment_table")
    List<AssignmentItem> getAllIncludingDeleted();

    @Query("SELECT * FROM assignment_table WHERE cloudId = :cloudId LIMIT 1")
    AssignmentItem findByCloudId(String cloudId);

    @Query("SELECT * FROM assignment_table WHERE id = :id LIMIT 1")
    AssignmentItem findById(int id);

    @Query("UPDATE assignment_table SET isDeleted = 1 WHERE id = :id")
    void markDeleted(int id);

    @Query("DELETE FROM assignment_table WHERE id = :id")
    void deletePermanently(int id);

    @Query("DELETE FROM assignment_table")
    void deleteAllAssignments();
}
