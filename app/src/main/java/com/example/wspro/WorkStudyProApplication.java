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

import java.util.concurrent.atomic.AtomicBoolean;

public class WorkStudyProApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private static final long SYNC_DEBOUNCE_DELAY_MS = 5000L;

    private final AtomicBoolean promptCheckRunning = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable debouncedSyncRunnable = this::performSilentSync;

    private Activity resumedActivity;
    private volatile boolean hasValidatedNetwork;
    private volatile boolean promptShownForConnection;

    @Override
    public void onCreate() {
        super.onCreate();
        SupabaseClient.applyAppearance(
                SupabaseClient.getInstance(this).getAppearanceMode());
        registerActivityLifecycleCallbacks(this);

        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
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

    private void updateConnectionState(ConnectivityManager manager, Network network) {
        updateConnectionState(manager.getNetworkCapabilities(network));
    }

    private void updateConnectionState(NetworkCapabilities capabilities) {
        boolean isValidated = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if (isValidated && !hasValidatedNetwork) {
            hasValidatedNetwork = true;
            promptShownForConnection = false;
            triggerDebouncedSync();
            showOfflineSyncPromptIfNeeded();
        } else if (!isValidated) {
            hasValidatedNetwork = false;
            promptShownForConnection = false;
        }
    }

    public void onLocalChangesQueued() {
        if (hasValidatedNetwork) {
            triggerDebouncedSync();
        } else {
            new Thread(() -> {
                try {
                    AppDatabase.getInstance(this).syncChangeDao().markPendingAsOffline();
                } catch (Exception ignored) { }
            }).start();
        }
    }

    private void triggerDebouncedSync() {
        mainHandler.removeCallbacks(debouncedSyncRunnable);
        mainHandler.postDelayed(debouncedSyncRunnable, SYNC_DEBOUNCE_DELAY_MS);
    }

    private void performSilentSync() {
        SupabaseClient client = SupabaseClient.getInstance(this);
        if (!hasValidatedNetwork || !client.isLoggedIn() || client.isDemoSession()) return;
        client.syncAllLocalDataToCloud(this, new SupabaseClient.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                // Silent success - no toasts or popups!
            }

            @Override
            public void onError(String errorMessage) {
                // Silent failure - changes remain queued safely, no toast spam!
            }
        });
    }

    private void showOfflineSyncPromptIfNeeded() {
        Activity activity = resumedActivity;
        SupabaseClient client = SupabaseClient.getInstance(this);
        if (!hasValidatedNetwork || promptShownForConnection || activity == null
                || activity.isFinishing() || activity.isDestroyed()
                || !client.isLoggedIn() || client.isDemoSession()
                || !promptCheckRunning.compareAndSet(false, true)) {
            return;
        }

        new Thread(() -> {
            boolean hasOfflineChanges;
            try {
                hasOfflineChanges = AppDatabase.getInstance(getApplicationContext())
                        .syncChangeDao().getOfflinePendingCount() > 0;
            } catch (RuntimeException e) {
                promptCheckRunning.set(false);
                return;
            }

            mainHandler.post(() -> {
                promptCheckRunning.set(false);
                Activity current = resumedActivity;
                if (!hasOfflineChanges || !hasValidatedNetwork || promptShownForConnection
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
