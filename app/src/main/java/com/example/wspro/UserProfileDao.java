package com.example.wspro;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE userId = :userId LIMIT 1")
    UserProfile getUserProfile(String userId);

    @Query("SELECT * FROM user_profile WHERE userId = :userId LIMIT 1")
    LiveData<UserProfile> getUserProfileLiveData(String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(UserProfile profile);

    @Update
    void update(UserProfile profile);

    @Query("DELETE FROM user_profile WHERE userId = :userId")
    void deleteByUserId(String userId);

    @Query("DELETE FROM user_profile")
    void deleteAll();
}
