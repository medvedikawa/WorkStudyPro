package com.example.wspro;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    public interface OnNotificationClickListener {
        void onNotificationClick(NotificationItem item);
    }

    private final List<NotificationItem> items = new ArrayList<>();
    private final OnNotificationClickListener listener;

    public NotificationAdapter(OnNotificationClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<NotificationItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationItem item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        private final View cardNotificationItem;
        private final ImageView ivNotificationIcon;
        private final TextView tvNotificationTitle;
        private final TextView tvNotificationTime;
        private final TextView tvNotificationMessage;
        private final View viewItemUnreadDot;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            cardNotificationItem = itemView.findViewById(R.id.cardNotificationItem);
            ivNotificationIcon = itemView.findViewById(R.id.ivNotificationIcon);
            tvNotificationTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvNotificationTime = itemView.findViewById(R.id.tvNotificationTime);
            tvNotificationMessage = itemView.findViewById(R.id.tvNotificationMessage);
            viewItemUnreadDot = itemView.findViewById(R.id.viewItemUnreadDot);
        }

        public void bind(NotificationItem item, OnNotificationClickListener listener) {
            Context context = itemView.getContext();

            tvNotificationTitle.setText(item.getTitle() != null ? item.getTitle() : "");
            tvNotificationMessage.setText(item.getMessage() != null ? item.getMessage() : "");
            tvNotificationTime.setText(formatRelativeTime(item.getCreatedAt()));

            // Read / unread appearance
            boolean isUnread = !item.isRead();
            viewItemUnreadDot.setVisibility(isUnread ? View.VISIBLE : View.GONE);
            cardNotificationItem.setBackgroundResource(
                    isUnread ? R.drawable.bg_notif_item_unread : R.drawable.bg_notif_item_read
            );
            tvNotificationTitle.setTypeface(null, isUnread ? Typeface.BOLD : Typeface.NORMAL);

            // Type icon styling
            String type = item.getType() != null ? item.getType() : "";
            if (NotificationItem.TYPE_DEADLINE.equals(type)) {
                ivNotificationIcon.setImageResource(R.drawable.ic_notif_deadline);
                ivNotificationIcon.setColorFilter(ContextCompat.getColor(context, R.color.ws_accent));
            } else if (NotificationItem.TYPE_CONFLICT.equals(type)) {
                ivNotificationIcon.setImageResource(R.drawable.ic_notif_conflict);
                ivNotificationIcon.setColorFilter(ContextCompat.getColor(context, R.color.ws_fire));
            } else if (NotificationItem.TYPE_WORKLOAD.equals(type)) {
                ivNotificationIcon.setImageResource(R.drawable.ic_notif_workload);
                ivNotificationIcon.setColorFilter(ContextCompat.getColor(context, R.color.ws_accent2));
            } else {
                ivNotificationIcon.setImageResource(R.drawable.ic_today_notifications);
                ivNotificationIcon.setColorFilter(ContextCompat.getColor(context, R.color.ws_accent));
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNotificationClick(item);
                }
            });
        }

        private String formatRelativeTime(long timestamp) {
            if (timestamp <= 0) return "";
            long now = System.currentTimeMillis();
            long diff = Math.max(0, now - timestamp);

            if (diff < 60_000L) {
                return "Just now";
            }
            if (diff < 3600_000L) {
                long minutes = diff / 60_000L;
                return minutes + "m ago";
            }
            if (diff < 24 * 3600_000L) {
                long hours = diff / 3600_000L;
                return hours + "h ago";
            }
            if (diff < 48 * 3600_000L) {
                return "Yesterday";
            }

            return new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(new Date(timestamp));
        }
    }
}
