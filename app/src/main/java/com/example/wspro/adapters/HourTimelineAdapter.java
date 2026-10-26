package com.example.wspro.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HourTimelineAdapter extends RecyclerView.Adapter<HourTimelineAdapter.HourViewHolder> {

    private List<ScheduleItem> scheduleItems;

    // Regex to strictly match scanned time format: e.g., "10:30 AM" or "2:00 PM"
    private static final String TIME_REGEX = "^(1[0-2]|0?[1-9]):[0-5][0-9]\\s?(AM|PM|am|pm)$";
    private static final Pattern timePattern = Pattern.compile(TIME_REGEX);

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
                // Only process if both Start and End times are valid scanned times
                if (isValidTime(item.getStartTime()) && isValidTime(item.getEndTime())) {
                    if (doesScheduleMatchHour(item, position)) {
                        View pillView = LayoutInflater.from(holder.itemView.getContext())
                                .inflate(R.layout.item_schedule_pill, holder.layoutEventContainer, false);

                        TextView tvTitle = pillView.findViewById(R.id.tvScheduleTitle);
                        TextView tvTime = pillView.findViewById(R.id.tvScheduleTime);

                        if (tvTitle != null) {
                            tvTitle.setText(item.getSubject());
                            tvTitle.setTextColor(androidx.core.content.ContextCompat.getColor(
                                    holder.itemView.getContext(), item.isWork()
                                            ? R.color.schedule_work_on : R.color.schedule_school_on));
                        }
                        if (tvTime != null) {
                            tvTime.setText(item.getStartTime() + " - " + item.getEndTime());
                            tvTime.setTextColor(androidx.core.content.ContextCompat.getColor(
                                    holder.itemView.getContext(), item.isWork()
                                            ? R.color.schedule_work_on : R.color.schedule_school_on));
                        }

                        int parsedColor = androidx.core.content.ContextCompat.getColor(
                                holder.itemView.getContext(),
                                item.isWork() ? R.color.schedule_work : R.color.schedule_school);

                        GradientDrawable bg = new GradientDrawable();
                        bg.setShape(GradientDrawable.RECTANGLE);
                        bg.setCornerRadius(16f);
                        bg.setColor(parsedColor);
                        pillView.setBackground(bg);

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

    /**
     * Validates if the string matches a scanned time format.
     * Returns true only if the text is a valid time (e.g. 12:00 PM).
     */
    private boolean isValidTime(String time) {
        if (time == null || time.isEmpty()) return false;
        Matcher matcher = timePattern.matcher(time.trim());
        return matcher.matches();
    }

    private String formatHourLabel(int hour) {
        if (hour == 0) return "12 AM";
        if (hour < 12) return hour + " AM";
        if (hour == 12) return "12 PM";
        return (hour - 12) + " PM";
    }

    private boolean doesScheduleMatchHour(ScheduleItem item, int hourIndex) {
        try {
            String startTime = item.getStartTime().trim().toUpperCase();
            String[] tokens = startTime.split(" ");
            String[] hm = tokens[0].split(":");
            int hour = Integer.parseInt(hm[0]);
            String period = tokens.length > 1 ? tokens[1] : "AM";

            if (period.equals("PM") && hour != 12) hour += 12;
            if (period.equals("AM") && hour == 12) hour = 0;

            return hour == hourIndex;
        } catch (Exception e) {
            return false;
        }
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
