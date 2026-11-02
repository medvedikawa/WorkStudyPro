package com.example.wspro;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NotificationTriggerEngine {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public static void schedulePeriodicCheck(Context context) {
        if (context == null) return;
        SupabaseClient supabaseClient = SupabaseClient.getInstance(context);
        if (!supabaseClient.isLoggedIn() || supabaseClient.isDemoSession()
                || supabaseClient.getAccessToken() == null
                || supabaseClient.getAccessToken().isEmpty()) return;
        PeriodicWorkRequest periodicRequest = new PeriodicWorkRequest.Builder(
                NotificationWorker.class,
                15,
                TimeUnit.MINUTES
        ).build();

        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                "wspro_notification_periodic_worker",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
        );
    }

    public static void cancelScheduledWork(Context context) {
        if (context == null) return;
        WorkManager.getInstance(context.getApplicationContext())
                .cancelUniqueWork("wspro_notification_periodic_worker");
    }

    public static void triggerImmediately(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        EXECUTOR.execute(() -> runTriggers(appCtx));
    }

    public static synchronized void runTriggers(Context context) {
        if (context == null) return;
        SupabaseClient supabaseClient = SupabaseClient.getInstance(context);
        if (!supabaseClient.isLoggedIn() || supabaseClient.isDemoSession()
                || supabaseClient.getAccessToken() == null
                || supabaseClient.getAccessToken().isEmpty()) return;

        AppDatabase db = AppDatabase.getInstance(context);
        long now = System.currentTimeMillis();

        // ----------------------------------------------------
        // Trigger 1: Deadline Reminders (24h and 2h before due)
        // ----------------------------------------------------
        List<AssignmentItem> pendingTasks = db.assignmentDao().getPendingAssignments();
        if (pendingTasks != null) {
            for (AssignmentItem task : pendingTasks) {
                long due = task.getDueTimestamp();
                if (due <= 0) continue;

                long diff = due - now;
                if (diff <= 0) continue; // Already expired

                // 2 Hours Deadline Reminder
                if (diff <= 2 * 3600 * 1000L) {
                    String dedupeKey = "deadline_2h_" + task.getId() + "_" + due;
                    if (!db.notificationDao().existsByDedupeKey(dedupeKey)) {
                        String title = "Urgent: " + task.getTitle() + " due soon";
                        String timeStr = task.getDueTime() != null ? task.getDueTime() : "soon";
                        String message = task.getTitle() + " (" + task.getSubject() + ") is due in less than 2 hours (" + timeStr + ").";

                        NotificationItem item = new NotificationItem(
                                NotificationItem.TYPE_DEADLINE,
                                title,
                                message,
                                now,
                                false,
                                Integer.toString(task.getId()),
                                dedupeKey
                        );
                        long rowId = db.notificationDao().insert(item);
                        if (rowId > 0) {
                            NotificationHelper.postNotification(
                                    context,
                                    (int) rowId,
                                    NotificationItem.TYPE_DEADLINE,
                                    title,
                                    message,
                                    Integer.toString(task.getId())
                            );
                        }
                    }
                }
                // 24 Hours Deadline Reminder
                else if (diff <= 24 * 3600 * 1000L) {
                    String dedupeKey = "deadline_24h_" + task.getId() + "_" + due;
                    if (!db.notificationDao().existsByDedupeKey(dedupeKey)) {
                        String title = "Upcoming Deadline: " + task.getTitle();
                        String timeStr = task.getDueTime() != null ? task.getDueTime() : "";
                        String message = task.getTitle() + " (" + task.getSubject() + ") is due in less than 24 hours"
                                + (timeStr.isEmpty() ? "." : " at " + timeStr + ".");

                        NotificationItem item = new NotificationItem(
                                NotificationItem.TYPE_DEADLINE,
                                title,
                                message,
                                now,
                                false,
                                String.valueOf(task.getId()),
                                dedupeKey
                        );
                        long rowId = db.notificationDao().insert(item);
                        if (rowId > 0) {
                            NotificationHelper.postNotification(
                                    context,
                                    (int) rowId,
                                    NotificationItem.TYPE_DEADLINE,
                                    title,
                                    message,
                                    String.valueOf(task.getId())
                            );
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Trigger 2: Conflict Alert (schedule overlap detection)
        // ----------------------------------------------------
        List<ScheduleItem> schedules = db.scheduleDao().getAll();
        if (schedules != null && schedules.size() >= 2) {
            for (int i = 0; i < schedules.size(); i++) {
                for (int j = i + 1; j < schedules.size(); j++) {
                    ScheduleItem s1 = schedules.get(i);
                    ScheduleItem s2 = schedules.get(j);

                    if (ConflictChecker.hasConflict(
                            s1.getDay(), s1.getStartTime(), s1.getEndTime(),
                            s2.getDay(), s2.getStartTime(), s2.getEndTime())) {

                        int minId = Math.min(s1.getId(), s2.getId());
                        int maxId = Math.max(s1.getId(), s2.getId());
                        String day = s1.getDay() != null ? s1.getDay() : "";
                        ScheduleItem firstById = s1.getId() < s2.getId() ? s1 : s2;
                        ScheduleItem secondById = s1.getId() < s2.getId() ? s2 : s1;
                        String dedupeKey = "conflict_" + minId + "_" + maxId + "_" + day + "_"
                                + firstById.getStartTime() + "_" + firstById.getEndTime() + "_"
                                + secondById.getStartTime() + "_" + secondById.getEndTime();

                        if (!db.notificationDao().existsByDedupeKey(dedupeKey)) {
                            String title = "Schedule Conflict: " + day;
                            String message = s1.getSubject() + " and " + s2.getSubject()
                                    + " overlap on " + day + " (" + s1.getStartTime() + " - " + s1.getEndTime()
                                    + " and " + s2.getStartTime() + " - " + s2.getEndTime() + ").";

                            NotificationItem item = new NotificationItem(
                                    NotificationItem.TYPE_CONFLICT,
                                    title,
                                    message,
                                    now,
                                    false,
                                    minId + "," + maxId,
                                    dedupeKey
                            );
                            long rowId = db.notificationDao().insert(item);
                            if (rowId > 0) {
                                NotificationHelper.postNotification(
                                    context,
                                    (int) rowId,
                                    NotificationItem.TYPE_CONFLICT,
                                    title,
                                    message,
                                    minId + "," + maxId
                                );
                            }
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Trigger 3: Workload Alert (highest load tier)
        // ----------------------------------------------------
        if (schedules != null) {
            SupabaseClient client = SupabaseClient.getInstance(context);
            int weeklyLimit = client.getWeeklyHoursLimit();
            LoadCalculator.LoadResult load = LoadCalculator.calculate(schedules, weeklyLimit);

            if (load.isHigh()) {
                Calendar cal = Calendar.getInstance();
                String dedupeKey = "workload_highest_" + cal.get(Calendar.YEAR) + "_W" + cal.get(Calendar.WEEK_OF_YEAR);

                if (!db.notificationDao().existsByDedupeKey(dedupeKey)) {
                    String title = "Heavy Workload Alert";
                    String message = "Your scheduled commitments (" + load.getRoundedTotal()
                            + " hrs) exceed your weekly limit of " + weeklyLimit + " hrs.";

                    NotificationItem item = new NotificationItem(
                            NotificationItem.TYPE_WORKLOAD,
                            title,
                            message,
                            now,
                            false,
                            String.valueOf(load.getRoundedTotal()),
                            dedupeKey
                    );
                    long rowId = db.notificationDao().insert(item);
                    if (rowId > 0) {
                        NotificationHelper.postNotification(
                                context,
                                (int) rowId,
                                NotificationItem.TYPE_WORKLOAD,
                                title,
                                message,
                                String.valueOf(load.getRoundedTotal())
                        );
                    }
                }
            }
        }
    }
}
