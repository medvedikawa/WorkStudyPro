package com.example.wspro;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScheduleConflictFinder {
    private ScheduleConflictFinder() {
    }

    public static List<String> findConflicts(AppDatabase db, List<ScheduleItem> proposed,
                                             List<Integer> deletedIds) {
        Set<Integer> replacedIds = new HashSet<>(deletedIds);
        for (ScheduleItem item : proposed) {
            if (item.getId() > 0) replacedIds.add(item.getId());
        }

        List<ScheduleItem> existingSchedules = db.scheduleDao().getAll();
        List<ScheduleItem> checked = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        for (ScheduleItem item : proposed) {
            for (ScheduleItem existing : existingSchedules) {
                if (replacedIds.contains(existing.getId()) || existing.isWork() != item.isWork()) continue;
                if (ConflictChecker.hasConflict(item.getDay(), item.getStartTime(), item.getEndTime(),
                        existing.getDay(), existing.getStartTime(), existing.getEndTime())) {
                    conflicts.add(ScheduleConflictText.format(item, existing));
                }
            }
            for (ScheduleItem previous : checked) {
                if (ConflictChecker.hasConflict(item.getDay(), item.getStartTime(), item.getEndTime(),
                        previous.getDay(), previous.getStartTime(), previous.getEndTime())) {
                    conflicts.add(ScheduleConflictText.format(item, previous));
                }
            }
            checked.add(item);
        }
        return conflicts;
    }
}
