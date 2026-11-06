package com.example.wspro;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SkippedLinesAdapter
        extends ListAdapter<SkippedLinesAdapter.FooterState, SkippedLinesAdapter.ViewHolder> {

    public interface Listener {
        void onAddLine(String line);
        void onDismissLine(String line);
        void onDismiss();
        void onExpandedChanged(boolean expanded);
    }

    private static final DiffUtil.ItemCallback<FooterState> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull FooterState oldItem,
                                               @NonNull FooterState newItem) {
                    return true;
                }

                @Override
                public boolean areContentsTheSame(@NonNull FooterState oldItem,
                                                  @NonNull FooterState newItem) {
                    return oldItem.expanded == newItem.expanded
                            && Objects.equals(oldItem.lines, newItem.lines);
                }
            };

    private final LayoutInflater inflater;
    private Listener listener;

    public SkippedLinesAdapter(LayoutInflater inflater) {
        super(DIFF_CALLBACK);
        this.inflater = inflater;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void submitState(List<String> lines, boolean expanded) {
        if (lines == null || lines.isEmpty()) {
            submitList(Collections.emptyList());
        } else {
            submitList(Collections.singletonList(new FooterState(lines, expanded)));
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(inflater.inflate(R.layout.item_skipped_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FooterState state = getItem(position);
        holder.header.setText("Couldn't place (" + state.lines.size() + ")"
                + (state.expanded ? " ▾" : " ▸"));
        holder.container.setVisibility(state.expanded ? View.VISIBLE : View.GONE);
        holder.container.removeAllViews();
        for (String line : state.lines) {
            View row = inflater.inflate(R.layout.item_skipped_line, holder.container, false);
            TextView text = row.findViewById(R.id.tvSkippedText);
            Button add = row.findViewById(R.id.btnAddAsClass);
            TextView dismiss = row.findViewById(R.id.btnDismissSkippedLine);
            text.setText(line);
            add.setOnClickListener(v -> {
                if (listener != null) listener.onAddLine(line);
            });
            dismiss.setOnClickListener(v -> {
                if (listener != null) listener.onDismissLine(line);
            });
            holder.container.addView(row);
        }
        holder.header.setOnClickListener(v -> {
            if (listener != null) listener.onExpandedChanged(!state.expanded);
        });
        holder.dismiss.setOnClickListener(v -> {
            if (listener != null) listener.onDismiss();
        });
    }

    static class FooterState {
        final List<String> lines;
        final boolean expanded;

        FooterState(List<String> lines, boolean expanded) {
            this.lines = new ArrayList<>(lines);
            this.expanded = expanded;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView header;
        final TextView dismiss;
        final LinearLayout container;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            header = itemView.findViewById(R.id.tvSkippedHeader);
            dismiss = itemView.findViewById(R.id.btnDismissSkipped);
            container = itemView.findViewById(R.id.layoutSkippedContainer);
        }
    }
}
