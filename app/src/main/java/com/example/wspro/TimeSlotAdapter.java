package com.example.wspro;

import android.app.TimePickerDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

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

        // 1. Remove existing listener to prevent recycling issues
        if (holder.subjectWatcher != null) {
            holder.etSubject.removeTextChangedListener(holder.subjectWatcher);
        }

        // 2. Bind Subject
        holder.etSubject.setText(item.getSubject());

        // 3. Add new listener
        holder.subjectWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                item.setSubject(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
        holder.etSubject.addTextChangedListener(holder.subjectWatcher);

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

// Bind Category Spinner
        ArrayAdapter<CharSequence> categoryAdapter = ArrayAdapter.createFromResource(
                context,
                R.array.schedule_categories,
                android.R.layout.simple_spinner_item
        );
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.spinnerCategory.setAdapter(categoryAdapter);

        if (item.getCategory() != null && !item.getCategory().isEmpty()) {
            int catPosition = categoryAdapter.getPosition(item.getCategory());
            if (catPosition >= 0) {
                holder.spinnerCategory.setSelection(catPosition);
            }
        }

        holder.spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                item.setCategory(parent.getItemAtPosition(pos).toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
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

    public static int timeToMinutes(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 0;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());
            java.util.Date date = sdf.parse(timeStr.trim().toUpperCase());
            if (date != null) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(date);
                return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } return 0;
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

            if (!isStart){
                int startMinutes = timeToMinutes(item.getStartTime());
                int endMinutes = timeToMinutes(formattedTime);

                if (startMinutes >= endMinutes){
                    Toast.makeText(context, "End time cannot be earlier or equal to start time.", Toast.LENGTH_SHORT).show();
                    return; //Stop here, do not update the end time
                }
            }

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
        EditText etSubject;
        Spinner spinnerDay;
        EditText etStart;
        EditText etEnd;
        ImageView btnDelete;
        Spinner spinnerCategory;
        TextWatcher subjectWatcher;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            etSubject = itemView.findViewById(R.id.etRowSubject);
            spinnerCategory = itemView.findViewById(R.id.spinnerCategory);
            spinnerDay = itemView.findViewById(R.id.sDay);
            etStart = itemView.findViewById(R.id.etRowStart);
            etEnd = itemView.findViewById(R.id.etRowEnd);
            btnDelete = itemView.findViewById(R.id.btnDeleteSlot);
        }
    }
}