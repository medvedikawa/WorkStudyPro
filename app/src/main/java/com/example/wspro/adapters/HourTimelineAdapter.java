package com.example.wspro.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.wspro.ConflictChecker;
import com.example.wspro.R;
import com.example.wspro.ScheduleItem;
import java.util.List;

public class HourTimelineAdapter extends RecyclerView.Adapter<HourTimelineAdapter.HourViewHolder> {

    private List<ScheduleItem> scheduleItems;

    public HourTimelineAdapter(List<ScheduleItem> scheduleItems) {
        this.scheduleItems = scheduleItems;
    }

    public void updateData(List<ScheduleItem> newSchedules) {
        this.scheduleItems = newSchedules;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HourViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hour_slot, parent, false);
        return new HourViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HourViewHolder holder, int position) {
        String hourLabel = formatHourLabel(position);
        holder.tvHour.setText(hourLabel);

        holder.layoutEventContainer.removeAllViews();

        if (scheduleItems != null) {
            for (ScheduleItem item : scheduleItems) {
                if (isValidTime(item.getStartTime())) {
                    if (doesScheduleMatchHour(item, position)) {
                        View pillView = LayoutInflater.from(holder.itemView.getContext())
                                .inflate(R.layout.item_schedule_pill, holder.layoutEventContainer, false);

                        TextView tvTitle = pillView.findViewById(R.id.tvScheduleTitle);
                        TextView tvTime = pillView.findViewById(R.id.tvScheduleTime);

                        if (tvTitle != null) {
                            tvTitle.setText(item.getSubject());
                            tvTitle.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
                        }
                        if (tvTime != null) {
                            tvTime.setText(item.getStartTime() + " - " + item.getEndTime());
                            tvTime.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
                        }

                        String category = item.getCategory();
                        if ("Class".equalsIgnoreCase(category)) {
                            pillView.setBackgroundResource(R.drawable.bg_class_pill);
                        } else if ("Work".equalsIgnoreCase(category)) {
                            pillView.setBackgroundResource(R.drawable.bg_work_pill);
                        } else {
                            pillView.setBackgroundResource(R.drawable.bg_other_pill);
                        }

                        holder.layoutEventContainer.addView(pillView);
                    }
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return 24;
    }

    private boolean isValidTime(String time) {
        if (time == null || time.trim().isEmpty()) return false;
        String t = time.trim().toUpperCase();
        return ConflictChecker.timeToMinutes(t) >= 0;
    }

    private String formatHourLabel(int hour) {
        if (hour == 0) return "12 AM";
        if (hour < 12) return hour + " AM";
        if (hour == 12) return "12 PM";
        return (hour - 12) + " PM";
    }

    private boolean doesScheduleMatchHour(ScheduleItem item, int hourIndex) {
        int startMin = ConflictChecker.timeToMinutes(item.getStartTime());
        int endMin = ConflictChecker.timeToMinutes(item.getEndTime());
        if (endMin <= startMin) {
            endMin = startMin + 60;
        }
        int slotStart = hourIndex * 60;
        int slotEnd = (hourIndex + 1) * 60;
        return Math.max(startMin, slotStart) < Math.min(endMin, slotEnd);
    }

    public static class HourViewHolder extends RecyclerView.ViewHolder {
        TextView tvHour;
        LinearLayout layoutEventContainer;

        public HourViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHour = itemView.findViewById(R.id.tvHourLabel);
            layoutEventContainer = itemView.findViewById(R.id.layoutHourSchedules);
        }
    }
}
