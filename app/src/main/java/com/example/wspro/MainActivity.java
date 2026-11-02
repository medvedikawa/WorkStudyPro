package com.example.wspro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvDateHeader;
    private TextView tvWelcomeTitle;
    private TextView tvWorkloadStatus;
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

    private List<ScheduleItem> cachedSchedules = new ArrayList<>();
    private List<AssignmentItem> cachedAssignments = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvDateHeader = findViewById(R.id.tvDateHeader);
        tvWelcomeTitle = findViewById(R.id.tvWelcomeTitle);
        tvWorkloadStatus = findViewById(R.id.tvWorkloadStatus);
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

        View btnNotificationContainer = findViewById(R.id.btnNotificationContainer);
        if (btnNotificationContainer != null) {
            btnNotificationContainer.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });
        }
        View btnQuickNotification = findViewById(R.id.btnQuickNotification);
        if (btnQuickNotification != null) {
            btnQuickNotification.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });
        }

        BottomNavHelper.setup(this, BottomNavHelper.Tab.TODAY, new ScanButtonHelper(this));
        requestNotificationPermission();

        setupReactiveObservers();
    }

    private void setupReactiveObservers() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        db.scheduleDao().getAllLiveData().observe(this, schedules -> {
            cachedSchedules = schedules != null ? schedules : new ArrayList<>();
            renderDashboard(cachedSchedules, cachedAssignments);
        });
        db.assignmentDao().getPendingAssignmentsLiveData().observe(this, assignments -> {
            cachedAssignments = assignments != null ? assignments : new ArrayList<>();
            renderDashboard(cachedSchedules, cachedAssignments);
        });

        View viewNotificationUnreadDot = findViewById(R.id.viewNotificationUnreadDot);
        db.notificationDao().getUnreadCountLiveData().observe(this, count -> {
            if (viewNotificationUnreadDot != null) {
                viewNotificationUnreadDot.setVisibility(count != null && count > 0 ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderHeader();
        renderDashboard(cachedSchedules, cachedAssignments);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                boolean rationaleShown = getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .getBoolean("notif_rationale_shown", false);
                if (!rationaleShown) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.today_notification_permission_title)
                            .setMessage(R.string.today_notification_permission_message)
                            .setPositiveButton(R.string.today_notification_permission_enable, (dialog, which) -> {
                                getSharedPreferences("app_prefs", MODE_PRIVATE)
                                        .edit().putBoolean("notif_rationale_shown", true).apply();
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                            })
                            .setNegativeButton(R.string.today_notification_permission_not_now, (dialog, which) -> {
                                getSharedPreferences("app_prefs", MODE_PRIVATE)
                                        .edit().putBoolean("notif_rationale_shown", true).apply();
                                dialog.dismiss();
                            })
                            .show();
                }
            }
        }
    }

    private void navigateToWeek() {
        BottomNavHelper.navigateTo(this, BottomNavHelper.Tab.TODAY, BottomNavHelper.Tab.WEEK);
    }

    private void navigateToTasks() {
        BottomNavHelper.navigateTo(this, BottomNavHelper.Tab.TODAY, BottomNavHelper.Tab.TASKS);
    }

    private void renderDashboard(List<ScheduleItem> schedules, List<AssignmentItem> assignments) {
        renderWeekStrip(schedules);
        computeWeeklyLoad(schedules);
        evaluateRealConflicts(schedules);
        renderTodaySchedule(schedules);
        renderPrioritizedTasks(assignments, schedules);
        cardFocusTasks.setVisibility(View.VISIBLE);
        cardTodaySchedule.setVisibility(View.VISIBLE);
    }

    private void renderWeekStrip(List<ScheduleItem> schedules) {
        Calendar cal = Calendar.getInstance();
        int todayYear = cal.get(Calendar.YEAR);
        int todayDayOfYear = cal.get(Calendar.DAY_OF_YEAR);

        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);

        String[] dayNames = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
        String[] fullDays = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        String[] shortCodes = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};

        int[] chipIds = {R.id.chipDay0, R.id.chipDay1, R.id.chipDay2, R.id.chipDay3, R.id.chipDay4, R.id.chipDay5, R.id.chipDay6};
        int[] tvDayIds = {R.id.tvChipDay0, R.id.tvChipDay1, R.id.tvChipDay2, R.id.tvChipDay3, R.id.tvChipDay4, R.id.tvChipDay5, R.id.tvChipDay6};
        int[] tvDateIds = {R.id.tvChipDate0, R.id.tvChipDate1, R.id.tvChipDate2, R.id.tvChipDate3, R.id.tvChipDate4, R.id.tvChipDate5, R.id.tvChipDate6};
        int[] vDotIds = {R.id.vChipDot0, R.id.vChipDot1, R.id.vChipDot2, R.id.vChipDot3, R.id.vChipDot4, R.id.vChipDot5, R.id.vChipDot6};

        for (int i = 0; i < 7; i++) {
            View chipView = findViewById(chipIds[i]);
            TextView tvDay = findViewById(tvDayIds[i]);
            TextView tvDate = findViewById(tvDateIds[i]);
            View vDot = findViewById(vDotIds[i]);

            if (chipView == null || tvDay == null || tvDate == null || vDot == null) continue;

            int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
            tvDay.setText(dayNames[i]);
            tvDate.setText(String.valueOf(dayOfMonth));

            boolean isToday = cal.get(Calendar.YEAR) == todayYear && cal.get(Calendar.DAY_OF_YEAR) == todayDayOfYear;

            boolean hasSchedule = false;
            if (schedules != null) {
                String fullDayStr = fullDays[i].toLowerCase(Locale.ROOT);
                String shortCodeStr = shortCodes[i].toLowerCase(Locale.ROOT);
                for (ScheduleItem item : schedules) {
                    if (item.getDay() != null) {
                        String d = item.getDay().toLowerCase(Locale.ROOT);
                        if (d.contains(fullDayStr) || d.contains(shortCodeStr) || d.equals(String.valueOf(i))) {
                            hasSchedule = true;
                            break;
                        }
                    }
                }
            }

            if (isToday) {
                chipView.setBackgroundResource(R.drawable.bg_calendar_chip_today);
                tvDay.setTextColor(ContextCompat.getColor(this, R.color.on_brand));
                tvDay.setTypeface(null, android.graphics.Typeface.BOLD);
                tvDate.setTextColor(ContextCompat.getColor(this, R.color.on_brand));
                tvDate.setTypeface(null, android.graphics.Typeface.BOLD);
                if (hasSchedule) {
                    vDot.setVisibility(View.VISIBLE);
                    vDot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.on_brand)));
                } else {
                    vDot.setVisibility(View.INVISIBLE);
                }
            } else {
                chipView.setBackgroundResource(R.drawable.bg_calendar_chip_normal);
                tvDay.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
                tvDay.setTypeface(null, android.graphics.Typeface.NORMAL);
                tvDate.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                tvDate.setTypeface(null, android.graphics.Typeface.BOLD);
                if (hasSchedule) {
                    vDot.setVisibility(View.VISIBLE);
                    vDot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_purple)));
                } else {
                    vDot.setVisibility(View.INVISIBLE);
                }
            }

            final String dayCodeToOpen = shortCodes[i];
            chipView.setOnClickListener(v -> {
                Intent intent = new Intent(this, WeekActivity.class);
                intent.putExtra("SELECTED_DAY", dayCodeToOpen);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });

            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    private void computeWeeklyLoad(List<ScheduleItem> schedules) {
        SupabaseClient client = SupabaseClient.getInstance(this);
        LoadCalculator.LoadResult result = LoadCalculator.calculate(schedules, client.getWeeklyHoursLimit());

        TextView tvWorkloadNumber = findViewById(R.id.tvWorkloadNumber);
        TextView tvWorkloadLimit = findViewById(R.id.tvWorkloadLimit);
        if (tvWorkloadNumber != null) {
            tvWorkloadNumber.setText(String.valueOf(result.getRoundedTotal()));
        }
        if (tvWorkloadLimit != null) {
            tvWorkloadLimit.setText(getString(R.string.today_limit_hours, result.weeklyLimit));
        }

        int statusText;
        int fillColor;
        int bgDrawable;
        if (result.isHigh()) {
            statusText = R.string.today_load_high;
            fillColor = R.color.brand_raspberry;
            bgDrawable = R.drawable.bg_load_band_raspberry;
            if (client.isAlertBurnoutEnabled()) {
                int overLimit = Math.max(1, (int) Math.round(result.totalHours - result.weeklyLimit));
                tvWorkloadHours.setText(getString(R.string.today_over_limit, overLimit, result.weeklyLimit));
            } else {
                tvWorkloadHours.setText(getString(
                        R.string.today_class_work_hours, result.getRoundedClass(), result.getRoundedWork()));
            }
        } else if (result.band == LoadCalculator.LoadBand.LOW) {
            statusText = R.string.today_load_low;
            fillColor = R.color.brand_purple;
            bgDrawable = R.drawable.bg_load_band_purple;
            tvWorkloadHours.setText(getString(
                    R.string.today_class_work_hours, result.getRoundedClass(), result.getRoundedWork()));
        } else {
            statusText = R.string.today_load_moderate;
            fillColor = R.color.brand_purple;
            bgDrawable = R.drawable.bg_load_band_purple;
            tvWorkloadHours.setText(getString(
                    R.string.today_class_work_hours, result.getRoundedClass(), result.getRoundedWork()));
        }

        tvWorkloadStatus.setText(statusText);
        tvWorkloadStatus.setTextColor(ContextCompat.getColor(this, fillColor));
        tvWorkloadStatus.setBackgroundResource(bgDrawable);
        progressWeeklyLoad.setProgressTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, fillColor)));
        int progress = (int) Math.round(Math.min(result.totalHours / result.weeklyLimit, 1.0) * 100.0);
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
        btnSuggestAction.setOnClickListener(v -> navigateToWeek());
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

            TextView tvStart = row.findViewById(R.id.tvTodayScheduleStart);
            TextView tvEnd = row.findViewById(R.id.tvTodayScheduleEnd);
            if (tvStart != null) tvStart.setText(formatScheduleTime(item.getStartTime()));
            if (tvEnd != null) tvEnd.setText(formatScheduleTime(item.getEndTime()));

            TextView title = row.findViewById(R.id.tvTodayScheduleTitle);
            title.setText(item.getSubject());

            TextView category = row.findViewById(R.id.tvTodayScheduleCategory);
            if (category != null) {
                category.setText(item.isWork() ? "Work" : "Class");
            }
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
            long difference = dueTimestamp > 0 ? dueTimestamp - System.currentTimeMillis() : Long.MAX_VALUE;
            boolean urgent = dueTimestamp > 0 && (difference < 24L * 60L * 60L * 1000L);
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
            if (urgent) {
                due.setTextAppearance(R.style.WS_UrgentDue);
            } else {
                due.setTextAppearance(R.style.WS_Due);
            }

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
            }
        }
        return trimmed;
    }

}
