package com.example.wspro;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PriorityEngineTest {

    @Test
    public void workTasksAreNotWeightedOrMarkedAsShiftCollisions() {
        long dueTimestamp = System.currentTimeMillis() + 60 * 60 * 1000L;
        AssignmentItem workTask = new AssignmentItem(
                "Submit timesheet", "", "", "date", "time", dueTimestamp, false,
                AssignmentItem.TYPE_WORK);
        List<ScheduleItem> schedules = new ArrayList<>();
        schedules.add(new ScheduleItem("Shift", PriorityEngine.getDayCodeFromTimestamp(dueTimestamp),
                "9:00 AM", "5:00 PM", true));

        PriorityEngine.calculateAndSort(List.of(workTask), schedules);

        assertEquals(0, workTask.getPriorityScore(), 0);
        assertNull(workTask.getPriorityReason());
        assertFalse(workTask.isHasWorkCollision());
    }

    @Test
    public void schoolTasksRemainPrioritizedAheadOfWorkTasks() {
        long dueTimestamp = System.currentTimeMillis() + 60 * 60 * 1000L;
        AssignmentItem schoolTask = new AssignmentItem(
                "Submit assignment", "Biology", "Task Performance", "date", "time", dueTimestamp,
                false, AssignmentItem.TYPE_SCHOOL);
        AssignmentItem workTask = new AssignmentItem(
                "Submit timesheet", "", "", "date", "time", dueTimestamp, false,
                AssignmentItem.TYPE_WORK);

        List<AssignmentItem> sorted = PriorityEngine.calculateAndSort(
                List.of(workTask, schoolTask), new ArrayList<>());

        assertSame(schoolTask, sorted.get(0));
        assertSame(workTask, sorted.get(1));
        assertEquals(0, workTask.getPriorityScore(), 0);
    }
}
