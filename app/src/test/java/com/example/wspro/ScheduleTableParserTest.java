package com.example.wspro;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ScheduleTableParserTest {

    private ScheduleTableParser.Word w(String text, int left, int top, int right, int bottom) {
        return new ScheduleTableParser.Word(text, left, top, right, bottom);
    }

    private void addRowWords(List<ScheduleTableParser.Word> list, String text, int startX, int topY) {
        String[] tokens = text.split("\\s+");
        int curX = startX;
        for (String t : tokens) {
            int wLen = Math.max(20, t.length() * 10);
            list.add(new ScheduleTableParser.Word(t, curX, topY, curX + wLen, topY + 20));
            curX += wLen + 10;
        }
    }

    @Test
    public void testFixture1_FullStiFormWithOutsideTableNoiseAndCharges() {
        List<ScheduleTableParser.Word> words = new ArrayList<>();
        int y = 10;

        // Header outside table
        addRowWords(words, "STI College Global City", 50, y); y += 30;
        addRowWords(words, "32nd St. corner 9th Ave, BGC, Taguig City", 50, y); y += 30;
        addRowWords(words, "Tel: 888-1234", 50, y); y += 30;
        addRowWords(words, "Student No: 200012345 Name: Juan Dela Cruz Course: BSIT", 50, y); y += 40;

        // Table Header Row: Course Description | Units | Class No / Sec | Days | Time | Room
        int colSubjectX = 50;
        int colUnitsX = 350;
        int colSecX = 420;
        int colDaysX = 500;
        int colTimeX = 580;
        int colRoomX = 750;

        addRowWords(words, "Course Description", colSubjectX, y);
        addRowWords(words, "Units", colUnitsX, y);
        addRowWords(words, "Class No / Sec", colSecX, y);
        addRowWords(words, "Days", colDaysX, y);
        addRowWords(words, "Time", colTimeX, y);
        addRowWords(words, "Room", colRoomX, y);
        y += 35;

        // Row 1 & Continuation line: Information Assurance and Security (Data Privacy)
        // Row 1 (MON 3:00 PM - 5:00 PM)
        addRowWords(words, "Information Assurance and Security", colSubjectX, y);
        addRowWords(words, "3.00", colUnitsX, y);
        addRowWords(words, "IT401", colSecX, y);
        addRowWords(words, "MON", colDaysX, y);
        addRowWords(words, "3:00 PM - 5:00 PM", colTimeX, y);
        y += 25;
        // Continuation line + Row 2 (FRI 10:00 AM - 1:00 PM, Room COM001)
        addRowWords(words, "(Data Privacy)", colSubjectX, y);
        addRowWords(words, "FRI", colDaysX, y);
        addRowWords(words, "10:00 AM - 1:00 PM", colTimeX, y);
        addRowWords(words, "COM001", colRoomX, y);
        y += 35;

        // Row 3 & 4: IT Capstone Project 2 (WED 7:00 AM - 10:00 AM & WED 6:00 PM - 8:00 PM)
        addRowWords(words, "IT Capstone Project 2", colSubjectX, y);
        addRowWords(words, "3.00", colUnitsX, y);
        addRowWords(words, "IT402", colSecX, y);
        addRowWords(words, "WED", colDaysX, y);
        addRowWords(words, "7:00 AM - 10:00 AM", colTimeX, y);
        addRowWords(words, "LIB001", colRoomX, y);
        y += 25;
        addRowWords(words, "WED", colDaysX, y);
        addRowWords(words, "6:00 PM - 8:00 PM", colTimeX, y);
        addRowWords(words, "LIB001", colRoomX, y);
        y += 35;

        // Row 5 & 6: Network Technology 2 (THU 10:00 AM - 1:00 PM & WED 3:00 PM - 5:00 PM)
        addRowWords(words, "Network Technology 2", colSubjectX, y);
        addRowWords(words, "3.00", colUnitsX, y);
        addRowWords(words, "IT403", colSecX, y);
        addRowWords(words, "THU", colDaysX, y);
        addRowWords(words, "10:00 AM - 1:00 PM", colTimeX, y);
        addRowWords(words, "COM001", colRoomX, y);
        y += 25;
        addRowWords(words, "WED", colDaysX, y);
        addRowWords(words, "3:00 PM - 5:00 PM", colTimeX, y);
        addRowWords(words, "RM106", colRoomX, y);
        y += 35;

        // Row 7: Euthenics 2 (WED 5:00 PM - 6:00 PM)
        addRowWords(words, "Euthenics 2", colSubjectX, y);
        addRowWords(words, "1.00", colUnitsX, y);
        addRowWords(words, "GE101", colSecX, y);
        addRowWords(words, "WED", colDaysX, y);
        addRowWords(words, "5:00 PM - 6:00 PM", colTimeX, y);
        addRowWords(words, "RM106", colRoomX, y);
        y += 35;

        // Row 8: IT Service Management (THU 2:30 PM - 5:30 PM)
        addRowWords(words, "IT Service Management", colSubjectX, y);
        addRowWords(words, "3.00", colUnitsX, y);
        addRowWords(words, "IT404", colSecX, y);
        addRowWords(words, "THU", colDaysX, y);
        addRowWords(words, "2:30 PM - 5:30 PM", colTimeX, y);
        addRowWords(words, "AMP001", colRoomX, y);
        y += 35;

        // Row 9 & 10: Computer Graphics Programming (FRI 2:30 PM - 4:30 PM & FRI 5:00 PM - 8:00 PM)
        addRowWords(words, "Computer Graphics Programming", colSubjectX, y);
        addRowWords(words, "3.00", colUnitsX, y);
        addRowWords(words, "IT405", colSecX, y);
        addRowWords(words, "FRI", colDaysX, y);
        addRowWords(words, "2:30 PM - 4:30 PM", colTimeX, y);
        y += 25;
        addRowWords(words, "FRI", colDaysX, y);
        addRowWords(words, "5:00 PM - 8:00 PM", colTimeX, y);
        y += 35;

        // Row 11 & 12: Game Development (SAT 4:00 PM - 6:00 PM & SAT 1:00 PM - 4:00 PM)
        addRowWords(words, "Game Development", colSubjectX, y);
        addRowWords(words, "3.00", colUnitsX, y);
        addRowWords(words, "IT406", colSecX, y);
        addRowWords(words, "SAT", colDaysX, y);
        addRowWords(words, "4:00 PM - 6:00 PM", colTimeX, y);
        addRowWords(words, "RM101", colRoomX, y);
        y += 25;
        addRowWords(words, "SAT", colDaysX, y);
        addRowWords(words, "1:00 PM - 4:00 PM", colTimeX, y);
        addRowWords(words, "COM002", colRoomX, y);
        y += 40;

        // Footer: Total Units 19.00
        addRowWords(words, "Total Units: 19.00", colSubjectX, y); y += 35;

        // Noise outside table: Charges & Payment Schedule
        addRowWords(words, "CHARGES for 2026-2027/1st Term", 50, y); y += 25;
        addRowWords(words, "Assessment Date: 2026-08-15 Payment Due: 2026-09-01", 50, y); y += 25;
        addRowWords(words, "Tuition Fee 15000.00 Balance 5000.00", 50, y);

        ScheduleTableParser.ParseResult result = ScheduleTableParser.parse(words);

        assertFalse("Quality gate should pass", result.qualityGateFailed);
        assertEquals("Should produce exactly 12 meetings", 12, result.meetings.size());

        // Check total units calculation
        assertNotNull("Printed total units should be parsed", result.totalUnitsPrinted);
        assertEquals(19.00, result.totalUnitsPrinted, 0.01);
        assertEquals(19.00, result.totalUnitsCalculated, 0.01);
        assertFalse("Units should match", result.unitsMismatch);

        // Verify subjects and continuation
        assertEquals("Information Assurance and Security (Data Privacy)", result.meetings.get(0).subject);
        assertEquals("Information Assurance and Security (Data Privacy)", result.meetings.get(1).subject);
        assertEquals("IT Capstone Project 2", result.meetings.get(2).subject);
        assertEquals("Game Development", result.meetings.get(11).subject);

        // Verify CHARGES for 2026-2027/1st Term did not produce a time meeting
        for (ScheduleTableParser.ParsedMeeting m : result.meetings) {
            assertFalse(m.subject.contains("CHARGES"));
            assertFalse(m.startTime.contains("2026"));
        }
    }

    @Test
    public void testFixture2_DaysAndTimeInOneCellAndSplitDayToken() {
        List<ScheduleTableParser.Word> words = new ArrayList<>();
        int y = 50;

        // Header
        addRowWords(words, "Course Description", 50, y);
        addRowWords(words, "Days & Time", 300, y);
        y += 35;

        // Row 1: TTh 10:00AM-11:30AM in days & time column
        addRowWords(words, "Software Engineering", 50, y);
        addRowWords(words, "TTh 10:00AM-11:30AM", 300, y);
        y += 35;

        // Row 2: Split day token "T Th"
        addRowWords(words, "Database Systems", 50, y);
        addRowWords(words, "T Th 1:00PM-2:30PM", 300, y);

        ScheduleTableParser.ParseResult result = ScheduleTableParser.parse(words);

        assertFalse("Quality gate should pass", result.qualityGateFailed);
        assertEquals("Should produce 4 meetings (2 per combined TTh group)", 4, result.meetings.size());

        assertEquals("TUE", result.meetings.get(0).day);
        assertEquals("10:00 AM", result.meetings.get(0).startTime);
        assertEquals("11:30 AM", result.meetings.get(0).endTime);

        assertEquals("THU", result.meetings.get(1).day);
        assertEquals("10:00 AM", result.meetings.get(1).startTime);
        assertEquals("11:30 AM", result.meetings.get(1).endTime);
    }

    @Test
    public void testFixture3_FilipinoDayNamesAndMonFriRange() {
        List<ScheduleTableParser.Word> words = new ArrayList<>();
        int y = 50;

        addRowWords(words, "Subject", 50, y);
        addRowWords(words, "Days", 250, y);
        addRowWords(words, "Schedule", 500, y);
        y += 35;

        addRowWords(words, "Filipino Culture", 50, y);
        addRowWords(words, "Lunes, Martes, Huwebes", 250, y);
        addRowWords(words, "8:00 AM - 9:00 AM", 500, y);
        y += 35;

        addRowWords(words, "Physical Fitness", 50, y);
        addRowWords(words, "Mon-Fri", 250, y);
        addRowWords(words, "1:00 PM - 2:00 PM", 500, y);

        ScheduleTableParser.ParseResult result = ScheduleTableParser.parse(words);

        assertFalse(result.qualityGateFailed);

        // Lunes, Martes, Huwebes -> MON, TUE, THU (3 meetings)
        // Mon-Fri -> MON, TUE, WED, THU, FRI (5 meetings)
        assertEquals(8, result.meetings.size());
        assertEquals("MON", result.meetings.get(0).day);
        assertEquals("TUE", result.meetings.get(1).day);
        assertEquals("THU", result.meetings.get(2).day);
    }

    @Test
    public void testFixture4_RowWithTimeOfTBA() {
        List<ScheduleTableParser.Word> words = new ArrayList<>();
        int y = 50;

        addRowWords(words, "Course Title", 50, y);
        addRowWords(words, "Units", 250, y);
        addRowWords(words, "Time", 320, y);
        y += 35;

        addRowWords(words, "Independent Research Project", 50, y);
        addRowWords(words, "3.00", 250, y);
        addRowWords(words, "TBA", 320, y);

        ScheduleTableParser.ParseResult result = ScheduleTableParser.parse(words);

        assertEquals("Should produce 0 meetings for TBA row", 0, result.meetings.size());
        assertEquals("Should add 1 entry in skipped lines", 1, result.skippedLines.size());
        assertTrue("Skipped line should contain subject or TBA", result.skippedLines.get(0).contains("TBA") || result.skippedLines.get(0).contains("Independent Research"));
    }

    @Test
    public void testFixture5_PageWithOnlyNoiseText() {
        List<ScheduleTableParser.Word> words = new ArrayList<>();
        words.add(w("Welcome", 10, 10, 100, 30));
        words.add(w("to", 110, 10, 130, 30));
        words.add(w("WorkStudyPro", 140, 10, 250, 30));
        words.add(w("random", 10, 50, 80, 70));
        words.add(w("notes", 90, 50, 150, 70));

        ScheduleTableParser.ParseResult result = ScheduleTableParser.parse(words);

        assertTrue("Quality gate must fail on noise text", result.qualityGateFailed);
        assertEquals("0 meetings expected", 0, result.meetings.size());
        assertNotNull("Quality gate message expected", result.qualityGateMessage);
        assertTrue(result.qualityGateMessage.contains("We couldn't read this clearly"));
    }

    @Test
    public void testPhase4_WeeklyGridDaysAsColumns() {
        List<ScheduleTableParser.Word> words = new ArrayList<>();
        int y = 50;

        // Grid Header: Time | Mon | Wed | Fri
        addRowWords(words, "Time", 50, y);
        addRowWords(words, "Mon", 200, y);
        addRowWords(words, "Wed", 350, y);
        addRowWords(words, "Fri", 500, y);
        y += 35;

        // Row 1: 8:00 AM - 10:00 AM | IT 101 | IT 101 | CS 202
        addRowWords(words, "8:00 AM - 10:00 AM", 50, y);
        addRowWords(words, "IT 101", 200, y);
        addRowWords(words, "IT 101", 350, y);
        addRowWords(words, "CS 202", 500, y);

        ScheduleTableParser.ParseResult result = ScheduleTableParser.parse(words);

        assertFalse("Weekly grid quality gate should pass", result.qualityGateFailed);
        assertEquals("Should extract 3 meetings from grid row", 3, result.meetings.size());

        assertEquals("IT 101", result.meetings.get(0).subject);
        assertEquals("MON", result.meetings.get(0).day);
        assertEquals("8:00 AM", result.meetings.get(0).startTime);
        assertEquals("10:00 AM", result.meetings.get(0).endTime);

        assertEquals("IT 101", result.meetings.get(1).subject);
        assertEquals("WED", result.meetings.get(1).day);

        assertEquals("CS 202", result.meetings.get(2).subject);
        assertEquals("FRI", result.meetings.get(2).day);
    }

    @Test
    public void testPhase4_TransposedGridTimesAsColumns() {
        List<ScheduleTableParser.Word> words = new ArrayList<>();
        int y = 50;

        // Transposed Header: Day | 8:00 AM - 9:00 AM | 9:00 AM - 10:00 AM
        addRowWords(words, "Day", 50, y);
        addRowWords(words, "8:00 AM - 9:00 AM", 200, y);
        addRowWords(words, "9:00 AM - 10:00 AM", 400, y);
        y += 35;

        // Row 1: MON | Calculus | Physics
        addRowWords(words, "MON", 50, y);
        addRowWords(words, "Calculus", 200, y);
        addRowWords(words, "Physics", 400, y);

        ScheduleTableParser.ParseResult result = ScheduleTableParser.parse(words);

        assertFalse("Transposed grid quality gate should pass", result.qualityGateFailed);
        assertEquals("Should extract 2 meetings from transposed row", 2, result.meetings.size());

        assertEquals("Calculus", result.meetings.get(0).subject);
        assertEquals("MON", result.meetings.get(0).day);
        assertEquals("8:00 AM", result.meetings.get(0).startTime);
        assertEquals("9:00 AM", result.meetings.get(0).endTime);

        assertEquals("Physics", result.meetings.get(1).subject);
        assertEquals("MON", result.meetings.get(1).day);
        assertEquals("9:00 AM", result.meetings.get(1).startTime);
        assertEquals("10:00 AM", result.meetings.get(1).endTime);
    }
}
