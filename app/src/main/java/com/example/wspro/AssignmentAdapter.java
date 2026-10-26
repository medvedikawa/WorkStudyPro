package com.example.wspro;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AssignmentAdapter extends RecyclerView.Adapter<AssignmentAdapter.ViewHolder> {

    public interface OnAssignmentActionListener {
        void onToggleComplete(AssignmentItem item, boolean isCompleted);
        void onDelete(AssignmentItem item);
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
        holder.tvDueDateTime.setText(item.getDueDate() + " • " + item.getDueTime());

        // Category formatting
        String category = item.getCategory() != null ? item.getCategory() : "Assignment";
        if (category.equalsIgnoreCase("Task Performance")) {
            holder.tvCategoryBadge.setText("Task Performance");
            holder.tvCategoryBadge.setTextColor(androidx.core.content.ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.ws_category_task));
        } else if (category.equalsIgnoreCase("Group Work")) {
            holder.tvCategoryBadge.setText("Group Work");
            holder.tvCategoryBadge.setTextColor(androidx.core.content.ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.ws_category_group));
        } else if (category.equalsIgnoreCase("Laboratory")) {
            holder.tvCategoryBadge.setText("Laboratory");
            holder.tvCategoryBadge.setTextColor(androidx.core.content.ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.ws_category_lab));
        } else {
            holder.tvCategoryBadge.setText("Assignment");
            holder.tvCategoryBadge.setTextColor(androidx.core.content.ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.ws_category_assignment));
        }

        // Priority Rank Badge & Collision
        if (item.isCompleted()) {
            holder.tvPriorityBadge.setText("COMPLETED");
            holder.tvPriorityBadge.setTextColor(androidx.core.content.ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.ws_success));
            holder.layoutReasonBox.setVisibility(View.GONE);
            holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvTitle.setAlpha(0.5f);
        } else {
            holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.tvTitle.setAlpha(1.0f);

            int rank = position + 1;
            holder.tvPriorityBadge.setText("#" + rank + " PRIORITY (" + (int) item.getPriorityScore() + " pts)");

            if (item.isHasWorkCollision()) {
                holder.tvPriorityBadge.setTextColor(androidx.core.content.ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.ws_error));
                holder.layoutReasonBox.setVisibility(View.VISIBLE);
                holder.tvPriorityReason.setTextColor(androidx.core.content.ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.ws_error));
            } else {
                holder.tvPriorityBadge.setTextColor(androidx.core.content.ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.ws_warning));
                holder.layoutReasonBox.setVisibility(View.VISIBLE);
                holder.tvPriorityReason.setTextColor(androidx.core.content.ContextCompat.getColor(
                        holder.itemView.getContext(), R.color.ws_text2));
            }
            holder.tvPriorityReason.setText(item.getPriorityReason());
        }

        // Checkbox complete
        holder.cbComplete.setOnCheckedChangeListener(null);
        holder.cbComplete.setChecked(item.isCompleted());
        holder.cbComplete.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onToggleComplete(item, isChecked);
            }
        });

        // Delete button
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return assignments != null ? assignments.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategoryBadge, tvPriorityBadge, tvTitle, tvSubject, tvDueDateTime, tvPriorityReason;
        CheckBox cbComplete;
        ImageButton btnDelete;
        LinearLayout layoutReasonBox;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategoryBadge = itemView.findViewById(R.id.tvCategoryBadge);
            tvPriorityBadge = itemView.findViewById(R.id.tvPriorityBadge);
            tvTitle = itemView.findViewById(R.id.tvAssignmentTitle);
            tvSubject = itemView.findViewById(R.id.tvAssignmentSubject);
            tvDueDateTime = itemView.findViewById(R.id.tvDueDateTime);
            tvPriorityReason = itemView.findViewById(R.id.tvPriorityReason);
            cbComplete = itemView.findViewById(R.id.cbComplete);
            btnDelete = itemView.findViewById(R.id.btnDeleteAssignment);
            layoutReasonBox = itemView.findViewById(R.id.layoutReasonBox);
        }
    }
}
