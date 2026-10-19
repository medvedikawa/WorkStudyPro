package com.example.wspro.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wspro.R;
import com.example.wspro.ScheduleItem;

import java.util.List;

public class HourTimelineAdapter extends RecyclerView.Adapter<HourTimelineAdapter.HourViewHolder> {

    private List<ScheduleItem> daySchedules;

    public HourTimelineAdapter(List<ScheduleItem> daySchedules) {
        this.daySchedules = daySchedules;
    }

    public void updateData(List<ScheduleItem> newSchedules) {
        this.daySchedules = newSchedules;
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
        // Generate hour string for 0 to 23 (12 AM to 11 PM)
        String hourLabel = formatHourLabel(position);
        holder.tvHourLabel.setText(hourLabel);

        // Clear previous views in the slot container
        holder.layoutHourSchedules.removeAllViews();

        // Check if any schedule matches this hour slot
        for (ScheduleItem schedule : daySchedules) {
            if (isScheduleInHour(schedule, position)) {
                // Inflate a pill view for the class or work shift
                View schedulePill = LayoutInflater.from(holder.itemView.getContext())
                        .inflate(R.layout.item_schedule_pill, holder.layoutHourSchedules, false);

                TextView tvTitle = schedulePill.findViewById(R.id.tvScheduleTitle);
                String timeRange = schedule.getStartTime() + " - " + schedule.getEndTime();
                tvTitle.setText(schedule.getSubject() + " (" + timeRange + ")");

                // Style based on type (e.g., class vs work shift colors)
                if (schedule.isWork()) {
                    schedulePill.setBackgroundResource(R.drawable.bg_work_pill);
                } else {
                    schedulePill.setBackgroundResource(R.drawable.bg_class_pill);
                }

                holder.layoutHourSchedules.addView(schedulePill);
            }
        }
    }

    @Override
    public int getItemCount() {
        return 24; // 24 hours from 12 AM to 11 PM
    }

    private String formatHourLabel(int hour24) {
        if (hour24 == 0) return "12 AM";
        if (hour24 < 12) return hour24 + " AM";
        if (hour24 == 12) return "12 PM";
        return (hour24 - 12) + " PM";
    }

    private boolean isScheduleInHour(ScheduleItem schedule, int hour24) {
        try {
            // schedule.getStartTime() is in "HH:mm" format (e.g., "08:00" or "14:30")
            String startHourStr = schedule.getStartTime().split(":")[0];
            int startHour = Integer.parseInt(startHourStr);
            return startHour == hour24;
        } catch (Exception e) {
            return false;
        }
    }

    static class HourViewHolder extends RecyclerView.ViewHolder {
        TextView tvHourLabel;
        LinearLayout layoutHourSchedules;

        public HourViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHourLabel = itemView.findViewById(R.id.tvHourLabel);
            layoutHourSchedules = itemView.findViewById(R.id.layoutHourSchedules);
        }
    }
}