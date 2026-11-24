package com.example.wspro;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NotificationTriggerEngine {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public static void schedulePeriodicCheck(Context context) {
        if (context == null) return;
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

        AppDatabase db = AppDatabase.getInstance(context);
        long now = System.currentTimeMillis();

        // ----------------------------------------------------
        // Trigger 1: Deadline Reminders (1 Day and 1 Hour before due)
        // ----------------------------------------------------
        List<AssignmentItem> pendingTasks = db.assignmentDao().getPendingAssignments();
        if (pendingTasks != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String todayDateStr = dateFormat.format(Calendar.getInstance().getTime());
            Calendar calNow = Calendar.getInstance();
            int hour = calNow.get(Calendar.HOUR_OF_DAY);
            int minute = calNow.get(Calendar.MINUTE);
            String todayDayCode = PriorityEngine.getDayCodeFromTimestamp(now);

            for (AssignmentItem task : pendingTasks) {
                // Check custom task reminders
                if (task.isHasReminder() && !task.isCompleted()) {
                    if (task.isRecurring()) {
                        String recurringDays = task.getRecurringDays();
                        if (recurringDays != null && recurringDays.toUpperCase(Locale.US).contains(todayDayCode)) {
                            String reminderTime = task.getReminderTime();
                            if (reminderTime != null && !reminderTime.isEmpty()) {
                                String dedupeKey = "reminder_recurring_" + task.getId() + "_" + todayDateStr + "_" + reminderTime;
                                if (!db.notificationDao().existsByDedupeKey(dedupeKey)) {
                                    String title = "Recurring Reminder: " + task.getTitle();
                                    String message = "Reminder for " + task.getTitle() + " (" + reminderTime + ").";
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
                    } else {
                        long remTime = task.getReminderTimestamp();
                        if (remTime > 0 && now >= remTime) {
                            String dedupeKey = "reminder_onetime_" + task.getId() + "_" + remTime;
                            if (!db.notificationDao().existsByDedupeKey(dedupeKey)) {
                                String title = "Task Reminder: " + task.getTitle();
                                String message = task.getTitle() + " is due.";
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

                // Standard 1 Day and 1 Hour deadlines with title & time
                long due = task.getDueTimestamp();
                if (due <= 0) continue;

                long diff = due - now;
                if (diff <= 0) continue; // Already expired

                String timeStr = task.getDueTime() != null
                        ? TimeFormatPreference.formatTime(context, task.getDueTime()) : "soon";
                String subject = task.isWorkTask() || task.getSubject() == null
                        || task.getSubject().isEmpty() ? "" : " (" + task.getSubject() + ")";

                // 1 Hour Deadline Reminder
                if (diff <= 3600 * 1000L) {
                    String dedupeKey = "deadline_1h_" + task.getId() + "_" + due;
                    if (!db.notificationDao().existsByDedupeKey(dedupeKey)) {
                        String title = "Urgent: " + task.getTitle() + " due soon";
                        String message = task.getTitle() + subject + " is due in 1 hour (" + timeStr + ").";

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
                // 1 Day (24 Hours) Deadline Reminder
                else if (diff <= 24 * 3600 * 1000L) {
                    String dedupeKey = "deadline_1d_" + task.getId() + "_" + due;
                    if (!db.notificationDao().existsByDedupeKey(dedupeKey)) {
                        String title = "Upcoming Deadline: " + task.getTitle();
                        String message = task.getTitle() + subject + " is due in 1 day at " + timeStr + ".";

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

            // ----------------------------------------------------
            // Trigger 2: Daily Summary at 8:00 AM
            // ----------------------------------------------------
            if (hour == 8 && minute < 20) {
                String summaryDedupeKey = "daily_summary_" + todayDateStr;
                if (!db.notificationDao().existsByDedupeKey(summaryDedupeKey)) {
                    List<ScheduleItem> todaySchedules = db.scheduleDao().getAllSchedules(todayDayCode);
                    List<AssignmentItem> allAssignments = db.assignmentDao().getAllAssignments();
                    int todayTaskCount = 0;
                    if (allAssignments != null) {
                        for (AssignmentItem a : allAssignments) {
                            if (!a.isCompleted() && Objects.equals(todayDateStr, a.getDueDate())) {
                                todayTaskCount++;
                            }
                        }
                    }
                    int classCount = todaySchedules != null ? todaySchedules.size() : 0;

                    String title = "Good Morning! Today's Schedule";
                    String message = "You have " + classCount + " classes and " + todayTaskCount + " tasks scheduled for today (" + todayDateStr + ").";

                    NotificationItem item = new NotificationItem(
                            NotificationItem.TYPE_DEADLINE,
                            title,
                            message,
                            now,
                            false,
                            null,
                            summaryDedupeKey
                    );
                    long rowId = db.notificationDao().insert(item);
                    if (rowId > 0) {
                        NotificationHelper.postNotification(
                                context,
                                (int) rowId,
                                NotificationItem.TYPE_DEADLINE,
                                title,
                                message,
                                null
                        );
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Trigger 3: Conflict Alert (schedule overlap detection)
        // ----------------------------------------------------
        List<ScheduleItem> schedules = db.scheduleDao().getAll();
        if (schedules != null && schedules.size() >= 2) {
            for (int i = 0; i < schedules.size(); i++) {
                ScheduleItem s1 = schedules.get(i);
                for (int j = i + 1; j < schedules.size(); j++) {
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
                                    + " overlap on " + day + " ("
                                    + TimeFormatPreference.formatTime(context, s1.getStartTime()) + " - "
                                    + TimeFormatPreference.formatTime(context, s1.getEndTime()) + " and "
                                    + TimeFormatPreference.formatTime(context, s2.getStartTime()) + " - "
                                    + TimeFormatPreference.formatTime(context, s2.getEndTime()) + ").";

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
        // Trigger 4: Workload Alert (highest load tier)
        // ----------------------------------------------------
        if (schedules != null) {
            SupabaseClient client = SupabaseClient.getInstance(context);
            int weeklyLimit = client.getWeeklyHoursLimit();
            LoadCalculator.LoadResult load = LoadCalculator.calculate(
                    schedules, weeklyLimit, AlgorithmConfig.load(context));

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
