package com.example.wspro;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AssignmentAdapter extends RecyclerView.Adapter<AssignmentAdapter.ViewHolder> {

    public interface OnAssignmentActionListener {
        void onToggleComplete(AssignmentItem item, boolean isCompleted);
        void onEdit(AssignmentItem item);
        void onDelete(AssignmentItem item);
        void onSelectionChanged(AssignmentItem item, boolean isSelected);
    }

    private final Context context;
    private List<AssignmentItem> assignments;
    private final OnAssignmentActionListener listener;

    public AssignmentAdapter(Context context, List<AssignmentItem> assignments, OnAssignmentActionListener listener) {
        this.context = context;
        this.assignments = assignments;
        this.listener = listener;
    }

    public void updateData(List<AssignmentItem> newAssignments) {
        this.assignments = newAssignments;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_assignment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AssignmentItem item = assignments.get(position);

        holder.tvTitle.setText(item.getTitle());
        holder.tvSubject.setText(item.getSubject());
        holder.tvDueDateTime.setText(item.getDueDate() + " • "
                + TimeFormatPreference.formatTime(context, item.getDueTime()));
        boolean workTask = item.isWorkTask();

        // Display category badge (School, Work, Other)
        String category = item.getCategory() != null ? item.getCategory() : AssignmentItem.CATEGORY_SCHOOL;
        holder.tvCategoryBadge.setText(category + " (" + item.getImportance() + ")");
        if (category.equalsIgnoreCase(AssignmentItem.CATEGORY_WORK)) {
            holder.tvCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.schedule_work));
        } else if (category.equalsIgnoreCase(AssignmentItem.CATEGORY_OTHER)) {
            holder.tvCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.ws_accent2));
        } else {
            holder.tvCategoryBadge.setTextColor(ContextCompat.getColor(context, R.color.schedule_school));
        }

        holder.tvSubject.setVisibility(item.getSubject() == null || item.getSubject().isEmpty() ? View.GONE : View.VISIBLE);
        holder.layoutReasonBox.setVisibility(View.GONE);

        // Classification Chip display (Critical, Urgent, Not Urgent, Minor)
        if (item.isCompleted()) {
            holder.tvPriorityBadge.setText("COMPLETED");
            holder.tvPriorityBadge.setTextColor(ContextCompat.getColor(context, R.color.ws_success));
            holder.layoutReasonBox.setVisibility(View.GONE);
            holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvTitle.setAlpha(0.5f);
        } else {
            holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.tvTitle.setAlpha(1.0f);

            if (workTask || category.equalsIgnoreCase(AssignmentItem.CATEGORY_OTHER)) {
                holder.layoutReasonBox.setVisibility(View.GONE);
                holder.tvPriorityBadge.setVisibility(View.GONE);
            } else {
                holder.tvPriorityBadge.setVisibility(View.VISIBLE);
                String classification = PriorityEngine.classifyTask(item, System.currentTimeMillis());
                holder.tvPriorityBadge.setText(classification);

                if (classification.equals(PriorityEngine.CLASS_CRITICAL)) {
                    holder.tvPriorityBadge.setTextColor(ContextCompat.getColor(context, R.color.ws_error));
                } else if (classification.equals(PriorityEngine.CLASS_URGENT)) {
                    holder.tvPriorityBadge.setTextColor(ContextCompat.getColor(context, R.color.ws_warning));
                } else {
                    holder.tvPriorityBadge.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
                }

                if (item.isHasWorkCollision()) {
                    holder.layoutReasonBox.setVisibility(View.VISIBLE);
                    holder.tvPriorityReason.setTextColor(ContextCompat.getColor(context, R.color.ws_error));
                } else {
                    holder.layoutReasonBox.setVisibility(View.GONE);
                }
                holder.tvPriorityReason.setText(item.getPriorityReason());
            }
        }

        // Checkbox complete
        holder.cbComplete.setOnCheckedChangeListener(null);
        holder.cbComplete.setChecked(item.isCompleted());
        holder.cbComplete.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onToggleComplete(item, isChecked);
            }
        });

        // Checkbox select for bulk delete
        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setChecked(item.isSelected());
        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setSelected(isChecked);
            if (listener != null) {
                listener.onSelectionChanged(item, isChecked);
            }
        });

        // Delete button
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(item);
            }
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(item);
        });
    }

    @Override
    public int getItemCount() {
        return assignments != null ? assignments.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategoryBadge, tvPriorityBadge, tvTitle, tvSubject, tvDueDateTime, tvPriorityReason;
        CheckBox cbComplete, cbSelect;
        ImageButton btnDelete;
        LinearLayout layoutReasonBox, layoutBadges;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryBadge = itemView.findViewById(R.id.tvCategoryBadge);
            tvPriorityBadge = itemView.findViewById(R.id.tvPriorityBadge);
            tvTitle = itemView.findViewById(R.id.tvAssignmentTitle);
            tvSubject = itemView.findViewById(R.id.tvAssignmentSubject);
            tvDueDateTime = itemView.findViewById(R.id.tvDueDateTime);
            tvPriorityReason = itemView.findViewById(R.id.tvPriorityReason);
            cbComplete = itemView.findViewById(R.id.cbComplete);
            cbSelect = itemView.findViewById(R.id.cbSelectTask);
            btnDelete = itemView.findViewById(R.id.btnDeleteAssignment);
            layoutReasonBox = itemView.findViewById(R.id.layoutReasonBox);
            layoutBadges = itemView.findViewById(R.id.layoutAssignmentBadges);
        }
    }
}
