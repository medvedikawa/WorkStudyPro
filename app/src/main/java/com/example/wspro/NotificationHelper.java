package com.example.wspro;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class NotificationHelper {

    public static final String CHANNEL_DEADLINES = "channel_deadlines";
    public static final String CHANNEL_WORKLOAD_CONFLICTS = "channel_workload_conflicts";
    public static final String EXTRA_TYPE = "notification_type";
    public static final String EXTRA_RELATED_ID = "notification_related_id";

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        // 1. Deadlines Channel
        NotificationChannel deadlinesChannel = new NotificationChannel(
                CHANNEL_DEADLINES,
                "Deadlines",
                NotificationManager.IMPORTANCE_HIGH
        );
        deadlinesChannel.setDescription("Upcoming task and assignment deadline reminders");
        manager.createNotificationChannel(deadlinesChannel);

        // 2. Workload & Conflicts Channel
        NotificationChannel workloadChannel = new NotificationChannel(
                CHANNEL_WORKLOAD_CONFLICTS,
                "Workload & Conflicts",
                NotificationManager.IMPORTANCE_HIGH
        );
        workloadChannel.setDescription("Schedule overlap conflicts and excessive workload alerts");
        manager.createNotificationChannel(workloadChannel);
    }

    public static void postNotification(Context context, int notificationId, String type,
                                        String title, String message, String relatedId) {
        if (context == null) return;
        createChannels(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        String channelId = NotificationItem.TYPE_DEADLINE.equals(type)
                ? CHANNEL_DEADLINES
                : CHANNEL_WORKLOAD_CONFLICTS;

        Intent targetIntent;
        if (NotificationItem.TYPE_DEADLINE.equals(type)) {
            targetIntent = new Intent(context, TasksActivity.class);
        } else {
            targetIntent = new Intent(context, WeekActivity.class);
        }
        targetIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        targetIntent.putExtra(EXTRA_TYPE, type);
        if (relatedId != null) {
            targetIntent.putExtra(EXTRA_RELATED_ID, relatedId);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                targetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_today_notifications)
                .setColor(ContextCompat.getColor(context, R.color.brand_purple))
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    public static void cancelAll(Context context) {
        if (context == null) return;
        NotificationManagerCompat.from(context).cancelAll();
    }
}
