package com.example.wspro;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ScheduleDraftMapper {
    private ScheduleDraftMapper() {
    }

    public static List<ScheduleItem> toScheduleItems(List<TimeSlotItem> source, boolean work) {
        List<ScheduleItem> result = new ArrayList<>();
        int index = 1;
        for (TimeSlotItem item : source) {
            if (item.getGroupId() == null && (item.getId() <= 0 || item.getDays().size() > 1)) {
                item.setGroupId(UUID.randomUUID().toString());
            }
            String subject = item.getSubject() != null ? item.getSubject().trim() : "";
            if (subject.isEmpty()) {
                subject = "Time " + index;
            }
            index++;
            for (String day : item.getDays()) {
                ScheduleItem schedule = new ScheduleItem(subject, day,
                        item.getStartTime(), item.getEndTime(), work, item.getColorHex());
                if (day.equals(item.getOriginalDay())) schedule.setId(item.getId());
                schedule.setGroupId(item.getGroupId());
                result.add(schedule);
            }
        }
        return result;
    }

    public static List<Integer> findDeletedIds(List<TimeSlotItem> current,
                                                List<TimeSlotItem> originals) {
        Set<Integer> retainedIds = new HashSet<>();
        for (TimeSlotItem item : current) {
            if (item.getId() > 0 && item.getDays().contains(item.getOriginalDay())) {
                retainedIds.add(item.getId());
            }
        }
        List<Integer> deletedIds = new ArrayList<>();
        for (TimeSlotItem original : originals) {
            if (!retainedIds.contains(original.getId())) deletedIds.add(original.getId());
        }
        return deletedIds;
    }
}
