package com.example.wspro;

import java.util.List;

public final class LocalDataRepository {

    private LocalDataRepository() { }

    public static void saveSchedules(AppDatabase db, List<ScheduleItem> schedules) {
        db.runInTransaction(() -> {
            List<Long> ids = db.scheduleDao().insertAll(schedules);
            for (int i = 0; i < ids.size(); i++) {
                long id = ids.get(i);
                if (id == -1L) continue;
                schedules.get(i).setId((int) id);
                db.syncChangeDao().enqueue(new SyncChange(
                        SyncChange.SCHEDULE, (int) id, SyncChange.UPSERT, System.currentTimeMillis()));
            }
        });
        NotificationTriggerEngine.triggerImmediately(WorkStudyProApplication.getAppContext());
    }

    public static long saveAssignment(AppDatabase db, AssignmentItem assignment) {
        final long[] id = {-1L};
        db.runInTransaction(() -> {
            id[0] = db.assignmentDao().insert(assignment);
            assignment.setId((int) id[0]);
            db.syncChangeDao().enqueue(new SyncChange(
                    SyncChange.ASSIGNMENT, (int) id[0], SyncChange.UPSERT, System.currentTimeMillis()));
        });
        NotificationTriggerEngine.triggerImmediately(WorkStudyProApplication.getAppContext());
        return id[0];
    }

    public static void updateAssignment(AppDatabase db, AssignmentItem assignment) {
        db.runInTransaction(() -> {
            db.assignmentDao().update(assignment);
            db.syncChangeDao().enqueue(new SyncChange(
                    SyncChange.ASSIGNMENT, assignment.getId(), SyncChange.UPSERT, System.currentTimeMillis()));
        });
        NotificationTriggerEngine.triggerImmediately(WorkStudyProApplication.getAppContext());
    }

    public static void deleteAssignment(AppDatabase db, AssignmentItem assignment) {
        db.runInTransaction(() -> {
            db.assignmentDao().markDeleted(assignment.getId());
            db.syncChangeDao().enqueue(new SyncChange(
                    SyncChange.ASSIGNMENT, assignment.getId(), SyncChange.DELETE, System.currentTimeMillis()));
        });
    }

    public static void deleteSchedule(AppDatabase db, ScheduleItem schedule) {
        deleteSchedules(db, java.util.Collections.singletonList(schedule));
    }

    public static void deleteSchedules(AppDatabase db, List<ScheduleItem> schedules) {
        db.runInTransaction(() -> {
            long now = System.currentTimeMillis();
            for (ScheduleItem schedule : schedules) {
                db.scheduleDao().markDeleted(schedule.getId());
                db.syncChangeDao().enqueue(new SyncChange(
                        SyncChange.SCHEDULE, schedule.getId(), SyncChange.DELETE, now));
            }
        });
        NotificationTriggerEngine.triggerImmediately(WorkStudyProApplication.getAppContext());
    }

    public static void restoreSchedules(AppDatabase db, List<ScheduleItem> schedules) {
        db.runInTransaction(() -> {
            long now = System.currentTimeMillis();
            for (ScheduleItem schedule : schedules) {
                ScheduleItem stored = db.scheduleDao().findById(schedule.getId());
                if (stored != null) {
                    stored.setDeleted(false);
                    db.scheduleDao().update(stored);
                    db.syncChangeDao().enqueue(new SyncChange(
                            SyncChange.SCHEDULE, stored.getId(), SyncChange.UPSERT, now));
                }
            }
        });
        NotificationTriggerEngine.triggerImmediately(WorkStudyProApplication.getAppContext());
    }

    public static int restoreSchedule(AppDatabase db, ScheduleItem schedule) {
        int[] restoredId = {schedule.getId()};
        db.runInTransaction(() -> {
            ScheduleItem stored = db.scheduleDao().findById(schedule.getId());
            if (stored == null) {
                ScheduleItem restored = new ScheduleItem(
                        schedule.getSubject(), schedule.getDay(), schedule.getStartTime(),
                        schedule.getEndTime(), schedule.isWork(), schedule.getColorHex());
                restored.setCloudId(schedule.getCloudId());
                restored.setGroupId(schedule.getGroupId());
                restored.setDeleted(false);
                restoredId[0] = (int) db.scheduleDao().insert(restored);
            } else {
                stored.setDeleted(false);
                db.scheduleDao().update(stored);
            }
            db.syncChangeDao().enqueue(new SyncChange(
                    SyncChange.SCHEDULE, restoredId[0], SyncChange.UPSERT, System.currentTimeMillis()));
        });
        NotificationTriggerEngine.triggerImmediately(WorkStudyProApplication.getAppContext());
        return restoredId[0];
    }

    public static void updateSchedule(AppDatabase db, ScheduleItem schedule) {
        db.runInTransaction(() -> {
            db.scheduleDao().update(schedule);
            db.syncChangeDao().enqueue(new SyncChange(
                    SyncChange.SCHEDULE, schedule.getId(), SyncChange.UPSERT, System.currentTimeMillis()));
        });
        NotificationTriggerEngine.triggerImmediately(WorkStudyProApplication.getAppContext());
    }

    public static void saveEditedSchedules(AppDatabase db, List<ScheduleItem> edited,
                                           List<Integer> deletedIds) {
        db.runInTransaction(() -> {
            long now = System.currentTimeMillis();
            for (ScheduleItem editedItem : edited) {
                if (editedItem.getId() > 0) {
                    ScheduleItem existing = db.scheduleDao().findById(editedItem.getId());
                    if (existing == null || existing.isDeleted()) continue;
                    existing.setSubject(editedItem.getSubject());
                    existing.setDay(editedItem.getDay());
                    existing.setStartTime(editedItem.getStartTime());
                    existing.setEndTime(editedItem.getEndTime());
                    existing.setWork(editedItem.isWork());
                    existing.setColorHex(editedItem.getColorHex());
                    existing.setGroupId(editedItem.getGroupId());
                    db.scheduleDao().update(existing);
                    db.syncChangeDao().enqueue(new SyncChange(
                            SyncChange.SCHEDULE, existing.getId(), SyncChange.UPSERT, now));
                } else {
                    long id = db.scheduleDao().insert(editedItem);
                    editedItem.setId((int) id);
                    db.syncChangeDao().enqueue(new SyncChange(
                            SyncChange.SCHEDULE, (int) id, SyncChange.UPSERT, now));
                }
            }
            for (Integer deletedId : deletedIds) {
                if (deletedId == null || deletedId <= 0) continue;
                db.scheduleDao().deleteById(deletedId);
                db.syncChangeDao().enqueue(new SyncChange(
                        SyncChange.SCHEDULE, deletedId, SyncChange.DELETE, now));
            }
        });
        NotificationTriggerEngine.triggerImmediately(WorkStudyProApplication.getAppContext());
    }

    public static void insertCloudSchedules(AppDatabase db, List<ScheduleItem> schedules) {
        db.runInTransaction(() -> db.scheduleDao().insertAll(schedules));
        NotificationTriggerEngine.triggerImmediately(WorkStudyProApplication.getAppContext());
    }

    public static void insertCloudAssignments(AppDatabase db, List<AssignmentItem> assignments) {
        db.runInTransaction(() -> db.assignmentDao().insertAll(assignments));
        NotificationTriggerEngine.triggerImmediately(WorkStudyProApplication.getAppContext());
    }
}
