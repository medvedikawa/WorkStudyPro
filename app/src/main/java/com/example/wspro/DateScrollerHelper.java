package com.example.wspro;

import android.content.Context;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateScrollerHelper {

    public interface OnDaySelectedListener {
        void onDaySelected(String dayName);
    }

    public static void setup(Context context, LinearLayout daysContainer, OnDaySelectedListener listener) {
        daysContainer.removeAllViews();

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);

        SimpleDateFormat dayNumFormat = new SimpleDateFormat("dd", Locale.getDefault());
        SimpleDateFormat dayNameFormat = new SimpleDateFormat("E", Locale.getDefault());
        SimpleDateFormat fullDateFormat = new SimpleDateFormat("EEEE", Locale.getDefault());

        String todayFormatted = fullDateFormat.format(new Date());

        for (int i = 0; i < 7; i++) {
            Date currentDate = calendar.getTime();
            String dayNumStr = dayNumFormat.format(currentDate);
            String dayNameStr = dayNameFormat.format(currentDate);
            String fullDayName = fullDateFormat.format(currentDate);

            View chipView = LayoutInflater.from(context).inflate(R.layout.item_day_chip, daysContainer, false);

            TextView tvDayNumber = chipView.findViewById(R.id.tvDayNumber);
            TextView tvDayName = chipView.findViewById(R.id.tvDayName);
            View viewSelectionDot = chipView.findViewById(R.id.viewSelectionDot);

            tvDayNumber.setText(dayNumStr);
            tvDayName.setText(dayNameStr);

            boolean isToday = fullDayName.equals(todayFormatted);
            if (isToday) {
                chipView.setBackgroundResource(R.drawable.bg_day_chip_selected);
                viewSelectionDot.setVisibility(View.VISIBLE);
                tvDayNumber.setTextColor(Color.parseColor("#FFFFFF")); //this is white
                listener.onDaySelected(fullDayName);
            } else {
                chipView.setBackgroundResource(android.R.color.transparent);
                viewSelectionDot.setVisibility(View.GONE);
                tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.ws_muted));
            }

            chipView.setOnClickListener(v -> {
                for (int j = 0; j < daysContainer.getChildCount(); j++) {
                    View otherChip = daysContainer.getChildAt(j);
                    otherChip.setBackgroundResource(android.R.color.transparent);
                    otherChip.findViewById(R.id.viewSelectionDot).setVisibility(View.GONE);
                    ((TextView) otherChip.findViewById(R.id.tvDayNumber))
                            .setTextColor(ContextCompat.getColor(context, R.color.ws_muted));
                }
                chipView.setBackgroundResource(R.drawable.bg_day_chip_selected);
                viewSelectionDot.setVisibility(View.VISIBLE);
                tvDayNumber.setTextColor(Color.parseColor("#FFFFFF")); //this was the pink one

                listener.onDaySelected(fullDayName);
            });
            daysContainer.addView(chipView);
            calendar.add(Calendar.DATE, 1);
        }
    }
}