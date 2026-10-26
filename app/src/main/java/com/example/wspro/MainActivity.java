package com.example.wspro;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    protected static final String CHANNEL_ID = "work_study_conflicts";
    private static final String TAG = "TodayScreen";

    private TextView tvDateHeader;
    private TextView tvWelcomeTitle;
    private TextView tvWorkloadStatus;
    private TextView tvWorkloadValue;
    private TextView tvWorkloadHours;
    private ProgressBar progressWeeklyLoad;
    private LinearLayout layoutWorkloadCard;
    private LinearLayout layoutConflictCard;
    private TextView tvConflictHeading;
    private TextView tvConflictDetails;
    private View btnSuggestAction;
    private LinearLayout cardFocusTasks;
    private LinearLayout containerPriorityTasks;
    private LinearLayout layoutEmptyTasksPlaceholder;
    private LinearLayout cardTodaySchedule;
    private LinearLayout containerTodaySchedule;
    private LinearLayout layoutEmptySchedulePlaceholder;
    private boolean loadInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDateHeader = findViewById(R.id.tvDateHeader);
        tvWelcomeTitle = findViewById(R.id.tvWelcomeTitle);
        tvWorkloadStatus = findViewById(R.id.tvWorkloadStatus);
        tvWorkloadValue = findViewById(R.id.tvWorkloadValue);
        tvWorkloadHours = findViewById(R.id.tvWorkloadHours);
        progressWeeklyLoad = findViewById(R.id.progressWeeklyLoad);
        layoutWorkloadCard = findViewById(R.id.layoutWorkloadCard);
        layoutConflictCard = findViewById(R.id.layoutConflictCard);
        tvConflictHeading = findViewById(R.id.tvConflictHeading);
        tvConflictDetails = findViewById(R.id.tvConflictDetails);
        btnSuggestAction = findViewById(R.id.btnSuggestAction);
        cardFocusTasks = findViewById(R.id.cardFocusTasks);
        containerPriorityTasks = findViewById(R.id.containerPriorityTasks);
        layoutEmptyTasksPlaceholder = findViewById(R.id.layoutEmptyTasksPlaceholder);
        cardTodaySchedule = findViewById(R.id.cardTodaySchedule);
        containerTodaySchedule = findViewById(R.id.containerTodaySchedule);
        layoutEmptySchedulePlaceholder = findViewById(R.id.layoutEmptySchedulePlaceholder);

        renderHeader();
        btnSuggestAction.setOnClickListener(v -> navigateToWeek());
        findViewById(R.id.btnHomeAddTask).setOnClickListener(v -> navigateToTasks());
        findViewById(R.id.btnViewAllTasks).setOnClickListener(v -> navigateToTasks());
        findViewById(R.id.btnViewWeekSchedule).setOnClickListener(v -> navigateToWeek());
        findViewById(R.id.btnHomeAddSchedule).setOnClickListener(v -> {
            ImageButton fab = findViewById(R.id.fabScanSchedule);
            if (fab != null) fab.performClick();
        });

        createNotificationChannel();
        BottomNavHelper.setup(this, BottomNavHelper.Tab.TODAY, new ScanButtonHelper(this));
        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboardData();
    }

    private void renderHeader() {
        tvDateHeader.setText(new SimpleDateFormat(
                getString(R.string.today_date_format), Locale.getDefault()).format(new Date()));

        String storedName = SupabaseClient.getInstance(this).getUserName();
        String trimmedName = storedName == null ? "" : storedName.trim();
        String firstName = trimmedName.isEmpty()
                ? getString(R.string.app_name)
                : trimmedName.split("\\s+", 2)[0];

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int greeting = hour < 12 ? R.string.today_greeting_morning
                : hour < 18 ? R.string.today_greeting_afternoon : R.string.today_greeting_evening;
        tvWelcomeTitle.setText(getString(greeting, firstName));
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
    }

    private void navigateToWeek() {
        BottomNavHelper.navigateTo(this, BottomNavHelper.Tab.TODAY, BottomNavHelper.Tab.WEEK);
    }

    private void navigateToTasks() {
        BottomNavHelper.navigateTo(this, BottomNavHelper.Tab.TODAY, BottomNavHelper.Tab.TASKS);
    }

    private void loadDashboardData() {
        if (loadInProgress) return;
        loadInProgress = true;
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                List<ScheduleItem> allSchedules = db.scheduleDao().getAll();
                List<AssignmentItem> allAssignments = db.assignmentDao().getPendingAssignments();
                runOnUiThread(() -> {
                    loadInProgress = false;
                    renderDashboard(allSchedules, allAssignments);
                });
            } catch (RuntimeException error) {
                Log.e(TAG, "Unable to load the Today screen data.", error);
                runOnUiThread(() -> {
                    loadInProgress = false;
                    Toast.makeText(this, R.string.today_loading_error, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void renderDashboard(List<ScheduleItem> schedules, List<AssignmentItem> assignments) {
        computeWeeklyLoad(schedules);
        evaluateRealConflicts(schedules);
        renderTodaySchedule(schedules);
        renderPrioritizedTasks(assignments, schedules);
        cardFocusTasks.setVisibility(View.VISIBLE);
        cardTodaySchedule.setVisibility(View.VISIBLE);
    }

    private void computeWeeklyLoad(List<ScheduleItem> schedules) {
        SupabaseClient client = SupabaseClient.getInstance(this);
        int weeklyLimit = Math.max(1, client.getWeeklyHoursLimit());
        double totalHours = 0;
        double classHours = 0;
        double workHours = 0;

        if (schedules != null) {
            for (ScheduleItem item : schedules) {
                int start = ConflictChecker.timeToMinutes(item.getStartTime());
                int end = ConflictChecker.timeToMinutes(item.getEndTime());
                if (end <= start) continue;
                double hours = (end - start) / 60.0;
                totalHours += hours;
                if (item.isWork()) workHours += hours;
                else classHours += hours;
            }
        }

        int roundedTotal = (int) Math.round(totalHours);
        int roundedClass = (int) Math.round(classHours);
        int roundedWork = (int) Math.round(workHours);
        tvWorkloadValue.setText(getString(R.string.today_hours_of_limit, roundedTotal, weeklyLimit));

        boolean isHigh = totalHours > weeklyLimit;
        int statusText;
        int fillColor;
        if (isHigh) {
            statusText = R.string.today_load_high;
            fillColor = R.color.brand_raspberry;
            if (client.isAlertBurnoutEnabled()) {
                int overLimit = Math.max(1, (int) Math.round(totalHours - weeklyLimit));
                tvWorkloadHours.setText(getString(R.string.today_over_limit, overLimit, weeklyLimit));
            } else {
                tvWorkloadHours.setText(getString(
                        R.string.today_class_work_hours, roundedClass, roundedWork));
            }
        } else if (totalHours < weeklyLimit * 0.75) {
            statusText = R.string.today_load_low;
            fillColor = R.color.brand_purple;
            tvWorkloadHours.setText(getString(
                    R.string.today_class_work_hours, roundedClass, roundedWork));
        } else {
            statusText = R.string.today_load_moderate;
            fillColor = R.color.brand_purple;
            tvWorkloadHours.setText(getString(
                    R.string.today_class_work_hours, roundedClass, roundedWork));
        }

        tvWorkloadStatus.setText(statusText);
        tvWorkloadStatus.setTextColor(ContextCompat.getColor(this, fillColor));
        progressWeeklyLoad.setProgressTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, fillColor)));
        int progress = (int) Math.round(Math.min(totalHours / weeklyLimit, 1.0) * 100.0);
        progressWeeklyLoad.setProgress(progress);
        layoutWorkloadCard.setVisibility(View.VISIBLE);
    }

    private void evaluateRealConflicts(List<ScheduleItem> schedules) {
        if (!SupabaseClient.getInstance(this).isAlertConflictsEnabled()
                || schedules == null || schedules.size() < 2) {
            layoutConflictCard.setVisibility(View.GONE);
            return;
        }

        ScheduleItem clashA = null;
        ScheduleItem clashB = null;
        for (int i = 0; i < schedules.size() && clashA == null; i++) {
            for (int j = i + 1; j < schedules.size(); j++) {
                ScheduleItem first = schedules.get(i);
                ScheduleItem second = schedules.get(j);
                if (ConflictChecker.hasConflict(first.getDay(), first.getStartTime(), first.getEndTime(),
                        second.getDay(), second.getStartTime(), second.getEndTime())) {
                    clashA = first;
                    clashB = second;
                    break;
                }
            }
        }

        if (clashA == null) {
            layoutConflictCard.setVisibility(View.GONE);
            return;
        }

        final ScheduleItem firstConflict = clashA;
        final ScheduleItem secondConflict = clashB;
        String day = firstConflict.getDay();
        String nameA = firstConflict.getSubject();
        String nameB = secondConflict.getSubject();
        tvConflictHeading.setText(getString(R.string.today_conflict_heading, day));
        tvConflictDetails.setText(getString(R.string.today_conflict_details,
                nameA, nameB, firstConflict.getStartTime(), firstConflict.getEndTime()));
        layoutConflictCard.setVisibility(View.VISIBLE);
        btnSuggestAction.setOnClickListener(v -> sendPushNotification(getString(
                R.string.today_conflict_details, nameA, nameB,
                firstConflict.getStartTime(), firstConflict.getEndTime())));
    }

    private void renderTodaySchedule(List<ScheduleItem> allSchedules) {
        containerTodaySchedule.removeAllViews();
        String todayCode = PriorityEngine.getDayCodeFromTimestamp(System.currentTimeMillis());
        List<ScheduleItem> todayItems = new ArrayList<>();
        if (allSchedules != null) {
            for (ScheduleItem item : allSchedules) {
                if (item.getDay() != null && item.getDay().equalsIgnoreCase(todayCode)) {
                    todayItems.add(item);
                }
            }
        }
        todayItems.sort(Comparator.comparingInt(
                item -> ConflictChecker.timeToMinutes(item.getStartTime())));

        if (todayItems.isEmpty()) {
            layoutEmptySchedulePlaceholder.setVisibility(View.VISIBLE);
            return;
        }

        layoutEmptySchedulePlaceholder.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < todayItems.size(); i++) {
            ScheduleItem item = todayItems.get(i);
            View row = inflater.inflate(R.layout.item_today_schedule, containerTodaySchedule, false);
            if (i == todayItems.size() - 1) {
                row.setPadding(row.getPaddingLeft(), row.getPaddingTop(), row.getPaddingRight(), 0);
            }
            View bar = row.findViewById(R.id.todayScheduleBar);
            int barColor = ContextCompat.getColor(this,
                    item.isWork() ? R.color.work : R.color.school);
            ViewCompat.setBackgroundTintList(bar, ColorStateList.valueOf(barColor));

            TextView title = row.findViewById(R.id.tvTodayScheduleTitle);
            title.setText(item.getSubject());
            TextView details = row.findViewById(R.id.tvTodayScheduleDetails);
            String timeRange = formatTimeRange(item.getStartTime(), item.getEndTime());
            details.setText(getString(item.isWork()
                    ? R.string.today_schedule_work : R.string.today_schedule_class, timeRange));
            containerTodaySchedule.addView(row);
        }
    }

    private void renderPrioritizedTasks(List<AssignmentItem> pendingTasks,
                                        List<ScheduleItem> schedules) {
        containerPriorityTasks.removeAllViews();
        if (pendingTasks == null || pendingTasks.isEmpty()) {
            layoutEmptyTasksPlaceholder.setVisibility(View.VISIBLE);
            return;
        }

        layoutEmptyTasksPlaceholder.setVisibility(View.GONE);
        boolean collisionAlerts = SupabaseClient.getInstance(this).isAlertCollisionsEnabled();
        List<AssignmentItem> prioritized = PriorityEngine.calculateAndSort(pendingTasks, schedules);
        LayoutInflater inflater = LayoutInflater.from(this);
        int count = Math.min(prioritized.size(), 3);
        for (int i = 0; i < count; i++) {
            AssignmentItem task = prioritized.get(i);
            View row = inflater.inflate(R.layout.item_today_task, containerPriorityTasks, false);
            if (i == count - 1) {
                row.setPadding(row.getPaddingLeft(), row.getPaddingTop(), row.getPaddingRight(), 0);
            }

            long dueTimestamp = resolveDueTimestamp(task);
            boolean overdue = dueTimestamp > 0 && dueTimestamp < System.currentTimeMillis();
            boolean hasCollision = collisionAlerts && task.isHasWorkCollision();

            TextView rank = row.findViewById(R.id.tvTodayTaskRank);
            rank.setText(String.valueOf(i + 1));
            if (overdue || (i == 0 && hasCollision)) {
                rank.setBackgroundResource(R.drawable.bg_today_rank_alert);
                rank.setTextColor(ContextCompat.getColor(this, R.color.brand_raspberry));
            } else {
                rank.setBackgroundResource(R.drawable.bg_today_rank_default);
                rank.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }

            TextView title = row.findViewById(R.id.tvTodayTaskTitle);
            title.setText(task.getTitle());
            TextView due = row.findViewById(R.id.tvTodayTaskDue);
            due.setText(getDueLabel(task, dueTimestamp));
            if (overdue) due.setTextColor(ContextCompat.getColor(this, R.color.brand_raspberry));

            TextView collision = row.findViewById(R.id.tvTodayTaskCollision);
            collision.setVisibility(hasCollision ? View.VISIBLE : View.GONE);
            containerPriorityTasks.addView(row);
        }
    }

    private long resolveDueTimestamp(AssignmentItem task) {
        if (task.getDueTimestamp() > 0) return task.getDueTimestamp();
        String date = task.getDueDate();
        String time = task.getDueTime();
        if (date == null || time == null) return 0;
        String[] patterns = {"yyyy-MM-dd hh:mm a", "yyyy-MM-dd h:mm a", "yyyy-MM-dd HH:mm"};
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.getDefault());
            format.setLenient(false);
            try {
                Date parsed = format.parse(date + " " + time);
                if (parsed != null) return parsed.getTime();
            } catch (ParseException ignored) {
                // Try the next format for older task records.
            }
        }
        return 0;
    }

    private String getDueLabel(AssignmentItem task, long dueTimestamp) {
        if (dueTimestamp <= 0) {
            String dueDate = task.getDueDate();
            if (dueDate == null || dueDate.trim().isEmpty()) return "";
            return getString(R.string.today_due_date, dueDate);
        }

        long difference = dueTimestamp - System.currentTimeMillis();
        long hourMillis = 60L * 60L * 1000L;
        long dayMillis = 24L * hourMillis;
        if (difference < 0) {
            long overdueMillis = -difference;
            if (overdueMillis < dayMillis) {
                int hours = Math.max(1, (int) Math.round(overdueMillis / (double) hourMillis));
                return getString(hours == 1 ? R.string.today_overdue_hours
                        : R.string.today_overdue_hours_plural, hours);
            }
            int days = Math.max(1, (int) (overdueMillis / dayMillis));
            return getString(days == 1 ? R.string.today_overdue_days
                    : R.string.today_overdue_days_plural, days);
        }
        if (difference < hourMillis) return getString(R.string.today_due_under_hour);
        if (difference < dayMillis) {
            int hours = Math.min(23, Math.max(1, (int) Math.round(difference / (double) hourMillis)));
            return getString(hours == 1 ? R.string.today_due_hours
                    : R.string.today_due_hours_plural, hours);
        }
        if (difference <= 7L * dayMillis) {
            int days = (int) (difference / dayMillis);
            return getString(days == 1 ? R.string.today_due_days
                    : R.string.today_due_days_plural, days);
        }
        return getString(R.string.today_due_date,
                new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(dueTimestamp)));
    }

    private String formatTimeRange(String start, String end) {
        String formattedStart = formatScheduleTime(start);
        String formattedEnd = formatScheduleTime(end);
        String startPeriod = periodSuffix(formattedStart);
        String endPeriod = periodSuffix(formattedEnd);
        if (!startPeriod.isEmpty() && startPeriod.equals(endPeriod)) {
            formattedStart = formattedStart.substring(0, formattedStart.length() - startPeriod.length()).trim();
        }
        return formattedStart + " - " + formattedEnd;
    }

    private String formatScheduleTime(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        String[] patterns = {"h:mm a", "hh:mm a", "H:mm", "HH:mm"};
        for (String pattern : patterns) {
            SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.getDefault());
            parser.setLenient(false);
            try {
                Date parsed = parser.parse(trimmed);
                if (parsed != null) {
                    return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(parsed);
                }
            } catch (ParseException ignored) {
                // Preserve time values that use a format not recognized here.
            }
        }
        return trimmed;
    }

    private String periodSuffix(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.endsWith(" AM")) return "AM";
        if (upper.endsWith(" PM")) return "PM";
        return "";
    }

    private void sendPushNotification(String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(getString(R.string.today_conflict_notification_title))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(404, builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        CharSequence name = getString(R.string.today_notification_channel_name);
        String description = getString(R.string.today_notification_channel_description);
        int importance = android.app.NotificationManager.IMPORTANCE_HIGH;
        android.app.NotificationChannel channel =
                new android.app.NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }
}
