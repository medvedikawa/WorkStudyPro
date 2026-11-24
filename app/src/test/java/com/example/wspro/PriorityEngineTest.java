package com.example.wspro;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class PriorityEngineTest {

    @Test
    public void classifyTaskCorrectly() {
        long now = System.currentTimeMillis();
        AssignmentItem criticalTask = new AssignmentItem("Critical", "Sub", "School", "date", "time", now + 10 * 3600 * 1000L, false);
        criticalTask.setImportance(PriorityEngine.IMPORTANCE_HIGH);
        assertEquals(PriorityEngine.CLASS_CRITICAL, PriorityEngine.classifyTask(criticalTask, now));

        AssignmentItem urgentTask = new AssignmentItem("Urgent", "Sub", "School", "date", "time", now + 10 * 3600 * 1000L, false);
        urgentTask.setImportance(PriorityEngine.IMPORTANCE_LOW);
        assertEquals(PriorityEngine.CLASS_URGENT, PriorityEngine.classifyTask(urgentTask, now));

        AssignmentItem notUrgentTask = new AssignmentItem("Not Urgent", "Sub", "School", "date", "time", now + 72 * 3600 * 1000L, false);
        notUrgentTask.setImportance(PriorityEngine.IMPORTANCE_MID);
        assertEquals(PriorityEngine.CLASS_NOT_URGENT, PriorityEngine.classifyTask(notUrgentTask, now));

        AssignmentItem minorTask = new AssignmentItem("Minor", "Sub", "School", "date", "time", now + 72 * 3600 * 1000L, false);
        minorTask.setImportance(PriorityEngine.IMPORTANCE_LOW);
        assertEquals(PriorityEngine.CLASS_MINOR, PriorityEngine.classifyTask(minorTask, now));
    }

    @Test
    public void sortGroupsInCorrectOrderAndEarliestFirst() {
        long now = System.currentTimeMillis();
        AssignmentItem minor = new AssignmentItem("Minor", "Sub", "School", "date", "time", now + 100 * 3600 * 1000L, false);
        minor.setImportance(PriorityEngine.IMPORTANCE_LOW);

        AssignmentItem criticalEarly = new AssignmentItem("Critical Early", "Sub", "School", "date", "time", now + 5 * 3600 * 1000L, false);
        criticalEarly.setImportance(PriorityEngine.IMPORTANCE_HIGH);

        AssignmentItem criticalLate = new AssignmentItem("Critical Late", "Sub", "School", "date", "time", now + 20 * 3600 * 1000L, false);
        criticalLate.setImportance(PriorityEngine.IMPORTANCE_HIGH);

        List<AssignmentItem> sorted = PriorityEngine.calculateAndSort(
                List.of(minor, criticalLate, criticalEarly), new ArrayList<>());

        assertSame(criticalEarly, sorted.get(0));
        assertSame(criticalLate, sorted.get(1));
        assertSame(minor, sorted.get(2));
    }
}
