package com.example.wspro;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScheduleEditorRepository {
    private final Context appContext;

    public ScheduleEditorRepository(Context context) {
        appContext = context.getApplicationContext();
    }

    public List<TimeSlotItem> loadCategory(boolean work) {
        List<ScheduleItem> schedules = AppDatabase.getInstance(appContext)
                .scheduleDao().getSchedulesByWork(work);
        List<TimeSlotItem> items = new ArrayList<>(schedules.size());
        for (ScheduleItem schedule : schedules) {
            TimeSlotItem item = new TimeSlotItem(schedule.getSubject(), schedule.getDay(),
                    schedule.getStartTime(), schedule.getEndTime(), work, schedule.getColorHex());
            item.setId(schedule.getId());
            item.setGroupId(schedule.getGroupId());
            items.add(item);
        }
        return items;
    }

    public List<ScheduleItem> loadCategorySchedules(boolean work) {
        return AppDatabase.getInstance(appContext).scheduleDao().getSchedulesByWork(work);
    }

    public List<String> findConflicts(List<ScheduleItem> proposed, List<Integer> deletedIds) {
        return ScheduleConflictFinder.findConflicts(
                AppDatabase.getInstance(appContext), proposed, deletedIds);
    }

    public SaveCheck checkBeforeSave(List<ScheduleItem> proposed, List<Integer> deletedIds) {
        AppDatabase db = AppDatabase.getInstance(appContext);
        List<String> conflicts = findConflicts(proposed, deletedIds);
        List<ScheduleItem> loadSchedules = new ArrayList<>();
        Set<Integer> replaced = new HashSet<>(deletedIds);
        for (ScheduleItem item : proposed) {
            if (item.getId() > 0) replaced.add(item.getId());
        }
        for (ScheduleItem existing : db.scheduleDao().getAll()) {
            if (!replaced.contains(existing.getId())) loadSchedules.add(existing);
        }
        loadSchedules.addAll(proposed);
        int weeklyLimit = SupabaseClient.getInstance(appContext).getWeeklyHoursLimit();
        return new SaveCheck(conflicts, LoadCalculator.calculate(loadSchedules, weeklyLimit));
    }

    public void save(boolean editMode, List<ScheduleItem> schedules, List<Integer> deletedIds) {
        AppDatabase db = AppDatabase.getInstance(appContext);
        if (editMode) LocalDataRepository.saveEditedSchedules(db, schedules, deletedIds);
        else LocalDataRepository.saveSchedules(db, schedules);
        SupabaseClient.getInstance(appContext).setHasUnsyncedChanges(true);
        ((WorkStudyProApplication) appContext).onLocalChangesQueued();
    }

    public static final class SaveCheck {
        public final List<String> conflicts;
        public final LoadCalculator.LoadResult load;

        private SaveCheck(List<String> conflicts, LoadCalculator.LoadResult load) {
            this.conflicts = conflicts;
            this.load = load;
        }
    }
}
