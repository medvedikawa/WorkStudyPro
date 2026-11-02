package com.example.wspro;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseClient {

    private static final String TAG = "SupabaseClient";
    public static final String SUPABASE_URL = "https://cbtwqpzrvfucuslvobuz.supabase.co";
    public static final String SUPABASE_ANON_KEY = "sb_publishable_P2rp7Bd_XgBu5291lzxgng_fxRuL4PP";

    private static final String PREFS_NAME = "WorkStudySupabasePrefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_COURSE = "user_course";
    private static final String KEY_USER_WORKPLACE = "user_workplace";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_HAS_UNSYNCED_CHANGES = "has_unsynced_changes";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time";
    private static final String KEY_WEEKLY_LIMIT = "weekly_hours_limit";
    private static final String KEY_ALERT_CONFLICTS = "alert_conflicts";
    private static final String KEY_ALERT_COLLISIONS = "alert_collisions";
    private static final String KEY_ALERT_BURNOUT = "alert_burnout";
    private static final String KEY_APPEARANCE = "appearance_mode";
    private static final String KEY_AVATAR_PATH = "avatar_path";
    private static final String KEY_AVATAR_URL = "avatar_url";
    private static final String AVATAR_BUCKET = "avatars";
    private static final String KEY_PROFILE_UPDATED_AT = "profile_updated_at";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static volatile SupabaseClient INSTANCE;
    private final Context context;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final SharedPreferences prefs;
    private final Handler mainHandler;

    public interface AuthCallback {
        void onSuccess(String userId, String email, String accessToken);
        void onError(String errorMessage);
    }

    public interface RecoveryCallback {
        void onSuccess(String userId, String email, String accessToken, String refreshToken);
        void onError(String errorMessage);
    }

    public interface DataCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    private SupabaseClient(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    try {
                        Response response = chain.proceed(chain.request());
                        if (!response.isSuccessful()) {
                            Log.e(TAG, "Supabase request failed: "
                                    + chain.request().method() + " "
                                    + chain.request().url().encodedPath()
                                    + " (" + response.code() + "): "
                                    + response.peekBody(4096).string());
                        }
                        return response;
                    } catch (IOException e) {
                        Log.e(TAG, "Supabase request failed: "
                                + chain.request().method() + " "
                                + chain.request().url().encodedPath(), e);
                        throw e;
                    }
                })
                .build();
        this.gson = new Gson();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static SupabaseClient getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SupabaseClient.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SupabaseClient(context);
                }
            }
        }
        return INSTANCE;
    }

    // --- NETWORK CHECK HELPER ---

    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NetworkCapabilities cap = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return cap != null && (cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    public boolean isNetworkAvailable() {
        return isNetworkAvailable(context);
    }

    // --- SESSION & PREFERENCE HELPERS ---

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false) && getAccessToken() != null;
    }

    public boolean isDemoSession() {
        return "demo_user_id".equals(getUserId());
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "");
    }

    public String getUserName() {
        return prefs.getString(userScopedKey(KEY_USER_NAME), "Working Student");
    }

    public String getUserCourse() {
        return prefs.getString(userScopedKey(KEY_USER_COURSE), "College Program");
    }

    public String getUserWorkplace() {
        return prefs.getString(userScopedKey(KEY_USER_WORKPLACE), "Part-time Worker");
    }

    public void setUserWorkplace(String workplace) {
        prefs.edit().putString(userScopedKey(KEY_USER_WORKPLACE), workplace).apply();
    }

    public void updateProfile(String name, String course, String workplace) {
        prefs.edit()
                .putString(userScopedKey(KEY_USER_NAME), name)
                .putString(userScopedKey(KEY_USER_COURSE), course)
                .putString(userScopedKey(KEY_USER_WORKPLACE), workplace)
                .apply();
    }

    public String getAvatarPath() {
        return prefs.getString(userScopedKey(KEY_AVATAR_PATH), null);
    }

    public void setAvatarPath(String path) {
        prefs.edit().putString(userScopedKey(KEY_AVATAR_PATH), path).apply();
    }

    public String getAvatarUrl() {
        return prefs.getString(userScopedKey(KEY_AVATAR_URL), null);
    }

    public void setAvatarUrl(String url) {
        prefs.edit().putString(userScopedKey(KEY_AVATAR_URL), url).apply();
    }

    public File getAvatarCacheFile() {
        String userId = getUserId();
        if (userId == null || userId.isEmpty()) return null;
        return new File(context.getFilesDir(), "profile_avatar_" + userId + ".jpg");
    }

    public void loadProfileAvatar(Context context, DataCallback<Bitmap> callback) {
        String userId = getUserId();
        String avatarUrl = getAvatarUrl();
        if (userId == null || userId.isEmpty()) {
            postDataError(callback, "A signed-in account is required to load the profile picture.");
            return;
        }

        File cacheFile = new File(context.getFilesDir(), "profile_avatar_" + userId + ".jpg");
        new Thread(() -> {
            Bitmap cached = cacheFile.exists() ? BitmapFactory.decodeFile(cacheFile.getAbsolutePath()) : null;
            if (cached != null) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(cached);
                });
                return;
            }

            if (avatarUrl == null || avatarUrl.isEmpty()) {
                postDataError(callback, "No profile picture is available.");
                return;
            }

            Request request = new Request.Builder().url(avatarUrl).get().build();
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    postDataError(callback, "Couldn't load the profile picture.");
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) {
                        postDataError(callback, "Couldn't load the profile picture ("
                                + response.code() + ").");
                        return;
                    }
                    byte[] imageBytes = response.body().bytes();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    if (bitmap == null) {
                        Log.e(TAG, "Supabase avatar response was not a readable image");
                        postDataError(callback, "The profile picture could not be decoded.");
                        return;
                    }
                    if (userId.equals(getUserId())) {
                        try (java.io.FileOutputStream output = new java.io.FileOutputStream(cacheFile)) {
                            output.write(imageBytes);
                        } catch (IOException e) {
                            Log.e(TAG, "Could not cache downloaded profile picture", e);
                        }
                        setAvatarPath(cacheFile.getAbsolutePath());
                    }
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(bitmap);
                    });
                }
            });
        }).start();
    }

    public boolean hasUnsyncedChanges() {
        return prefs.getBoolean(KEY_HAS_UNSYNCED_CHANGES, false);
    }

    public void setHasUnsyncedChanges(boolean hasChanges) {
        prefs.edit().putBoolean(KEY_HAS_UNSYNCED_CHANGES, hasChanges).apply();
    }

    public void setLastSyncTime(long timestamp) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, timestamp).apply();
    }

    public String getLastSyncTimeString() {
        long time = prefs.getLong(KEY_LAST_SYNC_TIME, 0);
        if (time <= 0) return "Not synced yet";
        return new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(new Date(time));
    }

    public int getWeeklyHoursLimit() {
        return prefs.getInt(KEY_WEEKLY_LIMIT, 45); // default 45 hours
    }

    public void setWeeklyHoursLimit(int hours) {
        prefs.edit().putInt(KEY_WEEKLY_LIMIT, hours).apply();
    }

    public boolean isAlertConflictsEnabled() {
        return prefs.getBoolean(KEY_ALERT_CONFLICTS, true);
    }

    public void setAlertConflictsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ALERT_CONFLICTS, enabled).apply();
    }

    public boolean isAlertCollisionsEnabled() {
        return prefs.getBoolean(KEY_ALERT_COLLISIONS, true);
    }

    public void setAlertCollisionsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ALERT_COLLISIONS, enabled).apply();
    }

    public boolean isAlertBurnoutEnabled() {
        return prefs.getBoolean(KEY_ALERT_BURNOUT, true);
    }

    public void setAlertBurnoutEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ALERT_BURNOUT, enabled).apply();
    }

    public String getAppearanceMode() {
        return prefs.getString(KEY_APPEARANCE, "system");
    }

    public void setAppearanceMode(String mode) {
        if (!"system".equals(mode) && !"light".equals(mode) && !"dark".equals(mode)) {
            throw new IllegalArgumentException("Unsupported appearance mode.");
        }
        prefs.edit().putString(KEY_APPEARANCE, mode).apply();
    }

    public static void applyAppearance(String mode) {
        int nightMode;
        if ("light".equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("dark".equals(mode)) {
            nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        } else {
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    public void saveSession(String accessToken, String refreshToken, String userId, String email, String name, String course) {
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_EMAIL, email)
                .remove(KEY_USER_NAME)
                .remove(KEY_USER_COURSE)
                .remove(KEY_AVATAR_PATH)
                .remove(KEY_AVATAR_URL);
        if (userId != null && !userId.isEmpty()) {
            if (name != null && !name.trim().isEmpty()) {
                editor.putString(userScopedKey(KEY_USER_NAME, userId), name);
            }
            if (course != null && !course.trim().isEmpty()) {
                editor.putString(userScopedKey(KEY_USER_COURSE, userId), course);
            }
        }
        if (refreshToken != null && !refreshToken.isEmpty()) {
            editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        } else {
            editor.remove(KEY_REFRESH_TOKEN);
        }
        editor.apply();
        if (!"demo_user_id".equals(userId) && accessToken != null && !accessToken.isEmpty()) {
            NotificationTriggerEngine.schedulePeriodicCheck(context);
        }
    }

    public void saveSession(String accessToken, String userId, String email, String name, String course) {
        saveSession(accessToken, null, userId, email, name, course);
    }

    public void clearSession() {
        SharedPreferences.Editor editor = prefs.edit()
                .remove(KEY_IS_LOGGED_IN)
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_USER_NAME)
                .remove(KEY_USER_COURSE)
                .remove(KEY_USER_WORKPLACE)
                .remove(KEY_AVATAR_PATH)
                .remove(KEY_AVATAR_URL)
                .remove(KEY_HAS_UNSYNCED_CHANGES)
                .remove(KEY_LAST_SYNC_TIME)
                .remove(KEY_WEEKLY_LIMIT)
                .remove(KEY_ALERT_CONFLICTS)
                .remove(KEY_ALERT_COLLISIONS)
                .remove(KEY_ALERT_BURNOUT);
        String[] userProfileKeys = {KEY_USER_NAME, KEY_USER_COURSE, KEY_USER_WORKPLACE,
                KEY_AVATAR_PATH, KEY_AVATAR_URL, KEY_PROFILE_UPDATED_AT};
        for (String storedKey : prefs.getAll().keySet()) {
            for (String profileKey : userProfileKeys) {
                if (storedKey.startsWith(profileKey + "_")) {
                    editor.remove(storedKey);
                    break;
                }
            }
        }
        editor.apply();
    }

    public void clearUserData(Context context, DataCallback<Boolean> callback) {
        Context appContext = context != null ? context.getApplicationContext() : this.context;
        String userId = getUserId();
        clearSession();
        NotificationTriggerEngine.cancelScheduledWork(appContext);
        NotificationHelper.cancelAll(appContext);
        appContext.getSharedPreferences("ScanPrefs", Context.MODE_PRIVATE).edit().clear().apply();
        appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().apply();

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);
                db.runInTransaction(() -> {
                    db.scheduleDao().deleteAllSchedules();
                    db.assignmentDao().deleteAllAssignments();
                    db.notificationDao().clearAll();
                    db.syncChangeDao().clearAll();
                    db.userProfileDao().deleteAll();
                });
                File cacheFile = userId == null || userId.isEmpty()
                        ? null : new File(appContext.getFilesDir(), "profile_avatar_" + userId + ".jpg");
                if (cacheFile != null && cacheFile.exists() && !cacheFile.delete()) {
                    Log.w(TAG, "Could not delete cached profile image for the logged-out account");
                }
                File[] avatarCaches = appContext.getFilesDir().listFiles((directory, name) ->
                        name.startsWith("profile_avatar_") && name.endsWith(".jpg"));
                if (avatarCaches != null) {
                    for (File avatarCache : avatarCaches) {
                        if (avatarCache.exists() && !avatarCache.delete()) {
                            Log.w(TAG, "Could not delete a cached profile image");
                        }
                    }
                }
                File legacyCacheFile = new File(appContext.getFilesDir(), "profile_avatar_user.jpg");
                if (legacyCacheFile.exists() && !legacyCacheFile.delete()) {
                    Log.w(TAG, "Could not delete legacy profile image cache");
                }
                NotificationHelper.cancelAll(appContext);
                mainHandler.post(() -> {
                    if (callback != null) callback.onSuccess(true);
                });
            } catch (Exception e) {
                Log.e(TAG, "Could not clear local account data", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Couldn't clear local account data.");
                });
            }
        }).start();
    }

    // --- AUTHENTICATION: SIGN UP ---

    public void signUp(String email, String password, String name, String course, AuthCallback callback) {
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("email", email.trim());
        bodyJson.addProperty("password", password);

        JsonObject userMetadata = new JsonObject();
        if (name != null && !name.trim().isEmpty()) {
            userMetadata.addProperty("full_name", name.trim());
        }
        if (course != null && !course.trim().isEmpty()) {
            userMetadata.addProperty("course", course.trim());
        }
        bodyJson.add("data", userMetadata);

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/signup")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(bodyJson.toString(), JSON))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                postError(callback, "Couldn't connect. Please check your internet connection.");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                        String accessToken = json.has("access_token") && !json.get("access_token").isJsonNull()
                                ? json.get("access_token").getAsString() : "";

                        String refreshToken = json.has("refresh_token") && !json.get("refresh_token").isJsonNull()
                                ? json.get("refresh_token").getAsString() : null;

                        String userId = "";
                        String userEmail = email;
                        if (json.has("user") && json.get("user").isJsonObject()) {
                            JsonObject userObj = json.getAsJsonObject("user");
                            userId = userObj.has("id") ? userObj.get("id").getAsString() : "";
                            if (userObj.has("email")) {
                                userEmail = userObj.get("email").getAsString();
                            }
                        }

                        final String finalToken = accessToken;
                        final String finalRefresh = refreshToken;
                        final String finalId = userId;
                        final String finalEmail = userEmail;

                        saveSession(finalToken, finalRefresh, finalId, finalEmail, name, course);
                        postSuccess(callback, finalId, finalEmail, finalToken);
                    } catch (Exception e) {
                        postError(callback, "Couldn't finish creating your account. Please try again.");
                    }
                } else {
                    postError(callback, signUpError(response.code(), responseBody));
                }
            }
        });
    }

    // --- AUTHENTICATION: SIGN IN ---

    public void signIn(String email, String password, AuthCallback callback) {
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("email", email.trim());
        bodyJson.addProperty("password", password);

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/token?grant_type=password")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(bodyJson.toString(), JSON))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                postError(callback, "Couldn't connect. Please check your internet connection.");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                        String accessToken = json.has("access_token") ? json.get("access_token").getAsString() : "";
                        String refreshToken = json.has("refresh_token") && !json.get("refresh_token").isJsonNull()
                                ? json.get("refresh_token").getAsString() : null;

                        String userId = "";
                        String userEmail = email;
                        String userName = "";
                        String userCourse = "";

                        if (json.has("user") && json.get("user").isJsonObject()) {
                            JsonObject userObj = json.getAsJsonObject("user");
                            userId = userObj.has("id") ? userObj.get("id").getAsString() : "";
                            if (userObj.has("email")) {
                                userEmail = userObj.get("email").getAsString();
                            }
                            if (userObj.has("user_metadata") && userObj.get("user_metadata").isJsonObject()) {
                                JsonObject meta = userObj.getAsJsonObject("user_metadata");
                                if (meta.has("full_name")) userName = meta.get("full_name").getAsString();
                                if (meta.has("course")) userCourse = meta.get("course").getAsString();
                            }
                        }

                        final String finalToken = accessToken;
                        final String finalRefresh = refreshToken;
                        final String finalId = userId;
                        final String finalEmail = userEmail;
                        final String finalName = userName;
                        final String finalCourse = userCourse;

                        saveSession(finalToken, finalRefresh, finalId, finalEmail, finalName, finalCourse);
                        postSuccess(callback, finalId, finalEmail, finalToken);
                    } catch (Exception e) {
                        postError(callback, "Couldn't sign you in right now. Please try again.");
                    }
                } else {
                    postError(callback, signInError(response.code()));
                }
            }
        });
    }

    // --- AUTHENTICATION: FORGOT PASSWORD / PASSWORD RECOVERY ---

    public void resetPassword(String email, AuthCallback callback) {
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("email", email.trim());

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/recover")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(bodyJson.toString(), JSON))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                postError(callback, "Couldn't connect. Please check your internet connection.");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    postSuccess(callback, "", email, "");
                } else {
                    postError(callback, recoveryError(response.code(), responseBody));
                }
            }
        });
    }

    public void resetPasswordForEmail(String email, AuthCallback callback) {
        resetPassword(email, callback);
    }

    public void verifyOtp(String email, String token, String type, RecoveryCallback callback) {
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("type", (type != null && !type.isEmpty()) ? type : "recovery");
        bodyJson.addProperty("email", email.trim());
        bodyJson.addProperty("token", token.trim());

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/verify")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(bodyJson.toString(), JSON))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(
                            "Couldn't connect. Please check your internet connection."));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                        String accessToken = json.has("access_token") && !json.get("access_token").isJsonNull()
                                ? json.get("access_token").getAsString() : "";
                        String refreshToken = json.has("refresh_token") && !json.get("refresh_token").isJsonNull()
                                ? json.get("refresh_token").getAsString() : "";
                        String userId = "";
                        String userEmail = email;
                        if (json.has("user") && json.get("user").isJsonObject()) {
                            JsonObject userObj = json.getAsJsonObject("user");
                            if (userObj.has("id") && !userObj.get("id").isJsonNull()) {
                                userId = userObj.get("id").getAsString();
                            }
                            if (userObj.has("email") && !userObj.get("email").isJsonNull()) {
                                userEmail = userObj.get("email").getAsString();
                            }
                        }
                        if (accessToken.isEmpty() || userId.isEmpty()) {
                            if (callback != null) {
                                mainHandler.post(() -> callback.onError(
                                        "Verification succeeded but the sign-in session was incomplete. Please try again."));
                            }
                            return;
                        }
                        if (callback != null) {
                            String verifiedUserId = userId;
                            String verifiedEmail = userEmail;
                            mainHandler.post(() -> callback.onSuccess(
                                    verifiedUserId, verifiedEmail, accessToken, refreshToken));
                        }
                    } catch (Exception e) {
                        if (callback != null) {
                            mainHandler.post(() -> callback.onError(
                                    "Verification succeeded but couldn't parse response."));
                        }
                    }
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(otpError(response.code(), responseBody)));
                    }
                }
            }
        });
    }

    public void updateUser(String newPassword, String accessToken, DataCallback<Boolean> callback) {
        String token = (accessToken != null && !accessToken.isEmpty()) ? accessToken : getAccessToken();
        if (token == null || token.isEmpty()) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Authorization token is missing. Please verify code again."));
            }
            return;
        }

        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("password", newPassword);

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/user")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .put(RequestBody.create(bodyJson.toString(), JSON))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Couldn't connect. Please check your internet connection."));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(true));
                } else {
                    if (callback != null) {
                        if (response.code() == 429) {
                            mainHandler.post(() -> callback.onError("Too many attempts. Please wait a moment and try again."));
                        } else if (response.code() >= 500) {
                            mainHandler.post(() -> callback.onError("The service is temporarily unavailable. Please try again later."));
                        } else {
                            mainHandler.post(() -> callback.onError(extractErrorMessage(responseBody, "Failed to update password (" + response.code() + ")")));
                        }
                    }
                }
            }
        });
    }

    public void updateUser(String newPassword, DataCallback<Boolean> callback) {
        updateUser(newPassword, null, callback);
    }

    private String signUpError(int responseCode, String responseBody) {
        String lowerBody = responseBody.toLowerCase(Locale.ROOT);
        if (lowerBody.contains("already registered") || lowerBody.contains("already exists")
                || lowerBody.contains("already been registered")) {
            return "An account with this email already exists. Try logging in or use another email.";
        }
        if (responseCode == 429) return "Too many attempts. Please wait a moment and try again.";
        if (responseCode >= 500) return "The service is temporarily unavailable. Please try again later.";
        return "Couldn't create your account. Check your details and try again.";
    }

    private String signInError(int responseCode) {
        if (responseCode == 400 || responseCode == 401) {
            return "Email or password is incorrect. Check your details and try again.";
        }
        if (responseCode == 429) return "Too many attempts. Please wait a moment and try again.";
        if (responseCode >= 500) return "The service is temporarily unavailable. Please try again later.";
        return "Couldn't sign you in right now. Please try again.";
    }

    private String recoveryError(int responseCode, String responseBody) {
        if (responseCode == 429) return "Too many attempts. Please wait a moment and try again.";
        if (responseCode >= 500) return "The service is temporarily unavailable. Please try again later.";
        return extractErrorMessage(responseBody, "Couldn't send password reset instructions. Check your email and try again.");
    }

    private String recoveryError(int responseCode) {
        return recoveryError(responseCode, "");
    }

    private String otpError(int responseCode, String responseBody) {
        if (responseCode == 429) {
            return "Too many attempts. Please wait a moment and try again.";
        }
        String lower = responseBody.toLowerCase(Locale.ROOT);
        if (lower.contains("expired")) {
            return "The verification code has expired. Please request a new code.";
        }
        if (lower.contains("invalid") || lower.contains("token") || lower.contains("otp")) {
            return "The verification code is incorrect or invalid. Please check and try again.";
        }
        if (responseCode >= 500) {
            return "The service is temporarily unavailable. Please try again later.";
        }
        return extractErrorMessage(responseBody, "Verification failed. Please check the code and try again.");
    }

    // --- FULL CLOUD SYNC METHOD (Schedules & Assignments) ---

    public void syncAllLocalDataToCloud(Context context, DataCallback<Boolean> callback) {
        if (!isLoggedIn()) {
            if (callback != null) callback.onError("Please log in to sync your data.");
            return;
        }
        if (isDemoSession()) {
            if (callback != null) callback.onError("Cloud sync is not available for demo accounts.");
            return;
        }

        new Thread(() -> {
            try {
                synchronizeLocalData(context);
                setHasUnsyncedChanges(AppDatabase.getInstance(context).syncChangeDao().getPendingCount() > 0);
                setLastSyncTime(System.currentTimeMillis());
                if (callback != null) mainHandler.post(() -> callback.onSuccess(true));
            } catch (IOException | RuntimeException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(
                            "Couldn't sync right now. Check your connection and try again."));
                }
            }
        }).start();
    }

    private void synchronizeLocalData(Context context) throws IOException {
        AppDatabase db = AppDatabase.getInstance(context);
        String schedulesJson = executeSyncRequest("/rest/v1/schedules?select=*", "GET", null);
        String assignmentsJson = executeSyncRequest("/rest/v1/assignments?select=*", "GET", null);
        JsonArray remoteSchedules = JsonParser.parseString(schedulesJson).getAsJsonArray();
        JsonArray remoteAssignments = JsonParser.parseString(assignmentsJson).getAsJsonArray();
        List<SyncChange> changes = db.syncChangeDao().getPendingChanges();

        mergeSchedules(db, remoteSchedules, changes);
        mergeAssignments(db, remoteAssignments, changes);

        for (SyncChange change : db.syncChangeDao().getPendingChanges()) {
            if (SyncChange.SCHEDULE.equals(change.getEntityType())) {
                syncScheduleChange(db, change);
            } else if (SyncChange.ASSIGNMENT.equals(change.getEntityType())) {
                syncAssignmentChange(db, change);
            }
            db.syncChangeDao().remove(change.getId());
        }
    }

    private void mergeSchedules(AppDatabase db, JsonArray remoteRows, List<SyncChange> changes) throws IOException {
        List<ScheduleItem> localRows = db.scheduleDao().getAllIncludingDeleted();
        for (JsonElement element : remoteRows) {
            JsonObject row = element.getAsJsonObject();
            ScheduleItem remote = parseSchedule(row);
            if (remote.getCloudId() == null) continue;

            ScheduleItem local = db.scheduleDao().findByCloudId(remote.getCloudId());
            if (local == null) {
                local = findMatchingSchedule(localRows, remote);
            }
            if (local == null) {
                db.scheduleDao().insert(remote);
                localRows.add(remote);
                continue;
            }

            boolean localPending = hasPendingChange(changes, SyncChange.SCHEDULE, local.getId());
            local.setCloudId(remote.getCloudId());
            if (!localPending && !local.isDeleted()) {
                copyScheduleValues(remote, local);
                db.scheduleDao().update(local);
            }
        }
    }

    private void mergeAssignments(AppDatabase db, JsonArray remoteRows, List<SyncChange> changes) throws IOException {
        List<AssignmentItem> localRows = db.assignmentDao().getAllIncludingDeleted();
        for (JsonElement element : remoteRows) {
            JsonObject row = element.getAsJsonObject();
            AssignmentItem remote = parseAssignment(row);
            if (remote.getCloudId() == null) continue;

            AssignmentItem local = db.assignmentDao().findByCloudId(remote.getCloudId());
            if (local == null) {
                local = findMatchingAssignment(localRows, remote);
            }
            if (local == null) {
                db.assignmentDao().insert(remote);
                localRows.add(remote);
                continue;
            }

            boolean localPending = hasPendingChange(changes, SyncChange.ASSIGNMENT, local.getId());
            local.setCloudId(remote.getCloudId());
            if (!localPending && !local.isDeleted()) {
                copyAssignmentValues(remote, local);
                db.assignmentDao().update(local);
            }
        }
    }

    private void syncScheduleChange(AppDatabase db, SyncChange change) throws IOException {
        ScheduleItem item = db.scheduleDao().findById(change.getLocalId());
        if (SyncChange.DELETE.equals(change.getOperation())) {
            if (item != null && item.getCloudId() != null) {
                executeSyncRequest(withIdFilter("/rest/v1/schedules", item.getCloudId()), "DELETE", null);
            }
            if (item != null) {
                db.scheduleDao().deletePermanently(item.getId());
            }
            return;
        }
        if (item == null || item.isDeleted()) return;

        if (item.getCloudId() == null) {
            JsonArray response = JsonParser.parseString(executeSyncRequest(
                    "/rest/v1/schedules", "POST", scheduleJson(item))).getAsJsonArray();
            String cloudId = response.isEmpty() ? null : getCloudId(response.get(0).getAsJsonObject());
            if (cloudId == null) throw new IOException("The schedule could not be confirmed as saved.");
            item = db.scheduleDao().findById(item.getId());
            if (item != null) {
                item.setCloudId(cloudId);
                db.scheduleDao().update(item);
            }
        } else {
            String response = executeSyncRequest(
                    withIdFilter("/rest/v1/schedules", item.getCloudId()),
                    "PATCH", scheduleJson(item));
            if (JsonParser.parseString(response).getAsJsonArray().isEmpty()) {
                throw new IOException("The saved schedule could not be found in the cloud.");
            }
        }
    }

    private void syncAssignmentChange(AppDatabase db, SyncChange change) throws IOException {
        AssignmentItem item = db.assignmentDao().findById(change.getLocalId());
        if (SyncChange.DELETE.equals(change.getOperation())) {
            if (item != null && item.getCloudId() != null) {
                executeSyncRequest(withIdFilter("/rest/v1/assignments", item.getCloudId()), "DELETE", null);
            }
            if (item != null) {
                db.assignmentDao().deletePermanently(item.getId());
            }
            return;
        }
        if (item == null || item.isDeleted()) return;

        if (item.getCloudId() == null) {
            JsonArray response = JsonParser.parseString(executeSyncRequest(
                    "/rest/v1/assignments", "POST", assignmentJson(item))).getAsJsonArray();
            String cloudId = response.isEmpty() ? null : getCloudId(response.get(0).getAsJsonObject());
            if (cloudId == null) throw new IOException("The task could not be confirmed as saved.");
            item = db.assignmentDao().findById(item.getId());
            if (item != null) {
                item.setCloudId(cloudId);
                db.assignmentDao().update(item);
            }
        } else {
            String response = executeSyncRequest(
                    withIdFilter("/rest/v1/assignments", item.getCloudId()),
                    "PATCH", assignmentJson(item));
            if (JsonParser.parseString(response).getAsJsonArray().isEmpty()) {
                throw new IOException("The saved task could not be found in the cloud.");
            }
        }
    }

    private synchronized boolean refreshSessionToken() {
        String refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null);
        if (refreshToken == null || refreshToken.isEmpty()) return false;

        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("refresh_token", refreshToken);

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(bodyJson.toString(), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String respStr = response.body().string();
                JsonObject json = JsonParser.parseString(respStr).getAsJsonObject();
                String newAccess = json.has("access_token") && !json.get("access_token").isJsonNull()
                        ? json.get("access_token").getAsString() : null;
                String newRefresh = json.has("refresh_token") && !json.get("refresh_token").isJsonNull()
                        ? json.get("refresh_token").getAsString() : null;

                if (newAccess != null && !newAccess.isEmpty()) {
                    SharedPreferences.Editor editor = prefs.edit().putString(KEY_ACCESS_TOKEN, newAccess);
                    if (newRefresh != null && !newRefresh.isEmpty()) {
                        editor.putString(KEY_REFRESH_TOKEN, newRefresh);
                    }
                    editor.apply();
                    return true;
                }
            }
        } catch (IOException ignored) { }
        return false;
    }

    private String executeSyncRequest(String path, String method, JsonObject body) throws IOException {
        Request request = buildSyncRequest(path, method, body);
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 401) {
                if (refreshSessionToken()) {
                    Request retryRequest = buildSyncRequest(path, method, body);
                    try (Response retryResponse = httpClient.newCall(retryRequest).execute()) {
                        String retryBody = retryResponse.body() != null ? retryResponse.body().string() : "";
                        if (!retryResponse.isSuccessful()) {
                            throw new IOException("Cloud sync request failed with status " + retryResponse.code());
                        }
                        return retryBody;
                    }
                }
            }
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Cloud sync request failed with status " + response.code());
            }
            return responseBody;
        }
    }

    private Request buildSyncRequest(String path, String method, JsonObject body) {
        Request.Builder builder = new Request.Builder()
                .url(SUPABASE_URL + path)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + getAccessToken())
                .addHeader("Prefer", "return=representation");
        if ("GET".equals(method)) {
            builder.get();
        } else if ("DELETE".equals(method)) {
            builder.delete();
        } else {
            builder.method(method, RequestBody.create(body != null ? body.toString() : "{}", JSON))
                    .addHeader("Content-Type", "application/json");
        }
        return builder.build();
    }

    private String withIdFilter(String path, String cloudId) {
        HttpUrl url = HttpUrl.get(SUPABASE_URL + path.substring(0, path.indexOf('?') >= 0
                ? path.indexOf('?') : path.length()));
        return url.newBuilder().addQueryParameter("id", "eq." + cloudId).build().toString()
                .substring(SUPABASE_URL.length());
    }

    private boolean hasPendingChange(List<SyncChange> changes, String type, int localId) {
        for (SyncChange change : changes) {
            if (type.equals(change.getEntityType()) && localId == change.getLocalId()) return true;
        }
        return false;
    }

    private ScheduleItem findMatchingSchedule(List<ScheduleItem> localRows, ScheduleItem target) {
        for (ScheduleItem row : localRows) {
            if (row.getCloudId() == null && sameSchedule(row, target)) return row;
        }
        return null;
    }

    private AssignmentItem findMatchingAssignment(List<AssignmentItem> localRows, AssignmentItem target) {
        for (AssignmentItem row : localRows) {
            if (row.getCloudId() == null && sameAssignment(row, target)) return row;
        }
        return null;
    }

    private boolean sameSchedule(ScheduleItem first, ScheduleItem second) {
        return java.util.Objects.equals(first.getSubject(), second.getSubject())
                && java.util.Objects.equals(first.getDay(), second.getDay())
                && java.util.Objects.equals(first.getStartTime(), second.getStartTime())
                && java.util.Objects.equals(first.getEndTime(), second.getEndTime())
                && first.isWork() == second.isWork()
                && java.util.Objects.equals(first.getColorHex(), second.getColorHex());
    }

    private boolean sameAssignment(AssignmentItem first, AssignmentItem second) {
        return java.util.Objects.equals(first.getTitle(), second.getTitle())
                && java.util.Objects.equals(first.getSubject(), second.getSubject())
                && java.util.Objects.equals(first.getCategory(), second.getCategory())
                && java.util.Objects.equals(first.getDueDate(), second.getDueDate())
                && java.util.Objects.equals(first.getDueTime(), second.getDueTime())
                && first.getDueTimestamp() == second.getDueTimestamp()
                && first.isCompleted() == second.isCompleted();
    }

    private void copyScheduleValues(ScheduleItem source, ScheduleItem target) {
        target.setSubject(source.getSubject());
        target.setDay(source.getDay());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setWork(source.isWork());
        target.setColorHex(source.getColorHex());
    }

    private void copyAssignmentValues(AssignmentItem source, AssignmentItem target) {
        target.setTitle(source.getTitle());
        target.setSubject(source.getSubject());
        target.setCategory(source.getCategory());
        target.setDueDate(source.getDueDate());
        target.setDueTime(source.getDueTime());
        target.setDueTimestamp(source.getDueTimestamp());
        target.setCompleted(source.isCompleted());
    }

    private JsonObject scheduleJson(ScheduleItem item) {
        JsonObject row = new JsonObject();
        row.addProperty("subject", item.getSubject());
        row.addProperty("day", item.getDay());
        row.addProperty("start_time", item.getStartTime());
        row.addProperty("end_time", item.getEndTime());
        row.addProperty("is_work", item.isWork());
        row.addProperty("color_hex", item.getColorHex());
        if (getUserId() != null && !getUserId().isEmpty()) row.addProperty("user_id", getUserId());
        return row;
    }

    private JsonObject assignmentJson(AssignmentItem item) {
        JsonObject row = new JsonObject();
        row.addProperty("title", item.getTitle());
        row.addProperty("subject", item.getSubject());
        row.addProperty("category", item.getCategory());
        row.addProperty("due_date", item.getDueDate());
        row.addProperty("due_time", item.getDueTime());
        row.addProperty("due_timestamp", item.getDueTimestamp());
        row.addProperty("is_completed", item.isCompleted());
        if (getUserId() != null && !getUserId().isEmpty()) row.addProperty("user_id", getUserId());
        return row;
    }

    private String getCloudId(JsonObject row) {
        if (!row.has("id") || row.get("id").isJsonNull()) return null;
        return row.get("id").getAsString();
    }

    private ScheduleItem parseSchedule(JsonObject row) {
        String subject = row.has("subject") ? row.get("subject").getAsString() : "";
        String day = row.has("day") ? row.get("day").getAsString() : "";
        String startTime = row.has("start_time") ? row.get("start_time").getAsString() : "";
        String endTime = row.has("end_time") ? row.get("end_time").getAsString() : "";
        boolean isWork = row.has("is_work") && row.get("is_work").getAsBoolean();
        String color = row.has("color_hex") && !row.get("color_hex").isJsonNull()
                ? row.get("color_hex").getAsString()
                : (isWork ? "#EA580C" : "#4F46E5");
        ScheduleItem item = new ScheduleItem(subject, day, startTime, endTime, isWork, color);
        item.setCloudId(getCloudId(row));
        return item;
    }

    private AssignmentItem parseAssignment(JsonObject row) {
        String title = row.has("title") ? row.get("title").getAsString() : "";
        String subject = row.has("subject") ? row.get("subject").getAsString() : "";
        String category = row.has("category") ? row.get("category").getAsString() : "Assignment";
        String dueDate = row.has("due_date") ? row.get("due_date").getAsString() : "";
        String dueTime = row.has("due_time") ? row.get("due_time").getAsString() : "";
        long dueTimestamp = row.has("due_timestamp") ? row.get("due_timestamp").getAsLong() : 0L;
        boolean completed = row.has("is_completed") && row.get("is_completed").getAsBoolean();
        AssignmentItem item = new AssignmentItem(title, subject, category, dueDate, dueTime, dueTimestamp, completed);
        item.setCloudId(getCloudId(row));
        return item;
    }

    // --- SCHEDULES: SYNC / INSERT TO CLOUD ---

    public void syncSchedulesToCloud(List<ScheduleItem> items, DataCallback<Integer> callback) {
        String token = getAccessToken();
        String userId = getUserId();

        if (token == null || token.isEmpty()) {
            if (callback != null) callback.onError("User is not signed in.");
            return;
        }

        JsonArray array = new JsonArray();
        for (ScheduleItem item : items) {
            JsonObject obj = new JsonObject();
            if (userId != null && !userId.isEmpty()) {
                obj.addProperty("user_id", userId);
            }
            obj.addProperty("subject", item.getSubject());
            obj.addProperty("day", item.getDay());
            obj.addProperty("start_time", item.getStartTime());
            obj.addProperty("end_time", item.getEndTime());
            obj.addProperty("is_work", item.isWork());
            obj.addProperty("color_hex", item.getColorHex());
            array.add(obj);
        }

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/schedules")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(RequestBody.create(array.toString(), JSON))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    setLastSyncTime(System.currentTimeMillis());
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess(items.size()));
                    }
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(extractErrorMessage(responseBody, "Cloud sync failed (" + response.code() + ")")));
                    }
                }
            }
        });
    }

    // --- SCHEDULES: FETCH FROM CLOUD ---

    public void fetchSchedulesFromCloud(DataCallback<List<ScheduleItem>> callback) {
        String token = getAccessToken();
        if (token == null || token.isEmpty()) {
            if (callback != null) callback.onError("User is not signed in.");
            return;
        }

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/schedules?select=*")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        List<ScheduleItem> list = new ArrayList<>();
                        JsonArray array = JsonParser.parseString(responseBody).getAsJsonArray();
                        for (JsonElement el : array) {
                            list.add(parseSchedule(el.getAsJsonObject()));
                        }
                        if (callback != null) {
                            mainHandler.post(() -> callback.onSuccess(list));
                        }
                    } catch (Exception e) {
                        if (callback != null) {
                            mainHandler.post(() -> callback.onError("Error parsing schedules: " + e.getMessage()));
                        }
                    }
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(extractErrorMessage(responseBody, "Failed to load schedules (" + response.code() + ")")));
                    }
                }
            }
        });
    }

    // --- ASSIGNMENTS: SYNC BATCH TO CLOUD ---

    public void syncAssignmentsBatch(List<AssignmentItem> items, DataCallback<Boolean> callback) {
        String token = getAccessToken();
        String userId = getUserId();

        if (token == null || token.isEmpty()) {
            if (callback != null) callback.onError("User is not signed in.");
            return;
        }

        JsonArray array = new JsonArray();
        for (AssignmentItem item : items) {
            JsonObject obj = new JsonObject();
            if (userId != null && !userId.isEmpty()) {
                obj.addProperty("user_id", userId);
            }
            obj.addProperty("title", item.getTitle());
            obj.addProperty("subject", item.getSubject());
            obj.addProperty("category", item.getCategory());
            obj.addProperty("due_date", item.getDueDate());
            obj.addProperty("due_time", item.getDueTime());
            obj.addProperty("due_timestamp", item.getDueTimestamp());
            obj.addProperty("is_completed", item.isCompleted());
            array.add(obj);
        }

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/assignments")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(RequestBody.create(array.toString(), JSON))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(true));
                } else {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(extractErrorMessage(responseBody, "Failed to upload tasks (" + response.code() + ")")));
                    }
                }
            }
        });
    }

    public void syncAssignmentToCloud(AssignmentItem item, DataCallback<Boolean> callback) {
        List<AssignmentItem> list = new ArrayList<>();
        list.add(item);
        syncAssignmentsBatch(list, callback);
    }

    // --- ASSIGNMENTS: FETCH FROM CLOUD ---

    public void fetchAssignmentsFromCloud(DataCallback<List<AssignmentItem>> callback) {
        String token = getAccessToken();
        if (token == null || token.isEmpty()) {
            if (callback != null) callback.onError("User is not signed in.");
            return;
        }

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/assignments?select=*")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        List<AssignmentItem> list = new ArrayList<>();
                        JsonArray array = JsonParser.parseString(responseBody).getAsJsonArray();
                        for (JsonElement el : array) {
                            list.add(parseAssignment(el.getAsJsonObject()));
                        }
                        if (callback != null) {
                            mainHandler.post(() -> callback.onSuccess(list));
                        }
                    } catch (Exception e) {
                        if (callback != null) {
                            mainHandler.post(() -> callback.onError("Error parsing tasks: " + e.getMessage()));
                        }
                    }
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(extractErrorMessage(responseBody, "Failed to load tasks (" + response.code() + ")")));
                    }
                }
            }
        });
    }

    // --- PROFILE & ROOM SYNC METHODS ---

    public void saveUserProfileToRoom(Context context, UserProfile profile) {
        if (context == null || profile == null) return;
        try {
            AppDatabase.getInstance(context).userProfileDao().insertOrUpdate(profile);
        } catch (Exception e) {
            Log.e(TAG, "Could not cache profile in Room", e);
        }
    }

    public void loadUserProfileFromRoom(Context context, String userId, DataCallback<UserProfile> callback) {
        if (context == null || userId == null) {
            if (callback != null) mainHandler.post(() -> callback.onError("Invalid user"));
            return;
        }
        new Thread(() -> {
            try {
                UserProfile profile = AppDatabase.getInstance(context).userProfileDao().getUserProfile(userId);
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(profile));
                }
            } catch (Exception e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e.getLocalizedMessage()));
                }
            }
        }).start();
    }

    public void loadProfileFromSupabase(Context context, DataCallback<UserProfile> callback) {
        String userId = getUserId();
        String token = getAccessToken();
        if (userId == null || userId.isEmpty() || token == null || token.isEmpty() || isDemoSession()) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("A signed-in Supabase account is required."));
            }
            return;
        }

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/profiles?id=eq." + userId
                        + "&select=id,full_name,course,avatar_url,updated_at")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                postDataError(callback, "Couldn't load your profile. Check your connection and try again.");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    postDataError(callback, extractErrorMessage(body,
                            "Couldn't load your profile (" + response.code() + ")."));
                    return;
                }

                try {
                    JsonArray rows = JsonParser.parseString(body).getAsJsonArray();
                    if (rows.size() == 0) {
                        mainHandler.post(() -> {
                            if (callback != null) callback.onSuccess(null);
                        });
                        return;
                    }

                    JsonObject row = rows.get(0).getAsJsonObject();
                    String name = getJsonString(row, "full_name");
                    String course = getJsonString(row, "course");
                    String avatarUrl = getJsonString(row, "avatar_url");
                    long updatedAt = prefs.getLong(userScopedKey(KEY_PROFILE_UPDATED_AT, userId),
                            System.currentTimeMillis());
                    if (row.has("updated_at") && !row.get("updated_at").isJsonNull()) {
                        updatedAt = parseTimestamp(row.get("updated_at").getAsString(), updatedAt);
                    }
                    File avatarFile = new File(context.getFilesDir(), "profile_avatar_" + userId + ".jpg");
                    long cachedUpdatedAt = prefs.getLong(userScopedKey(KEY_PROFILE_UPDATED_AT, userId), 0L);
                    if (cachedUpdatedAt != 0L && cachedUpdatedAt != updatedAt
                            && avatarFile.exists() && !avatarFile.delete()) {
                        Log.w(TAG, "Could not invalidate stale cached profile picture");
                    }
                    UserProfile profile = new UserProfile(userId, name, getUserEmail(), course,
                            avatarFile.exists() ? avatarFile.getAbsolutePath() : null, avatarUrl, updatedAt);

                    if (userId.equals(getUserId())) {
                        SharedPreferences.Editor editor = prefs.edit();
                        if (name != null) editor.putString(userScopedKey(KEY_USER_NAME, userId), name);
                        else editor.remove(userScopedKey(KEY_USER_NAME, userId));
                        if (course != null) editor.putString(userScopedKey(KEY_USER_COURSE, userId), course);
                        else editor.remove(userScopedKey(KEY_USER_COURSE, userId));
                        editor.putString(userScopedKey(KEY_AVATAR_URL, userId), avatarUrl);
                        editor.putLong(userScopedKey(KEY_PROFILE_UPDATED_AT, userId), updatedAt);
                        if (avatarFile.exists()) {
                            editor.putString(userScopedKey(KEY_AVATAR_PATH, userId), avatarFile.getAbsolutePath());
                        } else {
                            editor.remove(userScopedKey(KEY_AVATAR_PATH, userId));
                        }
                        editor.apply();
                        saveUserProfileToRoom(context, profile);
                    }
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(profile);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Could not parse Supabase profile response", e);
                    postDataError(callback, "Couldn't read your profile data.");
                }
            }
        });
    }

    public void updateProfileData(Context context, String name, String course, String avatarUrl, DataCallback<Boolean> callback) {
        if (isDemoSession()) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError("Demo profiles can't be changed."));
            }
            return;
        }

        String token = getAccessToken();
        String userId = getUserId();
        if (userId == null || userId.isEmpty() || token == null || token.isEmpty()) {
            postDataError(callback, "Sign in before saving your profile.");
            return;
        }

        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("id", userId);
        bodyJson.addProperty("full_name", name == null ? "" : name.trim());
        bodyJson.addProperty("course", course == null ? "" : course.trim());
        String savedAvatarUrl = avatarUrl != null ? avatarUrl : getAvatarUrl();
        if (savedAvatarUrl == null) bodyJson.add("avatar_url", com.google.gson.JsonNull.INSTANCE);
        else bodyJson.addProperty("avatar_url", savedAvatarUrl);
        SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        utcFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        bodyJson.addProperty("updated_at", utcFormat.format(new Date()));

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/profiles?on_conflict=id")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(RequestBody.create(bodyJson.toString(), JSON))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Couldn't save your profile. Check your connection and try again."));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    File avatarFile = new File(context.getFilesDir(), "profile_avatar_" + userId + ".jpg");
                    String localAvatarPath = avatarFile.exists() ? avatarFile.getAbsolutePath() : null;
                    UserProfile profile = new UserProfile(userId, name, getUserEmail(), course,
                            localAvatarPath, savedAvatarUrl, System.currentTimeMillis());
                    if (userId.equals(getUserId())) {
                        SharedPreferences.Editor editor = prefs.edit()
                                .putString(userScopedKey(KEY_USER_NAME, userId), name)
                                .putString(userScopedKey(KEY_USER_COURSE, userId), course)
                                .putString(userScopedKey(KEY_AVATAR_URL, userId), savedAvatarUrl)
                                .putLong(userScopedKey(KEY_PROFILE_UPDATED_AT, userId),
                                        System.currentTimeMillis());
                        if (localAvatarPath != null) {
                            editor.putString(userScopedKey(KEY_AVATAR_PATH, userId), localAvatarPath);
                        }
                        editor.apply();
                    }
                    if (userId.equals(getUserId())) {
                        saveUserProfileToRoom(context, profile);
                    }
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(true));
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(extractErrorMessage(responseBody, "Could not update profile online (" + response.code() + ")")));
                    }
                }
            }
        });
    }

    public void updateEmail(Context context, String newEmail, DataCallback<Boolean> callback) {
        String token = getAccessToken();
        if (token == null || token.isEmpty()) {
            if (callback != null) callback.onError("User is not signed in.");
            return;
        }
        if (isDemoSession()) {
            if (callback != null) callback.onError("Email change is not available in demo mode.");
            return;
        }

        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("email", newEmail.trim());

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/user")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .put(RequestBody.create(bodyJson.toString(), JSON))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Couldn't connect. Please check your internet connection."));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    prefs.edit().putString(KEY_USER_EMAIL, newEmail.trim()).apply();
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(true));
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(extractErrorMessage(responseBody, "Failed to send email confirmation (" + response.code() + ")")));
                    }
                }
            }
        });
    }

    public void reauthenticate(DataCallback<Boolean> callback) {
        String token = getAccessToken();
        if (token == null || token.isEmpty()) {
            if (callback != null) callback.onError("User is not signed in.");
            return;
        }
        if (isDemoSession()) {
            if (callback != null) callback.onError("Password change is not available in demo mode.");
            return;
        }

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/reauthenticate")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create(new byte[0], null))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Couldn't connect. Please check your internet connection."));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(true));
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(extractErrorMessage(responseBody, "Failed to send verification code (" + response.code() + ")")));
                    }
                }
            }
        });
    }

    public void updatePasswordWithNonce(String newPassword, String nonce, DataCallback<Boolean> callback) {
        String token = getAccessToken();
        if (token == null || token.isEmpty()) {
            if (callback != null) callback.onError("User is not signed in.");
            return;
        }
        if (isDemoSession()) {
            if (callback != null) callback.onError("Password change is not available in demo mode.");
            return;
        }

        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("password", newPassword);
        bodyJson.addProperty("nonce", nonce.trim());

        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/auth/v1/user")
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .put(RequestBody.create(bodyJson.toString(), JSON))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Couldn't connect. Please check your internet connection."));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(true));
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(extractErrorMessage(responseBody, "Password change failed. Verify code and try again (" + response.code() + ")")));
                    }
                }
            }
        });
    }

    public void uploadProfilePicture(byte[] imageBytes, DataCallback<String> callback) {
        String token = getAccessToken();
        if (token == null || token.isEmpty()) {
            if (callback != null) callback.onError("User is not signed in.");
            return;
        }
        if (isDemoSession()) {
            if (callback != null) callback.onError("Storage upload is not available in demo mode.");
            return;
        }

        String userId = getUserId();
        if (userId == null || userId.isEmpty()) {
            postDataError(callback, "A signed-in account is required to upload a profile picture.");
            return;
        }
        String pathName = userId + "/avatar.jpg";
        String uploadUrl = SUPABASE_URL + "/storage/v1/object/" + AVATAR_BUCKET + "/" + pathName;
        String publicUrl = SUPABASE_URL + "/storage/v1/object/public/" + AVATAR_BUCKET + "/" + pathName;

        MediaType mediaType = MediaType.parse("image/jpeg");
        RequestBody requestBody = RequestBody.create(imageBytes, mediaType);

        Request request = new Request.Builder()
                .url(uploadUrl)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("x-upsert", "true")
                .addHeader("Content-Type", "image/jpeg")
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("Upload failed: " + e.getLocalizedMessage()));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(publicUrl));
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(extractErrorMessage(responseBody, "Upload failed with status " + response.code())));
                    }
                }
            }
        });
    }

    // --- PRIVATE HELPERS ---

    private void postSuccess(AuthCallback callback, String id, String email, String token) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(id, email, token));
        }
    }

    private void postDataError(DataCallback<?> callback, String error) {
        if (callback != null) mainHandler.post(() -> callback.onError(error));
    }

    private String userScopedKey(String key) {
        String userId = getUserId();
        return userScopedKey(key, userId);
    }

    private String userScopedKey(String key, String userId) {
        return key + "_" + (userId == null || userId.isEmpty() ? "anonymous" : userId);
    }

    private String getJsonString(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }

    private long parseTimestamp(String value, long fallback) {
        String[] formats = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for (String format : formats) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(format, Locale.US);
                parser.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                Date date = parser.parse(value);
                if (date != null) return date.getTime();
            } catch (java.text.ParseException ignored) {
                // Try the next supported PostgREST timestamp format.
            }
        }
        Log.e(TAG, "Could not parse Supabase profile updated_at value");
        return fallback;
    }

    private void postError(AuthCallback callback, String error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    private String extractErrorMessage(String jsonBody, String fallback) {
        try {
            JsonObject json = JsonParser.parseString(jsonBody).getAsJsonObject();
            if (json.has("msg")) return json.get("msg").getAsString();
            if (json.has("error_description")) return json.get("error_description").getAsString();
            if (json.has("message")) return json.get("message").getAsString();
        } catch (Exception ignored) { }
        return fallback;
    }
}
