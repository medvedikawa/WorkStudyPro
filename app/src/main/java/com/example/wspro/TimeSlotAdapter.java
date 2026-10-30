package com.example.wspro;

import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TimeSlotAdapter extends RecyclerView.Adapter<TimeSlotAdapter.ViewHolder> {

    public static final int[] PALETTE = {
            R.color.schedule_indigo,
            R.color.schedule_work,
            R.color.schedule_emerald,
            R.color.schedule_orange,
            R.color.schedule_rose,
            R.color.schedule_school
    };

    private final List<TimeSlotItem> timeSlots;
    private final Context context;
    private final boolean categoryLocked;
    private Runnable onItemsChangedListener;

    public TimeSlotAdapter(Context context, List<TimeSlotItem> timeSlots) {
        this(context, timeSlots, false);
    }

    public TimeSlotAdapter(Context context, List<TimeSlotItem> timeSlots, boolean categoryLocked) {
        this.context = context;
        this.timeSlots = timeSlots;
        this.categoryLocked = categoryLocked;
    }

    public void setOnItemsChangedListener(Runnable listener) {
        onItemsChangedListener = listener;
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
                item.setSubject(s.toString());
                item.setSubjectError(null);
                showInlineError(holder.tvSubjectError, null);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };
        holder.etSubject.addTextChangedListener(holder.subjectWatcher);

        // 2. Day Spinner Setup
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                context,
                R.array.days_array,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.spinnerDay.setAdapter(spinnerAdapter);

        String itemDay = item.getDay() != null ? item.getDay().toUpperCase().trim() : "";
        holder.spinnerDay.setSelection(0);
        if (!itemDay.isEmpty()) {
            for (int i = 1; i < spinnerAdapter.getCount(); i++) {
                CharSequence itemSeq = spinnerAdapter.getItem(i);
                if (itemSeq == null) continue;
                String opt = itemSeq.toString().toUpperCase();
                String normOpt = EditScheduleActivity.normalizeDay(opt);
                if (normOpt.equalsIgnoreCase(itemDay) || opt.startsWith(itemDay.substring(0, Math.min(2, itemDay.length())))) {
                    holder.spinnerDay.setSelection(i);
                    break;
                }
            }
        }

        holder.spinnerDay.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos == 0) {
                    item.setDay("");
                } else {
                    Object selectedObj = parent.getItemAtPosition(pos);
                    String fullDay = selectedObj != null ? selectedObj.toString() : "";
                    item.setDay(EditScheduleActivity.normalizeDay(fullDay));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

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
            item.setWork(false);
            if (isColor(item, R.color.schedule_orange)
                    || isColor(item, R.color.schedule_work)) {
                item.setColorHex(getColorHex(R.color.schedule_school));
            }
            updateTypeToggleUI(holder, false);
            updatePaletteSelection(holder, item.getColorHex());
        });

        holder.btnTypeWork.setOnClickListener(v -> {
            item.setWork(true);
            if (isColor(item, R.color.schedule_indigo)
                    || isColor(item, R.color.schedule_school)) {
                item.setColorHex(getColorHex(R.color.schedule_work));
            }
            updateTypeToggleUI(holder, true);
            updatePaletteSelection(holder, item.getColorHex());
        });

        // 5. Color Palette Setup
        setupPalette(holder, item);

        // 6. Delete Action
        if (holder.btnDelete != null) {
            holder.btnDelete.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    timeSlots.remove(pos);
                    notifyItemRemoved(pos);
                    if (onItemsChangedListener != null) onItemsChangedListener.run();
                }
            });
        }
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
                item.setColorHex(colorHex);
                updatePaletteSelection(holder, colorHex);
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

            if (isStart) {
                item.setStartTime(formattedTime);
                item.setStartTimeError(null);
            } else {
                item.setEndTime(formattedTime);
                item.setEndTimeError(null);
            }
        }, hour, minute, false);

        timePickerDialog.show();
    }

    @Override
    public int getItemCount() {
        return timeSlots.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        EditText etSubject, etStart, etEnd;
        Spinner spinnerDay;
        ImageView btnDelete;
        TextView btnTypeSchool, btnTypeWork;
        TextView tvSubjectError, tvStartError, tvEndError, tvSlotFlags;
        View layoutColorPalette;
        View[] colorDots = new View[6];
        FrameLayout[] colorWrappers = new FrameLayout[6];
        TextWatcher subjectWatcher;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSlotFlags = itemView.findViewById(R.id.tvSlotFlags);
            etSubject = itemView.findViewById(R.id.etRowSubject);
            etStart = itemView.findViewById(R.id.etRowStart);
            etEnd = itemView.findViewById(R.id.etRowEnd);
            tvSubjectError = itemView.findViewById(R.id.tvSubjectError);
            tvStartError = itemView.findViewById(R.id.tvStartError);
            tvEndError = itemView.findViewById(R.id.tvEndError);
            spinnerDay = itemView.findViewById(R.id.sDay);
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
