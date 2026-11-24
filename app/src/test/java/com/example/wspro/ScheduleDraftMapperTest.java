package com.example.wspro;

import org.junit.Test;

import java.util.Collections;
import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class ScheduleDraftMapperTest {
    @Test
    public void editedNameSurvivesCollapseExpandAndSave() {
        TimeSlotItem draftItem = new TimeSlotItem(
                "", "MON", "9:00 AM", "10:00 AM", false, "#FFFFFF");
        TimeSlotItem boundItem = draftItem.copy();

        TimeSlotAdapter.updateSubject(boundItem, "Biology", (original, updated) -> {
            if (!Objects.equals(original.getSubject(), updated.getSubject())) {
                draftItem.setSubject(updated.getSubject());
            }
        });

        TimeSlotItem collapsedItem = boundItem.copy();
        TimeSlotItem expandedItem = collapsedItem.copy();
        assertEquals("Biology", expandedItem.getSubject());

        ScheduleItem savedItem = ScheduleDraftMapper.toScheduleItems(
                Collections.singletonList(draftItem), false).get(0);
        assertEquals("Biology", savedItem.getSubject());
    }

    @Test
    public void blankNameUsesTimeFallbackWithoutReplacingEnteredName() {
        TimeSlotItem unnamed = new TimeSlotItem(
                "  ", "MON", "9:00 AM", "10:00 AM", false, "#FFFFFF");
        TimeSlotItem named = new TimeSlotItem(
                "Biology", "TUE", "11:00 AM", "12:00 PM", false, "#FFFFFF");

        assertEquals("Time 1", ScheduleDraftMapper.toScheduleItems(
                Collections.singletonList(unnamed), false).get(0).getSubject());
        assertEquals("Biology", ScheduleDraftMapper.toScheduleItems(
                Collections.singletonList(named), false).get(0).getSubject());
    }
}
