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
    }

    public static long saveAssignment(AppDatabase db, AssignmentItem assignment) {
        final long[] id = {-1L};
        db.runInTransaction(() -> {
            id[0] = db.assignmentDao().insert(assignment);
            assignment.setId((int) id[0]);
            db.syncChangeDao().enqueue(new SyncChange(
                    SyncChange.ASSIGNMENT, (int) id[0], SyncChange.UPSERT, System.currentTimeMillis()));
        });
        return id[0];
    }

    public static void updateAssignment(AppDatabase db, AssignmentItem assignment) {
        db.runInTransaction(() -> {
            db.assignmentDao().update(assignment);
            db.syncChangeDao().enqueue(new SyncChange(
                    SyncChange.ASSIGNMENT, assignment.getId(), SyncChange.UPSERT, System.currentTimeMillis()));
        });
    }

    public static void deleteAssignment(AppDatabase db, AssignmentItem assignment) {
        db.runInTransaction(() -> {
            db.assignmentDao().markDeleted(assignment.getId());
            db.syncChangeDao().enqueue(new SyncChange(
                    SyncChange.ASSIGNMENT, assignment.getId(), SyncChange.DELETE, System.currentTimeMillis()));
        });
    }

    public static void deleteSchedule(AppDatabase db, ScheduleItem schedule) {
        db.runInTransaction(() -> {
            db.scheduleDao().markDeleted(schedule.getId());
            db.syncChangeDao().enqueue(new SyncChange(
                    SyncChange.SCHEDULE, schedule.getId(), SyncChange.DELETE, System.currentTimeMillis()));
        });
    }

    public static void updateSchedule(AppDatabase db, ScheduleItem schedule) {
        db.runInTransaction(() -> {
            db.scheduleDao().update(schedule);
            db.syncChangeDao().enqueue(new SyncChange(
                    SyncChange.SCHEDULE, schedule.getId(), SyncChange.UPSERT, System.currentTimeMillis()));
        });
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
    }

    public static void insertCloudSchedules(AppDatabase db, List<ScheduleItem> schedules) {
        db.runInTransaction(() -> db.scheduleDao().insertAll(schedules));
    }

    public static void insertCloudAssignments(AppDatabase db, List<AssignmentItem> assignments) {
        db.runInTransaction(() -> db.assignmentDao().insertAll(assignments));
    }
}
