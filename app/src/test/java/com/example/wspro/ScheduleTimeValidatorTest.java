package com.example.wspro;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ScheduleTimeValidatorTest {
    @Test
    public void normalizeDaySupportsExistingAliases() {
        assertEquals("MON", ScheduleTimeValidator.normalizeDay("Monday"));
        assertEquals("THU", ScheduleTimeValidator.normalizeDay("R"));
        assertEquals("", ScheduleTimeValidator.normalizeDay("Choose a day"));
    }

    @Test
    public void validateForSaveAcceptsTwelveAndTwentyFourHourTimes() {
        TimeSlotItem twelveHour = new TimeSlotItem(
                "Class", "MON", "9:00 AM", "10:00 AM", false, "#FFFFFF");
        TimeSlotItem twentyFourHour = new TimeSlotItem(
                "Shift", "TUE", "13:00", "14:00", true, "#FFFFFF");

        assertTrue(ScheduleTimeValidator.validateForSave(
                java.util.Arrays.asList(twelveHour, twentyFourHour)));
        assertNull(twelveHour.getScheduleError());
        assertNull(twentyFourHour.getScheduleError());
    }

    @Test
    public void validateForSaveMarksMissingDayAndNonIncreasingTime() {
        TimeSlotItem missingDay = new TimeSlotItem(
                "Class", "", "9:00 AM", "10:00 AM", false, "#FFFFFF");
        TimeSlotItem reversedTime = new TimeSlotItem(
                "Shift", "TUE", "2:00 PM", "1:00 PM", true, "#FFFFFF");

        assertFalse(ScheduleTimeValidator.validateForSave(
                java.util.Arrays.asList(missingDay, reversedTime)));
        assertEquals("Select at least one day.", missingDay.getScheduleError());
        assertEquals("End time must be after start time.", reversedTime.getEndTimeError());
    }

    @Test
    public void validateForSaveAcceptsEmptyDraft() {
        assertTrue(ScheduleTimeValidator.validateForSave(Collections.emptyList()));
    }
}
