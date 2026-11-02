package com.example.wspro;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_profile")
public class UserProfile {

    @PrimaryKey
    @NonNull
    private String userId;

    private String name;
    private String email;
    private String course;
    private String avatarPath;
    private String avatarUrl;
    private long updatedAt;

    public UserProfile(@NonNull String userId, String name, String email, String course,
                       String avatarPath, String avatarUrl, long updatedAt) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.course = course;
        this.avatarPath = avatarPath;
        this.avatarUrl = avatarUrl;
        this.updatedAt = updatedAt;
    }

    @NonNull
    public String getUserId() {
        return userId;
    }

    public void setUserId(@NonNull String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
