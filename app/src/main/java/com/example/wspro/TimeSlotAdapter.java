package com.example.wspro;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TimeSlotAdapter extends RecyclerView.Adapter<TimeSlotAdapter.ViewHolder> {

    private List<TimeSlotItem> timeSlots;

    public TimeSlotAdapter(List<TimeSlotItem> timeSlots) {
        this.timeSlots = timeSlots;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_time_slot, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TimeSlotItem item = timeSlots.get(position);

        holder.etDay.setText(item.getDay());
        holder.etStart.setText(item.getStartTime());
        holder.etEnd.setText(item.getEndTime());

        // Update model as user types
        holder.etDay.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) item.setDay(holder.etDay.getText().toString());
        });
        holder.etStart.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) item.setStartTime(holder.etStart.getText().toString());
        });
        holder.etEnd.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) item.setEndTime(holder.etEnd.getText().toString());
        });

        // Delete button action
        holder.btnDelete.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            if (currentPosition != RecyclerView.NO_POSITION) {
                timeSlots.remove(currentPosition);
                notifyItemRemoved(currentPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return timeSlots.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        EditText etDay, etStart, etEnd;
        ImageView btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            etDay = itemView.findViewById(R.id.etRowDay);
            etStart = itemView.findViewById(R.id.etRowStart);
            etEnd = itemView.findViewById(R.id.etRowEnd);
            btnDelete = itemView.findViewById(R.id.btnDeleteSlot);
        }
    }
}