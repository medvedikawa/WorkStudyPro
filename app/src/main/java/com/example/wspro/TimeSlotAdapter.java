package com.example.wspro;

import android.app.TimePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TimeSlotAdapter extends RecyclerView.Adapter<TimeSlotAdapter.ViewHolder> {

    private List<TimeSlotItem> timeSlots;
    private Context context;

    public TimeSlotAdapter(Context context, List<TimeSlotItem> timeSlots) {
        this.context = context;
        this.timeSlots = timeSlots;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_time_slot, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TimeSlotItem item = timeSlots.get(position);

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                context,
                R.array.days_array,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.spinnerDay.setAdapter(spinnerAdapter);

        if (item.getDay() != null && !item.getDay().isEmpty()) {
            int spinnerPosition = spinnerAdapter.getPosition(item.getDay());
            if (spinnerPosition >= 0) {
                holder.spinnerDay.setSelection(spinnerPosition);
            }
        }

        holder.spinnerDay.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                item.setDay(parent.getItemAtPosition(pos).toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Set up Start time picker
        holder.etStart.setText(item.getStartTime());
        holder.etStart.setOnClickListener(v -> showTimePicker(holder.etStart, item, true));

        // Set up End time picker
        holder.etEnd.setText(item.getEndTime());
        holder.etEnd.setOnClickListener(v -> showTimePicker(holder.etEnd, item, false));

        // Delete button action (uncommented and fixed holder reference if needed)
        if (holder.btnDelete != null) {
            holder.btnDelete.setOnClickListener(v -> {
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION) {
                    timeSlots.remove(currentPosition);
                    notifyItemRemoved(currentPosition);
                }
            });
        }
    }

    private void showTimePicker(EditText targetView, TimeSlotItem item, boolean isStart) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(context, (view, hourOfDay, minuteOfHour) -> {
            String amPm = hourOfDay >= 12 ? "PM" : "AM";
            int hour12 = hourOfDay % 12;
            if (hour12 == 0) hour12 = 12;
            String formattedTime = String.format(Locale.getDefault(), "%d:%02d %s", hour12, minuteOfHour, amPm);

            targetView.setText(formattedTime);
            if (isStart) {
                item.setStartTime(formattedTime);
            } else {
                item.setEndTime(formattedTime);
            }
        }, hour, minute, false);

        timePickerDialog.show();
    }

    @Override
    public int getItemCount() {
        return timeSlots.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        Spinner spinnerDay;
        EditText etStart;
        EditText etEnd;
        ImageView btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            spinnerDay = itemView.findViewById(R.id.sDay);
            etStart = itemView.findViewById(R.id.etRowStart);
            etEnd = itemView.findViewById(R.id.etRowEnd);
            btnDelete = itemView.findViewById(R.id.btnDeleteSlot);
        }
    }
}