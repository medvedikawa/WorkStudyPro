package com.example.wspro;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase db;
    private NotificationAdapter adapter;
    private RecyclerView rvNotifications;
    private LinearLayout layoutEmptyNotifications;
    private TextView tvMarkAllRead;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        db = AppDatabase.getInstance(this);

        ImageButton btnReturnBack = findViewById(R.id.btnReturnBack);
        tvMarkAllRead = findViewById(R.id.tvMarkAllRead);
        tvMarkAllRead.setVisibility(View.GONE);
        rvNotifications = findViewById(R.id.rvNotifications);
        layoutEmptyNotifications = findViewById(R.id.layoutEmptyNotifications);

        btnReturnBack.setOnClickListener(v -> finish());

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(this::handleNotificationClick);
        rvNotifications.setAdapter(adapter);

        tvMarkAllRead.setOnClickListener(v -> {
            executor.execute(db.notificationDao()::markAllAsRead);
            Toast.makeText(this, "Marked all as read", Toast.LENGTH_SHORT).show();
        });

        db.notificationDao().getAllNotificationsLiveData().observe(this, notifications -> {
            if (notifications == null || notifications.isEmpty()) {
                rvNotifications.setVisibility(View.GONE);
                layoutEmptyNotifications.setVisibility(View.VISIBLE);
            } else {
                layoutEmptyNotifications.setVisibility(View.GONE);
                rvNotifications.setVisibility(View.VISIBLE);
                adapter.setItems(notifications);
            }
        });
        db.notificationDao().getUnreadCountLiveData().observe(this, count ->
                tvMarkAllRead.setVisibility(count != null && count > 0 ? View.VISIBLE : View.GONE));
    }

    private void handleNotificationClick(NotificationItem item) {
        executor.execute(() -> db.notificationDao().markAsRead(item.getId()));

        String type = item.getType();
        final Intent intent;
        if (NotificationItem.TYPE_DEADLINE.equals(type)) {
            intent = new Intent(this, TasksActivity.class);
        } else if (NotificationItem.TYPE_CONFLICT.equals(type) || NotificationItem.TYPE_WORKLOAD.equals(type)) {
            intent = new Intent(this, WeekActivity.class);
        } else {
            return;
        }
        intent.putExtra(NotificationHelper.EXTRA_TYPE, type);
        intent.putExtra(NotificationHelper.EXTRA_RELATED_ID, item.getRelatedId());
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
