package com.example.wspro;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

public final class BottomNavHelper {

    public enum Tab {
        TODAY, WEEK, TASKS, YOU
    }

    private BottomNavHelper() { }

    public static void setup(Activity activity, Tab selectedTab, ScanButtonHelper scanHelper) {
        bind(activity, selectedTab, Tab.TODAY, R.id.btnToday);
        bind(activity, selectedTab, Tab.WEEK, R.id.btnWeek);
        bind(activity, selectedTab, Tab.TASKS, R.id.btnTasks);
        bind(activity, selectedTab, Tab.YOU, R.id.btnYou);

        ImageButton scanButton = activity.findViewById(R.id.fabScanSchedule);
        if (scanButton != null && scanHelper != null) scanHelper.attachToFab(scanButton);
    }

    public static void navigateTo(Activity activity, Tab currentTab, Tab destination) {
        if (currentTab == destination) return;
        Class<? extends Activity> target;
        switch (destination) {
            case TODAY:
                target = MainActivity.class;
                break;
            case WEEK:
                target = WeekActivity.class;
                break;
            case TASKS:
                target = TasksActivity.class;
                break;
            case YOU:
                target = ProfileActivity.class;
                break;
            default:
                throw new IllegalArgumentException("Unknown tab: " + destination);
        }
        Intent intent = new Intent(activity, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.startActivity(intent);
        applyNoTransition(activity);
        activity.finish();
    }

    private static void applyNoTransition(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
        }
    }

    private static void bind(Activity activity, Tab selectedTab, Tab tab, int itemId) {
        TextView item = activity.findViewById(itemId);
        if (item == null) return;

        ImageView icon = activity.findViewById(iconId(tab));
        if (icon != null) {
            item.setTextAppearance(tab == selectedTab ? R.style.WS_NavActive : R.style.WS_Micro);
        }
        int color = ContextCompat.getColor(activity, icon != null
                ? (tab == selectedTab ? R.color.brand_purple : R.color.text_muted)
                : (tab == selectedTab ? R.color.ws_accent : R.color.ws_text));
        item.setTextColor(color);

        if (icon != null) {
            ImageViewCompat.setImageTintList(icon, android.content.res.ColorStateList.valueOf(color));
        }

        View slotContainer = (icon != null && icon.getParent() instanceof View)
                ? (View) icon.getParent()
                : item;

        if (tab != selectedTab) {
            View.OnClickListener clickListener = v -> navigateTo(activity, selectedTab, tab);
            slotContainer.setOnClickListener(clickListener);
            if (icon != null) icon.setOnClickListener(clickListener);
            item.setOnClickListener(clickListener);
        } else {
            slotContainer.setOnClickListener(null);
            slotContainer.setClickable(false);
            if (icon != null) icon.setOnClickListener(null);
            item.setOnClickListener(null);
        }
    }

    private static int iconId(Tab tab) {
        switch (tab) {
            case TODAY:
                return R.id.navIconToday;
            case WEEK:
                return R.id.navIconWeek;
            case TASKS:
                return R.id.navIconTasks;
            case YOU:
                return R.id.navIconYou;
            default:
                return 0;
        }
    }
}
