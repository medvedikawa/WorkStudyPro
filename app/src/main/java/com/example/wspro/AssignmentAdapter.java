package com.example.wspro;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AssignmentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    public interface OnAssignmentActionListener {
        void onToggleComplete(AssignmentItem item, boolean isCompleted);
        void onEdit(AssignmentItem item);
        void onDelete(AssignmentItem item);
        void onSelectionChanged(AssignmentItem item, boolean isSelected);
        void onStartSelection(AssignmentItem item);
    }

    public static class DisplayRow {
        public final boolean isHeader;
        public final String headerTitle;
        public final boolean isOverdue;
        public final AssignmentItem item;

        public DisplayRow(String headerTitle, boolean isOverdue) {
            this.isHeader = true;
            this.headerTitle = headerTitle;
            this.isOverdue = isOverdue;
            this.item = null;
        }

        public DisplayRow(AssignmentItem item) {
            this.isHeader = false;
            this.headerTitle = null;
            this.isOverdue = false;
            this.item = item;
        }
    }

    private final Context context;
    private List<DisplayRow> displayRows = new ArrayList<>();
    private final OnAssignmentActionListener listener;
    private boolean isSelecting = false;

    public AssignmentAdapter(Context context, List<AssignmentItem> assignments, OnAssignmentActionListener listener) {
        this.context = context;
        this.listener = listener;
        updateData(assignments);
    }

    public void setSelecting(boolean selecting) {
        this.isSelecting = selecting;
        notifyDataSetChanged();
    }

    public boolean isSelecting() {
        return isSelecting;
    }

    public void updateData(List<AssignmentItem> newAssignments) {
        displayRows = buildDisplayRows(newAssignments);
        notifyDataSetChanged();
    }

    private List<DisplayRow> buildDisplayRows(List<AssignmentItem> items) {
        List<DisplayRow> rows = new ArrayList<>();
        if (items == null || items.isEmpty()) return rows;

        Calendar now = Calendar.getInstance();
        Calendar dayStart = (Calendar) now.clone();
        dayStart.set(Calendar.HOUR_OF_DAY, 0);
        dayStart.set(Calendar.MINUTE, 0);
        dayStart.set(Calendar.SECOND, 0);
        dayStart.set(Calendar.MILLISECOND, 0);

        String lastGroup = "";
        for (AssignmentItem item : items) {
            String group;
            boolean isOverdue = false;
            if (item.isCompleted()) {
                group = "COMPLETED";
            } else {
                long diff = item.getDueTimestamp() - dayStart.getTimeInMillis();
                long oneDay = 24L * 60 * 60 * 1000;
                if (diff < 0) {
                    group = "OVERDUE";
                    isOverdue = true;
                } else if (diff < oneDay) {
                    group = "TODAY";
                } else if (diff < 2 * oneDay) {
                    group = "TOMORROW";
                } else if (diff < 7 * oneDay) {
                    group = "THIS WEEK";
                } else {
                    group = "LATER";
                }
            }

            if (!group.equals(lastGroup)) {
                rows.add(new DisplayRow(group, isOverdue));
                lastGroup = group;
            }
            rows.add(new DisplayRow(item));
        }
        return rows;
    }

    @Override
    public int getItemViewType(int position) {
        return displayRows.get(position).isHeader ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_task_group_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_assignment, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder baseHolder, int position) {
        DisplayRow row = displayRows.get(position);
        if (row.isHeader) {
            HeaderViewHolder holder = (HeaderViewHolder) baseHolder;
            holder.tvGroupHeader.setText(row.headerTitle);
            holder.tvGroupHeader.setTextColor(ContextCompat.getColor(context,
                    row.isOverdue ? R.color.task_rasp : R.color.task_muted));
            return;
        }

        ItemViewHolder holder = (ItemViewHolder) baseHolder;
        AssignmentItem item = row.item;
        if (item == null) return;

        holder.tvTitle.setText(item.getTitle());

        // Meta string: e.g. "Database Systems · School"
        String subject = item.getSubject() != null ? item.getSubject().trim() : "";
        String cat = item.getCategory() != null ? item.getCategory().trim() : AssignmentItem.CATEGORY_SCHOOL;
        List<String> metaParts = new ArrayList<>();
        if (!subject.isEmpty()) metaParts.add(subject);
        if (!cat.isEmpty()) metaParts.add(cat);
        holder.tvMeta.setText(String.join(" · ", metaParts));
        holder.tvMeta.setVisibility(metaParts.isEmpty() ? View.GONE : View.VISIBLE);

        // Due time display: e.g. "Today, 11:59 PM" or formatted date
        String dueStr = formatDue(item);
        holder.tvDueDateTime.setText(dueStr);

        boolean urgent = !item.isCompleted() && isUrgent(item);
        holder.tvDueDateTime.setTextColor(ContextCompat.getColor(context,
                urgent ? R.color.task_rasp : R.color.task_muted));
        holder.ivClock.setColorFilter(ContextCompat.getColor(context,
                urgent ? R.color.task_rasp : R.color.task_muted));

        // Priority / Classification badge styling (Critical, Urgent, Not Urgent, Minor)
        String classification = PriorityEngine.classifyTask(item, System.currentTimeMillis());
        holder.tvPriorityBadge.setText(classification);
        applyPriorityTheme(holder.tvPriorityBadge, classification);

        // Importance Badge (High, Mid, Low)
        String importance = item.getImportance();
        if (importance == null || importance.isEmpty()) importance = PriorityEngine.IMPORTANCE_MID;
        holder.tvImportanceBadge.setText(importance);
        applyPriorityTheme(holder.tvImportanceBadge, importance);

        // Conflict tag
        if (item.isHasWorkCollision() && !item.isCompleted()) {
            holder.layoutConflictTag.setVisibility(View.VISIBLE);
        } else {
            holder.layoutConflictTag.setVisibility(View.GONE);
        }

        // Card styling: completed vs selected vs normal
        boolean isSelected = item.isSelected();
        boolean isCompleted = item.isCompleted();

        if (isSelected) {
            holder.cardRoot.setBackgroundResource(R.drawable.bg_task_card_mockup);
            holder.cardRoot.setSelected(true);
        } else {
            holder.cardRoot.setBackgroundResource(R.drawable.bg_task_card_mockup);
            holder.cardRoot.setSelected(false);
        }

        if (isCompleted) {
            holder.cardRoot.setAlpha(0.65f);
            holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.task_muted));
        } else {
            holder.cardRoot.setAlpha(1.0f);
            holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.task_ink));
        }

        // Circular check state
        boolean checked = isSelecting ? isSelected : isCompleted;
        holder.frameTaskCheck.setSelected(checked);
        holder.ivTaskCheck.setVisibility(checked ? View.VISIBLE : View.GONE);

        // Check button click
        holder.frameTaskCheck.setOnClickListener(v -> {
            if (isSelecting) {
                item.setSelected(!item.isSelected());
                notifyItemChanged(holder.getBindingAdapterPosition());
                if (listener != null) listener.onSelectionChanged(item, item.isSelected());
            } else {
                boolean target = !item.isCompleted();
                if (listener != null) listener.onToggleComplete(item, target);
            }
        });

        // Kebab / 3 dots options
        if (isSelecting) {
            holder.btnTaskMenu.setVisibility(View.GONE);
        } else {
            holder.btnTaskMenu.setVisibility(View.VISIBLE);
            holder.btnTaskMenu.setOnClickListener(v -> showTaskOptionsPopup(v, item));
        }

        // Card tap & long-press
        holder.cardRoot.setOnClickListener(v -> {
            if (isSelecting) {
                item.setSelected(!item.isSelected());
                notifyItemChanged(holder.getBindingAdapterPosition());
                if (listener != null) listener.onSelectionChanged(item, item.isSelected());
            } else {
                if (listener != null) listener.onEdit(item);
            }
        });

        holder.cardRoot.setOnLongClickListener(v -> {
            if (!isSelecting) {
                item.setSelected(true);
                if (listener != null) listener.onStartSelection(item);
                return true;
            }
            return false;
        });
    }

    private void showTaskOptionsPopup(View anchor, AssignmentItem item) {
        PopupMenu popup = new PopupMenu(context, anchor);
        popup.getMenu().add(0, 1, 0, "Edit");
        popup.getMenu().add(0, 2, 1, "Delete");
        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == 1) {
                if (listener != null) listener.onEdit(item);
                return true;
            } else if (menuItem.getItemId() == 2) {
                if (listener != null) listener.onDelete(item);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void applyPriorityTheme(TextView tag, String priority) {
        if ("Critical".equalsIgnoreCase(priority)) {
            tag.setBackgroundResource(R.drawable.bg_tag_critical);
            tag.setTextColor(ContextCompat.getColor(context, R.color.task_tag_crit_txt));
        } else if ("High".equalsIgnoreCase(priority) || "Urgent".equalsIgnoreCase(priority)) {
            tag.setBackgroundResource(R.drawable.bg_tag_high);
            tag.setTextColor(ContextCompat.getColor(context, R.color.task_tag_high_txt));
        } else if ("Mid".equalsIgnoreCase(priority) || "Not Urgent".equalsIgnoreCase(priority)) {
            tag.setBackgroundResource(R.drawable.bg_tag_mid);
            tag.setTextColor(ContextCompat.getColor(context, R.color.task_tag_mid_txt));
        } else {
            tag.setBackgroundResource(R.drawable.bg_tag_low);
            tag.setTextColor(ContextCompat.getColor(context, R.color.task_tag_low_txt));
        }
    }

    private boolean isUrgent(AssignmentItem item) {
        long now = System.currentTimeMillis();
        long diff = item.getDueTimestamp() - now;
        return diff <= 24L * 60 * 60 * 1000;
    }

    private String formatDue(AssignmentItem item) {
        Calendar now = Calendar.getInstance();
        Calendar due = Calendar.getInstance();
        due.setTimeInMillis(item.getDueTimestamp());

        Calendar dayStart = (Calendar) now.clone();
        dayStart.set(Calendar.HOUR_OF_DAY, 0);
        dayStart.set(Calendar.MINUTE, 0);
        dayStart.set(Calendar.SECOND, 0);
        dayStart.set(Calendar.MILLISECOND, 0);

        long diff = item.getDueTimestamp() - dayStart.getTimeInMillis();
        long oneDay = 24L * 60 * 60 * 1000;
        String dayName;
        if (diff < 0 && diff > -oneDay) {
            dayName = "Yesterday";
        } else if (diff >= 0 && diff < oneDay) {
            dayName = "Today";
        } else if (diff >= oneDay && diff < 2 * oneDay) {
            dayName = "Tomorrow";
        } else {
            SimpleDateFormat df = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
            dayName = df.format(due.getTime());
        }

        String formattedTime = TimeFormatPreference.formatTime(context, item.getDueTime());
        return dayName + ", " + formattedTime;
    }

    @Override
    public int getItemCount() {
        return displayRows != null ? displayRows.size() : 0;
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvGroupHeader;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvGroupHeader = itemView.findViewById(R.id.tvGroupHeader);
        }
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        LinearLayout cardRoot;
        FrameLayout frameTaskCheck;
        ImageView ivTaskCheck, ivClock;
        TextView tvTitle, tvMeta, tvDueDateTime, tvPriorityBadge, tvImportanceBadge, tvConflictLabel;
        LinearLayout layoutConflictTag;
        ImageButton btnTaskMenu;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardTaskRoot);
            frameTaskCheck = itemView.findViewById(R.id.frameTaskCheck);
            ivTaskCheck = itemView.findViewById(R.id.ivTaskCheck);
            ivClock = itemView.findViewById(R.id.ivTaskClock);
            tvTitle = itemView.findViewById(R.id.tvAssignmentTitle);
            tvMeta = itemView.findViewById(R.id.tvTaskMeta);
            tvDueDateTime = itemView.findViewById(R.id.tvDueDateTime);
            tvPriorityBadge = itemView.findViewById(R.id.tvPriorityBadge);
            tvImportanceBadge = itemView.findViewById(R.id.tvImportanceBadge);
            layoutConflictTag = itemView.findViewById(R.id.layoutConflictTag);
            tvConflictLabel = itemView.findViewById(R.id.tvConflictLabel);
            btnTaskMenu = itemView.findViewById(R.id.btnTaskMenu);
        }
    }
}
