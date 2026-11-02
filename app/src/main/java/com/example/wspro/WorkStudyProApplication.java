package com.example.wspro;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class WorkStudyProApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "WorkStudyProApp";
    private static final long SYNC_DEBOUNCE_DELAY_MS = 5000L;
    private static WorkStudyProApplication appInstance;

    private final AtomicBoolean promptCheckRunning = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable debouncedSyncRunnable = this::performSilentSync;

    private Activity resumedActivity;
    private volatile boolean hasValidatedNetwork;
    private volatile boolean promptShownForConnection;

    public static Context getAppContext() {
        return appInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        appInstance = this;

        NotificationHelper.createChannels(this);
        SupabaseClient supabaseClient = SupabaseClient.getInstance(this);
        if (supabaseClient.isLoggedIn() && !supabaseClient.isDemoSession()) {
            NotificationTriggerEngine.schedulePeriodicCheck(this);
        }
        SupabaseClient.applyAppearance(supabaseClient.getAppearanceMode());
        registerActivityLifecycleCallbacks(this);

        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                updateConnectionState(connectivityManager, network);
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                updateConnectionState(capabilities);
            }

            @Override
            public void onLost(Network network) {
                hasValidatedNetwork = false;
                promptShownForConnection = false;
            }
        };

        connectivityManager.registerDefaultNetworkCallback(callback);
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork != null) updateConnectionState(connectivityManager, activeNetwork);
    }

    private void updateConnectionState(ConnectivityManager cm, Network network) {
        if (cm == null || network == null) return;
        updateConnectionState(cm.getNetworkCapabilities(network));
    }

    private void updateConnectionState(NetworkCapabilities capabilities) {
        boolean isConnected = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));

        if (!isConnected) {
            hasValidatedNetwork = false;
            promptShownForConnection = false;
            return;
        }

        hasValidatedNetwork = true;
        promptShownForConnection = false;
        scheduleDebouncedSync();
        showOfflineSyncPromptIfNeeded();
    }

    private void scheduleDebouncedSync() {
        mainHandler.removeCallbacks(debouncedSyncRunnable);
        mainHandler.postDelayed(debouncedSyncRunnable, SYNC_DEBOUNCE_DELAY_MS);
    }

    public void onLocalChangesQueued() {
        SupabaseClient client = SupabaseClient.getInstance(this);
        if (!client.isLoggedIn() || client.isDemoSession()) return;

        client.setHasUnsyncedChanges(true);
        if (hasValidatedNetwork) {
            scheduleDebouncedSync();
        }
    }

    private void performSilentSync() {
        if (!hasValidatedNetwork) return;

        SupabaseClient client = SupabaseClient.getInstance(this);
        if (!client.isLoggedIn() || client.isDemoSession()) return;

        client.syncAllLocalDataToCloud(this, new SupabaseClient.DataCallback<>() {
            @Override
            public void onSuccess(Boolean result) {
                promptShownForConnection = false;
            }

            @Override
            public void onError(String errorMessage) {
                showOfflineSyncPromptIfNeeded();
            }
        });
    }

    private void showOfflineSyncPromptIfNeeded() {
        SupabaseClient client = SupabaseClient.getInstance(this);
        if (!hasValidatedNetwork || promptShownForConnection || !client.isLoggedIn() || client.isDemoSession()) {
            return;
        }

        if (!promptCheckRunning.compareAndSet(false, true)) {
            return;
        }

        new Thread(() -> {
            boolean hasOfflineChanges;
            try {
                hasOfflineChanges =
                        AppDatabase.getInstance(this).syncChangeDao().getOfflinePendingCount() > 0;
            } catch (RuntimeException e) {
                android.util.Log.e(TAG, "Could not check for offline changes", e);
                promptCheckRunning.set(false);
                return;
            }

            if (!hasOfflineChanges) {
                promptCheckRunning.set(false);
                return;
            }

            mainHandler.post(() -> {
                promptCheckRunning.set(false);
                Activity current = resumedActivity;
                if (!hasValidatedNetwork || promptShownForConnection
                        || current == null || current.isFinishing() || current.isDestroyed()) {
                    return;
                }

                promptShownForConnection = true;
                new AlertDialog.Builder(current)
                        .setTitle("Sync your schedule?")
                        .setMessage("We have detected changes in your schedule. Should we sync them now?")
                        .setPositiveButton("Yes", (dialog, which) -> performSilentSync())
                        .setNegativeButton("Not yet", null)
                        .show();
            });
        }).start();
    }

    @Override
    public void onActivityResumed(Activity activity) {
        resumedActivity = activity;
        if (hasValidatedNetwork) {
            showOfflineSyncPromptIfNeeded();
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (resumedActivity == activity) resumedActivity = null;
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
    @Override public void onActivityDestroyed(Activity activity) {
        if (resumedActivity == activity) resumedActivity = null;
    }
}
