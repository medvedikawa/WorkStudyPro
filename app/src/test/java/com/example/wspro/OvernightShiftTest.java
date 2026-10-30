package com.example.wspro;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class OvernightShiftTest {

    @Test
    public void testOvernightWorkShiftLoadCalculation() {
        List<ScheduleItem> schedules = new ArrayList<>();
        // Work shift: 7:00 PM to 3:00 AM (8 hours)
        schedules.add(new ScheduleItem("Night Shift Barista", "FRI", "7:00 PM", "3:00 AM", true));
        // Class: 10:00 AM to 1:00 PM (3 hours)
        schedules.add(new ScheduleItem("Software Engineering", "MON", "10:00 AM", "1:00 PM", false));

        LoadCalculator.LoadResult result = LoadCalculator.calculate(schedules, 20);

        assertEquals(11.0, result.totalHours, 0.01);
        assertEquals(8.0, result.workHours, 0.01);
        assertEquals(3.0, result.classHours, 0.01);
    }

    @Test
    public void testOvernightShiftNextDaySpilloverConflict() {
        // Friday overnight shift: 7:00 PM to 3:00 AM (spills into Saturday 12:00 AM - 3:00 AM)
        // Saturday class: 2:00 AM to 4:00 AM (overlaps with Friday's spillover between 2:00 AM and 3:00 AM)
        boolean hasConflict = ConflictChecker.hasConflict(
                "FRI", "7:00 PM", "3:00 AM",
                "SAT", "2:00 AM", "4:00 AM"
        );
        assertTrue("Friday 7PM-3AM shift should conflict with Saturday 2AM-4AM class", hasConflict);
    }

    @Test
    public void testOvernightShiftNoConflictWhenClear() {
        // Friday overnight shift: 7:00 PM to 3:00 AM (spills into Saturday 12:00 AM - 3:00 AM)
        // Saturday class: 4:00 AM to 6:00 AM (clear of spillover)
        boolean hasConflict = ConflictChecker.hasConflict(
                "FRI", "7:00 PM", "3:00 AM",
                "SAT", "4:00 AM", "6:00 AM"
        );
        assertFalse("Friday 7PM-3AM shift should NOT conflict with Saturday 4AM-6AM class", hasConflict);
    }

    @Test
    public void testTouchingIntervalsDoNotConflict() {
        // Wednesday 5:00 PM to 6:00 PM and Wednesday 6:00 PM to 8:00 PM
        boolean hasConflict = ConflictChecker.hasConflict(
                "WED", "5:00 PM", "6:00 PM",
                "WED", "6:00 PM", "8:00 PM"
        );
        assertFalse("Touching time boundaries should not conflict", hasConflict);
    }
}
