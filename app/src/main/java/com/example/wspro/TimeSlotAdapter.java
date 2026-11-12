package com.example.wspro;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class TimeSlotAdapter extends ListAdapter<TimeSlotItem, TimeSlotAdapter.ViewHolder> {
    private static final Object PAYLOAD_DAYS = new Object();

    private static final DiffUtil.ItemCallback<TimeSlotItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull TimeSlotItem oldItem,
                                               @NonNull TimeSlotItem newItem) {
                    return oldItem.getStableId() == newItem.getStableId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull TimeSlotItem oldItem,
                                                 @NonNull TimeSlotItem newItem) {
                    return oldItem.getDays().equals(newItem.getDays())
                            && areNonDayContentsTheSame(oldItem, newItem);
                }

                @Override
                public Object getChangePayload(@NonNull TimeSlotItem oldItem,
                                               @NonNull TimeSlotItem newItem) {
                    if (!oldItem.getDays().equals(newItem.getDays())
                            && areDaySelectionContentsTheSame(oldItem, newItem)) {
                        return PAYLOAD_DAYS;
                    }
                    return null;
                }

                private boolean areDaySelectionContentsTheSame(TimeSlotItem oldItem,
                                                                TimeSlotItem newItem) {
                    return Objects.equals(oldItem.getSubject(), newItem.getSubject())
                            && Objects.equals(oldItem.getStartTime(), newItem.getStartTime())
                            && Objects.equals(oldItem.getEndTime(), newItem.getEndTime())
                            && oldItem.isWork() == newItem.isWork()
                            && Objects.equals(oldItem.getColorHex(), newItem.getColorHex())
                            && Objects.equals(oldItem.getGroupId(), newItem.getGroupId())
                            && Objects.equals(oldItem.getSubjectError(), newItem.getSubjectError())
                            && Objects.equals(oldItem.getStartTimeError(), newItem.getStartTimeError())
                            && Objects.equals(oldItem.getEndTimeError(), newItem.getEndTimeError())
                            && Objects.equals(oldItem.getFlags(), newItem.getFlags());
                }

                private boolean areNonDayContentsTheSame(TimeSlotItem oldItem,
                                                         TimeSlotItem newItem) {
                    return Objects.equals(oldItem.getSubject(), newItem.getSubject())
                            && Objects.equals(oldItem.getStartTime(), newItem.getStartTime())
                            && Objects.equals(oldItem.getEndTime(), newItem.getEndTime())
                            && oldItem.isWork() == newItem.isWork()
                            && Objects.equals(oldItem.getColorHex(), newItem.getColorHex())
                            && Objects.equals(oldItem.getGroupId(), newItem.getGroupId())
                            && Objects.equals(oldItem.getSubjectError(), newItem.getSubjectError())
                            && Objects.equals(oldItem.getStartTimeError(), newItem.getStartTimeError())
                            && Objects.equals(oldItem.getEndTimeError(), newItem.getEndTimeError())
                            && Objects.equals(oldItem.getScheduleError(), newItem.getScheduleError())
                            && oldItem.isConflict() == newItem.isConflict()
                            && Objects.equals(oldItem.getConflictReason(), newItem.getConflictReason())
                            && Objects.equals(oldItem.getCrossTabWarning(), newItem.getCrossTabWarning())
                            && Objects.equals(oldItem.getFlags(), newItem.getFlags());
                }
            };

    private static final String[] DAY_CODES = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
    private static final String[] DAY_LABELS = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
    private static final String[] DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private static final String[] SUMMARY_DAY_CODES = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
    private static final String[] SUMMARY_DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    public interface OnDeleteSlotListener {
        void onDeleteRequested(TimeSlotItem item);
    }

    public interface OnItemChangedListener {
        void onItemChanged(TimeSlotItem originalItem, TimeSlotItem updatedItem);
    }

    public interface OnAddSlotListener {
        void onAddSlot(TimeSlotItem item);
    }

    private interface ItemMutation {
        void apply(TimeSlotItem item);
    }

    public static final int[] PALETTE = {
            R.color.schedule_indigo,
            R.color.schedule_work,
            R.color.schedule_emerald,
            R.color.schedule_orange,
            R.color.schedule_rose,
            R.color.schedule_school
    };

    private final Context context;
    private long expandedStableId = Long.MIN_VALUE;
    private OnItemChangedListener onItemChangedListener;
    private OnDeleteSlotListener onDeleteSlotListener;
    private OnAddSlotListener onAddSlotListener;

    public TimeSlotAdapter(Context context) {
        super(DIFF_CALLBACK);
        this.context = context;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getStableId();
    }

    public void setOnItemChangedListener(OnItemChangedListener listener) {
        onItemChangedListener = listener;
    }

    public void setOnDeleteSlotListener(OnDeleteSlotListener listener) {
        onDeleteSlotListener = listener;
    }

    public void setOnAddSlotListener(OnAddSlotListener listener) {
        onAddSlotListener = listener;
    }

    public void setExpandedStableId(long stableId) {
        expandedStableId = stableId;
    }

    private void onItemChanged(TimeSlotItem item, ItemMutation mutation) {
        if (onItemChangedListener == null) return;
        TimeSlotItem originalItem = item.copy();
        TimeSlotItem updatedItem = item.copy();
        mutation.apply(updatedItem);
        onItemChangedListener.onItemChanged(originalItem, updatedItem);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_time_slot, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TimeSlotItem item = getItem(position);

        holder.tvBlockTitle.setText(item.getSubject() == null || item.getSubject().trim().isEmpty()
                ? context.getString(R.string.schedule_block_title, position + 1)
                : item.getSubject().trim());
        boolean expanded = expandedStableId == item.getStableId();
        holder.expandedFields.setVisibility(expanded ? View.VISIBLE : View.GONE);
        holder.ivExpandIndicator.setRotation(expanded ? 180f : 0f);
        boolean conflict = item.isConflict();
        boolean needsCheck = item.getFlagHintText() != null
                || item.getScheduleError() != null
                || item.getSubjectError() != null || item.getStartTimeError() != null
                || item.getEndTimeError() != null;
        holder.itemView.setBackground(createCardBackground(conflict));
        holder.tvStatus.setText(conflict ? "Conflict" : needsCheck ? "Check" : "OK");
        holder.tvStatus.setBackground(createStatusBackground(conflict
                ? R.color.brand_raspberry : needsCheck ? R.color.ws_surface2 : R.color.schedule_emerald));
        holder.tvStatus.setTextColor(ContextCompat.getColor(context,
                conflict ? R.color.ws_on_brand : R.color.text_primary));
        String conflictReason = item.getConflictReason();
        holder.tvConflictReason.setText(conflictReason == null ? "" : conflictReason);
        holder.tvConflictReason.setVisibility(conflictReason == null ? View.GONE : View.VISIBLE);
        String crossTabWarning = item.getCrossTabWarning();
        holder.tvCrossTabWarning.setText(crossTabWarning == null ? "" : crossTabWarning);
        holder.tvCrossTabWarning.setVisibility(crossTabWarning == null ? View.GONE : View.VISIBLE);
        holder.cardHeader.setOnClickListener(v -> {
            long next = expanded ? Long.MIN_VALUE : item.getStableId();
            int previous = findStablePosition(expandedStableId);
            expandedStableId = next;
            if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous);
            int current = holder.getBindingAdapterPosition();
            if (current != RecyclerView.NO_POSITION) notifyItemChanged(current);
        });

        // 0. Flags Hint Display
        String flagHint = item.getFlagHintText();
        if (holder.tvSlotFlags != null) {
            if (flagHint != null && !flagHint.isEmpty()) {
                holder.tvSlotFlags.setText(flagHint);
                holder.tvSlotFlags.setVisibility(View.VISIBLE);
            } else {
                holder.tvSlotFlags.setVisibility(View.GONE);
            }
        }

        // 1. TextWatcher for Subject
        if (holder.subjectWatcher != null) {
            holder.etSubject.removeTextChangedListener(holder.subjectWatcher);
        }
        String currentSubject = holder.etSubject.getText() != null ? holder.etSubject.getText().toString() : "";
        String targetSubject = item.getSubject() != null ? item.getSubject() : "";
        if (!currentSubject.equals(targetSubject)) {
            holder.etSubject.setText(targetSubject);
        }
        showInlineError(holder.tvSubjectError, item.getSubjectError());
        holder.subjectWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String subject = s.toString();
                item.setSubject(subject);
                item.setSubjectError(null);
                showInlineError(holder.tvSubjectError, null);
                holder.tvBlockTitle.setText(subject.trim().isEmpty()
                        ? context.getString(R.string.schedule_block_title, Math.max(1, holder.getBindingAdapterPosition() + 1))
                        : subject.trim());
                onItemChanged(item, updated -> {
                    updated.setSubject(subject);
                    updated.setSubjectError(null);
                });
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
        holder.etSubject.addTextChangedListener(holder.subjectWatcher);

        // 2. Day selection
        bindDays(holder, item, new HashSet<>());
        bindDayShortcut(holder.btnWeekdays, item,
                new String[]{"MON", "TUE", "WED", "THU", "FRI"});
        bindDayShortcut(holder.btnEveryDay, item, DAY_CODES);
        holder.btnClearDays.setOnClickListener(v ->
                onItemChanged(item, updated -> updated.setDays(new HashSet<>())));
        holder.tvScheduleSummary.setText(createSummary(item));
        holder.tvScheduleError.setText(item.getScheduleError());
        holder.tvScheduleError.setVisibility(item.getScheduleError() == null
                ? View.GONE : View.VISIBLE);

        // 3. Time Pickers
        String formattedStart = TimeFormatPreference.formatTime(context, item.getStartTime());
        String currentStart = holder.etStart.getText() != null ? holder.etStart.getText().toString() : "";
        if (!currentStart.equals(formattedStart)) {
            holder.etStart.setText(formattedStart);
        }
        showInlineError(holder.tvStartError, item.getStartTimeError());
        holder.etStart.setOnClickListener(v ->
                showTimePicker(holder.etStart, holder.tvStartError, item, true));

        String formattedEnd = TimeFormatPreference.formatTime(context, item.getEndTime());
        String currentEnd = holder.etEnd.getText() != null ? holder.etEnd.getText().toString() : "";
        if (!currentEnd.equals(formattedEnd)) {
            holder.etEnd.setText(formattedEnd);
        }
        showInlineError(holder.tvEndError, item.getEndTimeError());
        holder.etEnd.setOnClickListener(v ->
                showTimePicker(holder.etEnd, holder.tvEndError, item, false));

        // 4. Color Palette Setup
        setupPalette(holder, item);

        holder.btnAddAnother.setOnClickListener(v -> {
            if (onAddSlotListener != null) onAddSlotListener.onAddSlot(item);
        });

        if (holder.btnDelete != null) {
            holder.btnDelete.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && onDeleteSlotListener != null) {
                    onDeleteSlotListener.onDeleteRequested(getItem(pos));
                }
            });
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_DAYS)) {
            TimeSlotItem item = getItem(position);
            bindDaySelection(holder, item.getDays());
            holder.tvScheduleSummary.setText(createSummary(item, holder.selectedDays));
            bindDayRelatedState(holder, item);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void bindDayRelatedState(ViewHolder holder, TimeSlotItem item) {
        boolean conflict = item.isConflict();
        boolean needsCheck = item.getFlagHintText() != null
                || item.getScheduleError() != null
                || item.getSubjectError() != null || item.getStartTimeError() != null
                || item.getEndTimeError() != null;
        holder.itemView.setBackground(createCardBackground(conflict));
        holder.tvStatus.setText(conflict ? "Conflict" : needsCheck ? "Check" : "OK");
        holder.tvStatus.setBackground(createStatusBackground(conflict
                ? R.color.brand_raspberry : needsCheck ? R.color.ws_surface2 : R.color.schedule_emerald));
        holder.tvStatus.setTextColor(ContextCompat.getColor(context,
                conflict ? R.color.ws_on_brand : R.color.text_primary));
        String conflictReason = item.getConflictReason();
        holder.tvConflictReason.setText(conflictReason == null ? "" : conflictReason);
        holder.tvConflictReason.setVisibility(conflictReason == null ? View.GONE : View.VISIBLE);
        String crossTabWarning = item.getCrossTabWarning();
        holder.tvCrossTabWarning.setText(crossTabWarning == null ? "" : crossTabWarning);
        holder.tvCrossTabWarning.setVisibility(crossTabWarning == null ? View.GONE : View.VISIBLE);
        holder.tvScheduleError.setText(item.getScheduleError());
        holder.tvScheduleError.setVisibility(item.getScheduleError() == null
                ? View.GONE : View.VISIBLE);
    }

    private int findStablePosition(long stableId) {
        for (int i = 0; i < getCurrentList().size(); i++) {
            if (getItem(i).getStableId() == stableId) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    private GradientDrawable createCardBackground(boolean invalid) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(ContextCompat.getColor(context, R.color.ws_surface));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(invalid ? 2 : 1), ContextCompat.getColor(context,
                invalid ? R.color.brand_raspberry : R.color.ws_border));
        return background;
    }

    private GradientDrawable createStatusBackground(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(ContextCompat.getColor(context, color));
        background.setCornerRadius(dp(16));
        return background;
    }

    private void bindDays(ViewHolder holder, TimeSlotItem item, Set<String> selectedElsewhere) {
        holder.daySelector.removeAllViews();
        holder.daySelector.setOrientation(LinearLayout.VERTICAL);
        holder.selectedDays.clear();
        holder.selectedDays.addAll(item.getDays());
        holder.assignedElsewhereDays.clear();
        holder.assignedElsewhereDays.addAll(selectedElsewhere);
        holder.dayToggles = new ToggleButton[DAY_CODES.length];
        LinearLayout row = null;
        for (int i = 0; i < DAY_CODES.length; i++) {
            if (i == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                holder.daySelector.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
            }
            String day = DAY_CODES[i];
            boolean assignedElsewhere = selectedElsewhere.contains(day);

            FrameLayout cell = new FrameLayout(context);
            LinearLayout.LayoutParams cellParams =
                    new LinearLayout.LayoutParams(0, dp(40), 1f);
            row.addView(cell, cellParams);

            ToggleButton toggle = new ToggleButton(context);
            holder.dayToggles[i] = toggle;
            toggle.setTextOn(DAY_LABELS[i]);
            toggle.setTextOff(DAY_LABELS[i]);
            toggle.setAllCaps(false);
            toggle.setTextSize(10);
            toggle.setMinWidth(0);
            toggle.setMinimumWidth(0);
            toggle.setMinHeight(dp(40));
            toggle.setPadding(0, 0, 0, 0);
            FrameLayout.LayoutParams toggleParams = new FrameLayout.LayoutParams(
                    dp(36), dp(36), android.view.Gravity.CENTER);
            cell.addView(toggle, toggleParams);
            toggle.setTag(day);
            updateDayToggle(holder, i);
            toggle.setOnClickListener(v -> {
                setDaySelected(holder, item, day, !holder.selectedDays.contains(day));
            });
            toggle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_UP && !holder.dragStarted) {
                    v.performClick();
                }
                return handleDayTouch(holder, item, day, event);
            });

            View dot = new View(context);
            dot.setBackground(createCircle(R.color.brand_raspberry));
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
                    dp(6), dp(6), android.view.Gravity.TOP | android.view.Gravity.END);
            dotParams.setMargins(0, dp(3), dp(3), 0);
            cell.addView(dot, dotParams);
            dot.setVisibility(assignedElsewhere ? View.VISIBLE : View.GONE);
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    private boolean handleDayTouch(ViewHolder holder, TimeSlotItem item, String day,
                                   MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                holder.initialTouchDay = day;
                holder.dragSelectState = !holder.selectedDays.contains(day);
                holder.dragStarted = false;
                holder.lastTouchX = event.getRawX();
                holder.lastTouchY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                float rawX = event.getRawX();
                float rawY = event.getRawY();
                float deltaX = rawX - holder.lastTouchX;
                float deltaY = rawY - holder.lastTouchY;
                float distance = (float) Math.hypot(deltaX, deltaY);
                int steps = Math.max(1, (int) (distance / Math.max(1, dp(8))));
                for (int step = 1; step <= steps; step++) {
                    float fraction = (float) step / steps;
                    String crossedDay = findDayAt(holder,
                            holder.lastTouchX + deltaX * fraction,
                            holder.lastTouchY + deltaY * fraction);
                    if (crossedDay == null) continue;
                    if (!holder.dragStarted && !crossedDay.equals(holder.initialTouchDay)) {
                        holder.dragStarted = true;
                        setDaySelected(holder, item, holder.initialTouchDay, holder.dragSelectState);
                    }
                    if (holder.dragStarted) {
                        setDaySelected(holder, item, crossedDay, holder.dragSelectState);
                    }
                }
                holder.lastTouchX = rawX;
                holder.lastTouchY = rawY;
                return true;
            case MotionEvent.ACTION_UP:
                holder.initialTouchDay = null;
                holder.dragStarted = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                holder.initialTouchDay = null;
                holder.dragStarted = false;
                return true;
            default:
                return false;
            }
    }

    private String findDayAt(ViewHolder holder, float rawX, float rawY) {
        for (int i = 0; i < holder.dayToggles.length; i++) {
            ToggleButton toggle = holder.dayToggles[i];
            if (toggle == null) continue;
            int[] location = new int[2];
            toggle.getLocationOnScreen(location);
            if (rawX >= location[0] && rawX < location[0] + toggle.getWidth()
                    && rawY >= location[1] && rawY < location[1] + toggle.getHeight()) {
                return DAY_CODES[i];
            }
        }
        return null;
    }

    private void setDaySelected(ViewHolder holder, TimeSlotItem item, String day,
                                boolean selected) {
        boolean changed = selected ? holder.selectedDays.add(day) : holder.selectedDays.remove(day);
        if (!changed) return;
        updateDayToggle(holder, indexOfDay(day));
        holder.tvScheduleSummary.setText(createSummary(item, holder.selectedDays));
        Set<String> selectedDays = new HashSet<>(holder.selectedDays);
        onItemChanged(item, updated -> updated.setDays(selectedDays));
    }

    private void bindDaySelection(ViewHolder holder, Set<String> selectedDays) {
        holder.selectedDays.clear();
        holder.selectedDays.addAll(selectedDays);
        for (int i = 0; i < holder.dayToggles.length; i++) {
            updateDayToggle(holder, i);
        }
    }

    private void updateDayToggle(ViewHolder holder, int index) {
        if (index < 0 || index >= holder.dayToggles.length) return;
        ToggleButton toggle = holder.dayToggles[index];
        if (toggle == null) return;
        String day = DAY_CODES[index];
        boolean selected = holder.selectedDays.contains(day);
        toggle.setChecked(selected);
        toggle.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        toggle.setTextColor(ContextCompat.getColor(context,
                selected ? R.color.schedule_day_on : R.color.ws_text));
        toggle.setBackground(dayBackground(selected));
        boolean assignedElsewhere = holder.assignedElsewhereDays.contains(day);
        toggle.setContentDescription(DAY_NAMES[index] + (selected
                ? ", selected" : assignedElsewhere ? ", assigned to another block" : ""));
    }

    private int indexOfDay(String day) {
        for (int i = 0; i < DAY_CODES.length; i++) {
            if (DAY_CODES[i].equals(day)) return i;
        }
        return -1;
    }

    private void bindDayShortcut(TextView button, TimeSlotItem item, String[] days) {
        button.setOnClickListener(v -> selectDays(item, days));
    }

    private void selectDays(TimeSlotItem item, String[] days) {
        Set<String> selected = new HashSet<>();
        for (String day : days) {
            selected.add(day);
        }
        onItemChanged(item, updated -> updated.setDays(selected));
    }

    private String createSummary(TimeSlotItem item) {
        return createSummary(item, item.getDays());
    }

    private String createSummary(TimeSlotItem item, Set<String> days) {
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < SUMMARY_DAY_CODES.length; i++) {
            String day = SUMMARY_DAY_CODES[i];
            if (!days.contains(day)) continue;
            if (summary.length() > 0) summary.append(", ");
            summary.append(SUMMARY_DAY_NAMES[i]);
        }
        if (summary.length() == 0) summary.append("No days selected");
        return summary + " · " + TimeFormatPreference.formatTime(context, item.getStartTime())
                + " to " + TimeFormatPreference.formatTime(context, item.getEndTime());
    }

    private GradientDrawable dayBackground(boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(ContextCompat.getColor(context,
                selected ? R.color.ws_accent : R.color.ws_surface));
        if (!selected) {
            background.setStroke(dp(1), ContextCompat.getColor(context, R.color.ws_border));
        }
        return background;
    }

    private GradientDrawable createCircle(int color) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(ContextCompat.getColor(context, color));
        return circle;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void setupPalette(ViewHolder holder, TimeSlotItem item) {
        for (int i = 0; i < PALETTE.length; i++) {
            final String colorHex = getColorHex(PALETTE[i]);
            View dot = holder.colorDots[i];
            FrameLayout wrapper = holder.colorWrappers[i];

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(ContextCompat.getColor(context, PALETTE[i]));
            dot.setBackground(circle);

            wrapper.setOnClickListener(v -> {
                updatePaletteSelection(holder, colorHex);
                onItemChanged(item, updated -> updated.setColorHex(colorHex));
            });
        }
        updatePaletteSelection(holder, item.getColorHex());
    }

    private void updatePaletteSelection(ViewHolder holder, String activeColorHex) {
        for (int i = 0; i < PALETTE.length; i++) {
            String colorHex = getColorHex(PALETTE[i]);
            FrameLayout wrapper = holder.colorWrappers[i];
            boolean isSelected = colorHex.equalsIgnoreCase(activeColorHex);

            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(ContextCompat.getColor(context, android.R.color.transparent));
            if (isSelected) {
                ring.setStroke(4, ContextCompat.getColor(context, R.color.ws_text));
            }
            wrapper.setBackground(ring);
        }
    }

    private String getColorHex(int colorResource) {
        return String.format(Locale.ROOT, "#%06X",
                ContextCompat.getColor(context, colorResource) & 0xFFFFFF);
    }

    private void showInlineError(TextView errorView, String message) {
        errorView.setText(message);
        errorView.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showTimePicker(EditText targetView, TextView errorView,
                                TimeSlotItem item, boolean isStart) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormatPreference.is24Hour(context)
                        ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
                .setHour(hour)
                .setMinute(minute)
                .build();
        picker.addOnPositiveButtonClickListener(view -> {
            int hourOfDay = picker.getHour();
            int minuteOfHour = picker.getMinute();
            String formattedTime = TimeFormatPreference.formatTime(context, hourOfDay, minuteOfHour);
            targetView.setText(formattedTime);
            showInlineError(errorView, null);
            onItemChanged(item, updated -> {
                if (isStart) {
                    updated.setStartTime(formattedTime);
                    updated.setStartTimeError(null);
                } else {
                    updated.setEndTime(formattedTime);
                    updated.setEndTimeError(null);
                }
            });
        });
        picker.show(((androidx.fragment.app.FragmentActivity) context).getSupportFragmentManager(),
                "schedule_time_picker");
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        EditText etSubject, etStart, etEnd;
        LinearLayout daySelector;
        ToggleButton[] dayToggles = new ToggleButton[DAY_CODES.length];
        final Set<String> selectedDays = new HashSet<>();
        final Set<String> assignedElsewhereDays = new HashSet<>();
        String initialTouchDay;
        boolean dragSelectState;
        boolean dragStarted;
        float lastTouchX;
        float lastTouchY;
        View expandedFields;
        View cardHeader;
        View btnDelete;
        TextView tvBlockTitle, tvScheduleSummary, tvScheduleError;
        TextView tvStatus, tvConflictReason, tvCrossTabWarning;
        android.widget.ImageView ivExpandIndicator;
        TextView btnWeekdays, btnEveryDay, btnClearDays;
        TextView tvSubjectError, tvStartError, tvEndError, tvSlotFlags;
        View btnAddAnother;
        View[] colorDots = new View[6];
        FrameLayout[] colorWrappers = new FrameLayout[6];
        TextWatcher subjectWatcher;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBlockTitle = itemView.findViewById(R.id.tvBlockTitle);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            ivExpandIndicator = itemView.findViewById(R.id.ivExpandIndicator);
            tvConflictReason = itemView.findViewById(R.id.tvConflictReason);
            tvCrossTabWarning = itemView.findViewById(R.id.tvCrossTabWarning);
            expandedFields = itemView.findViewById(R.id.layoutExpandedFields);
            cardHeader = itemView.findViewById(R.id.layoutCardHeader);
            tvSlotFlags = itemView.findViewById(R.id.tvSlotFlags);
            etSubject = itemView.findViewById(R.id.etRowSubject);
            etStart = itemView.findViewById(R.id.etRowStart);
            etEnd = itemView.findViewById(R.id.etRowEnd);
            tvSubjectError = itemView.findViewById(R.id.tvSubjectError);
            tvStartError = itemView.findViewById(R.id.tvStartError);
            tvEndError = itemView.findViewById(R.id.tvEndError);
            tvScheduleSummary = itemView.findViewById(R.id.tvScheduleSummary);
            tvScheduleError = itemView.findViewById(R.id.tvScheduleError);
            daySelector = itemView.findViewById(R.id.daySelector);
            btnWeekdays = itemView.findViewById(R.id.btnWeekdays);
            btnEveryDay = itemView.findViewById(R.id.btnEveryDay);
            btnClearDays = itemView.findViewById(R.id.btnClearDays);
            btnDelete = itemView.findViewById(R.id.btnDeleteSlot);
            btnAddAnother = itemView.findViewById(R.id.btnAddAnotherTime);

            colorWrappers[0] = itemView.findViewById(R.id.wrapperColor1);
            colorWrappers[1] = itemView.findViewById(R.id.wrapperColor2);
            colorWrappers[2] = itemView.findViewById(R.id.wrapperColor3);
            colorWrappers[3] = itemView.findViewById(R.id.wrapperColor4);
            colorWrappers[4] = itemView.findViewById(R.id.wrapperColor5);
            colorWrappers[5] = itemView.findViewById(R.id.wrapperColor6);

            colorDots[0] = itemView.findViewById(R.id.viewColor1);
            colorDots[1] = itemView.findViewById(R.id.viewColor2);
            colorDots[2] = itemView.findViewById(R.id.viewColor3);
            colorDots[3] = itemView.findViewById(R.id.viewColor4);
            colorDots[4] = itemView.findViewById(R.id.viewColor5);
            colorDots[5] = itemView.findViewById(R.id.viewColor6);
        }
    }
}
