package com.example.wspro;

import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class TimeSlotAdapter extends ListAdapter<TimeSlotItem, TimeSlotAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<TimeSlotItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<TimeSlotItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull TimeSlotItem oldItem,
                                               @NonNull TimeSlotItem newItem) {
                    return oldItem.getStableId() == newItem.getStableId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull TimeSlotItem oldItem,
                                                 @NonNull TimeSlotItem newItem) {
                    return Objects.equals(oldItem.getSubject(), newItem.getSubject())
                            && oldItem.getDays().equals(newItem.getDays())
                            && Objects.equals(oldItem.getStartTime(), newItem.getStartTime())
                            && Objects.equals(oldItem.getEndTime(), newItem.getEndTime())
                            && oldItem.isWork() == newItem.isWork()
                            && Objects.equals(oldItem.getColorHex(), newItem.getColorHex())
                            && Objects.equals(oldItem.getGroupId(), newItem.getGroupId())
                            && Objects.equals(oldItem.getSubjectError(), newItem.getSubjectError())
                            && Objects.equals(oldItem.getStartTimeError(), newItem.getStartTimeError())
                            && Objects.equals(oldItem.getEndTimeError(), newItem.getEndTimeError())
                            && Objects.equals(oldItem.getScheduleError(), newItem.getScheduleError())
                            && Objects.equals(oldItem.getFlags(), newItem.getFlags());
                }
            };

    private static final String[] DAY_CODES = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    private static final String[] DAY_LABELS = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
    private static final String[] DAY_NAMES = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[] SUMMARY_DAY_CODES = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
    private static final String[] SUMMARY_DAY_NAMES = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    public interface OnDeleteSlotListener {
        void onDeleteRequested(TimeSlotItem item);
    }

    public interface OnItemChangedListener {
        void onItemChanged(TimeSlotItem originalItem, TimeSlotItem updatedItem);
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
    private final boolean categoryLocked;
    private OnItemChangedListener onItemChangedListener;
    private OnDeleteSlotListener onDeleteSlotListener;

    public TimeSlotAdapter(Context context, boolean categoryLocked) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.categoryLocked = categoryLocked;
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

        holder.tvBlockTitle.setText(context.getString(R.string.schedule_block_title, position + 1));

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
        holder.etSubject.setText(item.getSubject());
        showInlineError(holder.tvSubjectError, item.getSubjectError());
        holder.subjectWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String subject = s.toString();
                showInlineError(holder.tvSubjectError, null);
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
        Set<String> selectedElsewhere = new HashSet<>();
        for (TimeSlotItem other : getCurrentList()) {
            if (other != item) selectedElsewhere.addAll(other.getDays());
        }
        bindDays(holder, item, selectedElsewhere);
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
        holder.etStart.setText(item.getStartTime());
        showInlineError(holder.tvStartError, item.getStartTimeError());
        holder.etStart.setOnClickListener(v ->
                showTimePicker(holder.etStart, holder.tvStartError, item, true));

        holder.etEnd.setText(item.getEndTime());
        showInlineError(holder.tvEndError, item.getEndTimeError());
        holder.etEnd.setOnClickListener(v ->
                showTimePicker(holder.etEnd, holder.tvEndError, item, false));

        // 4. Type Toggle (School vs Work)
        updateTypeToggleUI(holder, item.isWork());
        holder.btnTypeSchool.setVisibility(categoryLocked ? View.GONE : View.VISIBLE);
        holder.btnTypeWork.setVisibility(categoryLocked ? View.GONE : View.VISIBLE);
        holder.layoutColorPalette.setVisibility(categoryLocked ? View.GONE : View.VISIBLE);
        holder.btnTypeSchool.setOnClickListener(v -> {
            String colorHex = isColor(item, R.color.schedule_orange)
                    || isColor(item, R.color.schedule_work)
                    ? getColorHex(R.color.schedule_school) : item.getColorHex();
            updateTypeToggleUI(holder, false);
            updatePaletteSelection(holder, colorHex);
            onItemChanged(item, updated -> {
                updated.setWork(false);
                updated.setColorHex(colorHex);
            });
        });

        holder.btnTypeWork.setOnClickListener(v -> {
            String colorHex = isColor(item, R.color.schedule_indigo)
                    || isColor(item, R.color.schedule_school)
                    ? getColorHex(R.color.schedule_work) : item.getColorHex();
            updateTypeToggleUI(holder, true);
            updatePaletteSelection(holder, colorHex);
            onItemChanged(item, updated -> {
                updated.setWork(true);
                updated.setColorHex(colorHex);
            });
        });

        // 5. Color Palette Setup
        setupPalette(holder, item);

        // 6. Delete Action
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
    public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            holder.tvBlockTitle.setText(context.getString(R.string.schedule_block_title, position + 1));
        }
    }

    private void bindDays(ViewHolder holder, TimeSlotItem item, Set<String> selectedElsewhere) {
        holder.daySelector.removeAllViews();
        for (int i = 0; i < DAY_CODES.length; i++) {
            String day = DAY_CODES[i];
            boolean selected = item.getDays().contains(day);
            boolean assignedElsewhere = selectedElsewhere.contains(day);

            FrameLayout cell = new FrameLayout(context);
            LinearLayout.LayoutParams cellParams =
                    new LinearLayout.LayoutParams(0, dp(48), 1f);
            holder.daySelector.addView(cell, cellParams);

            ToggleButton toggle = new ToggleButton(context);
            toggle.setTextOn(DAY_LABELS[i]);
            toggle.setTextOff(DAY_LABELS[i]);
            toggle.setChecked(selected);
            toggle.setAllCaps(false);
            toggle.setTextSize(12);
            toggle.setMinWidth(0);
            toggle.setMinimumWidth(dp(44));
            toggle.setMinHeight(dp(48));
            toggle.setPadding(0, 0, 0, 0);
            toggle.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            toggle.setTextColor(ContextCompat.getColor(context,
                    selected ? R.color.schedule_day_on : R.color.ws_text));
            toggle.setBackground(dayBackground(selected));
            FrameLayout.LayoutParams toggleParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
            cell.addView(toggle, toggleParams);
            toggle.setContentDescription(DAY_NAMES[i] + (selected
                    ? ", selected" : assignedElsewhere ? ", assigned to another block" : ""));
            toggle.setOnCheckedChangeListener((button, checked) ->
                    onItemChanged(item, updated -> {
                        if (checked) updated.getDays().add(day);
                        else updated.getDays().remove(day);
                    }));

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
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < SUMMARY_DAY_CODES.length; i++) {
            String day = SUMMARY_DAY_CODES[i];
            if (!item.getDays().contains(day)) continue;
            if (summary.length() > 0) summary.append(", ");
            summary.append(SUMMARY_DAY_NAMES[i]);
        }
        if (summary.length() == 0) summary.append("No days selected");
        return summary + " · " + item.getStartTime() + " to " + item.getEndTime();
    }

    private GradientDrawable dayBackground(boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(8));
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

    private void updateTypeToggleUI(ViewHolder holder, boolean isWork) {
        if (isWork) {
            holder.btnTypeWork.setBackgroundResource(R.drawable.bg_toggle_selected);
            holder.btnTypeWork.setTextColor(ContextCompat.getColor(context, R.color.ws_on_brand));
            holder.btnTypeSchool.setBackgroundResource(R.drawable.bg_toggle_unselected);
            holder.btnTypeSchool.setTextColor(ContextCompat.getColor(context, R.color.ws_text2));
        } else {
            holder.btnTypeSchool.setBackgroundResource(R.drawable.bg_toggle_selected);
            holder.btnTypeSchool.setTextColor(ContextCompat.getColor(context, R.color.ws_on_brand));
            holder.btnTypeWork.setBackgroundResource(R.drawable.bg_toggle_unselected);
            holder.btnTypeWork.setTextColor(ContextCompat.getColor(context, R.color.ws_text2));
        }
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

    private boolean isColor(TimeSlotItem item, int colorResource) {
        return getColorHex(colorResource).equalsIgnoreCase(item.getColorHex());
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

        TimePickerDialog timePickerDialog = new TimePickerDialog(context, (view, hourOfDay, minuteOfHour) -> {
            String amPm = hourOfDay >= 12 ? "PM" : "AM";
            int hour12 = hourOfDay % 12;
            if (hour12 == 0) hour12 = 12;

            String formattedTime = String.format(Locale.getDefault(), "%d:%02d %s", hour12, minuteOfHour, amPm);
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
        }, hour, minute, false);

        timePickerDialog.show();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        EditText etSubject, etStart, etEnd;
        LinearLayout daySelector;
        ImageView btnDelete;
        TextView btnTypeSchool, btnTypeWork;
        TextView tvBlockTitle, tvScheduleSummary, tvScheduleError;
        TextView btnWeekdays, btnEveryDay, btnClearDays;
        TextView tvSubjectError, tvStartError, tvEndError, tvSlotFlags;
        View layoutColorPalette;
        View[] colorDots = new View[6];
        FrameLayout[] colorWrappers = new FrameLayout[6];
        TextWatcher subjectWatcher;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBlockTitle = itemView.findViewById(R.id.tvBlockTitle);
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
            btnTypeSchool = itemView.findViewById(R.id.btnTypeSchool);
            btnTypeWork = itemView.findViewById(R.id.btnTypeWork);
            layoutColorPalette = itemView.findViewById(R.id.layoutColorPalette);

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
