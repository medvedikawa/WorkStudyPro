package com.example.wspro;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ScheduleGridTableParser {
    private static final String QUALITY_GATE_MESSAGE =
            "We couldn't read this clearly. Try again in better light, or add your classes manually.";

    private ScheduleGridTableParser() {
    }

    static List<ScheduleTableParser.GridDayColumn> detectWeeklyHeader(
            List<ScheduleTableParser.PhysicalRow> physicalRows) {
        for (ScheduleTableParser.PhysicalRow row : physicalRows) {
            List<ScheduleTableParser.GridDayColumn> columns = new ArrayList<>();
            double maxHeaderLeftColX = 0;

            for (ScheduleTableParser.Word word : row.words) {
                if (isExplicitGridDayWord(word.text)) {
                    List<String> parsed = ScheduleTableParser.parseDayGroupToken(word.text);
                    if (!parsed.isEmpty() && !parsed.get(0).isEmpty()) {
                        columns.add(new ScheduleTableParser.GridDayColumn(
                                parsed.get(0), word.left, word.right));
                    }
                } else if (word.text.equalsIgnoreCase("time")
                        || word.text.equalsIgnoreCase("sched")) {
                    maxHeaderLeftColX = Math.max(maxHeaderLeftColX, word.right);
                }
            }

            if (columns.size() >= 2) {
                columns.sort(Comparator.comparingDouble(column -> column.minX));
                for (int i = 0; i < columns.size(); i++) {
                    ScheduleTableParser.GridDayColumn current = columns.get(i);
                    if (i == 0) {
                        current.minX = Math.max(maxHeaderLeftColX, current.minX - 30);
                    } else {
                        ScheduleTableParser.GridDayColumn previous = columns.get(i - 1);
                        double midpoint = (previous.maxX + current.minX) / 2.0;
                        previous.maxX = midpoint;
                        current.minX = midpoint;
                    }
                    if (i == columns.size() - 1) current.maxX = 10000;
                }
                return columns;
            }
        }
        return null;
    }

    static ScheduleTableParser.ParseResult parseWeekly(
            List<ScheduleTableParser.PhysicalRow> physicalRows,
            List<ScheduleTableParser.GridDayColumn> dayColumns) {
        ScheduleTableParser.ParseResult result = new ScheduleTableParser.ParseResult();

        for (ScheduleTableParser.PhysicalRow row : physicalRows) {
            String[] timeRange = ScheduleTableParser.extractTimeRange(row.getFullText(), false);
            if (timeRange == null) continue;

            for (ScheduleTableParser.GridDayColumn column : dayColumns) {
                StringBuilder cellText = new StringBuilder();
                for (ScheduleTableParser.Word word : row.words) {
                    double centerX = word.getCenterX();
                    if (centerX < column.minX || centerX > column.maxX
                            || isExplicitGridDayWord(word.text)
                            || isTimeToken(word.text)) {
                        continue;
                    }
                    if (cellText.length() > 0) cellText.append(" ");
                    cellText.append(word.text);
                }

                String subject = ScheduleTableParser.cleanSubjectText(cellText.toString());
                if (subject.length() >= 2
                        && !subject.matches("(?i)^(time|room|sec|units|sched|day)$")) {
                    ScheduleTableParser.ParsedMeeting meeting = new ScheduleTableParser.ParsedMeeting();
                    meeting.subject = subject;
                    meeting.day = column.dayCode;
                    meeting.startTime = timeRange[0];
                    meeting.endTime = timeRange[1];
                    ScheduleTableParser.checkTimeFlags(meeting);
                    result.meetings.add(meeting);
                }
            }
        }

        finishGridResult(result);
        return result;
    }

    static List<ScheduleTableParser.GridTimeColumn> detectTransposedHeader(
            List<ScheduleTableParser.PhysicalRow> physicalRows) {
        for (ScheduleTableParser.PhysicalRow row : physicalRows) {
            String fullRowText = row.getFullText();
            if (ScheduleTableParser.extractTimeRange(fullRowText, true) == null) continue;

            List<ScheduleTableParser.GridTimeColumn> columns = new ArrayList<>();
            Pattern pattern = Pattern.compile(
                    "(\\d{1,2}(?:[:.]\\d{2})?\\s*(?:AM|PM)?)\\s*[-–—to,]+\\s*"
                            + "(\\d{1,2}(?:[:.]\\d{2})?\\s*(?:AM|PM)?)",
                    Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(fullRowText);

            while (matcher.find()) {
                String start = ScheduleTableParser.formatTime(matcher.group(1));
                String end = ScheduleTableParser.formatTime(matcher.group(2));
                if (start == null || end == null) continue;

                int startChar = matcher.start();
                int endChar = matcher.end();
                double minX = Double.MAX_VALUE;
                double maxX = 0;
                int currentPosition = 0;
                for (ScheduleTableParser.Word word : row.words) {
                    int wordLength = word.text.length();
                    if (currentPosition + wordLength >= startChar
                            && currentPosition < endChar) {
                        minX = Math.min(minX, word.left);
                        maxX = Math.max(maxX, word.right);
                    }
                    currentPosition += wordLength + 1;
                }
                if (minX == Double.MAX_VALUE) {
                    minX = 100;
                    maxX = 300;
                }
                columns.add(new ScheduleTableParser.GridTimeColumn(start, end, minX, maxX));
            }

            if (columns.size() >= 2) {
                columns.sort(Comparator.comparingDouble(column -> column.minX));
                for (int i = 0; i < columns.size(); i++) {
                    ScheduleTableParser.GridTimeColumn current = columns.get(i);
                    if (i == 0) {
                        current.minX = 100;
                    } else {
                        ScheduleTableParser.GridTimeColumn previous = columns.get(i - 1);
                        double midpoint = (previous.maxX + current.minX) / 2.0;
                        previous.maxX = midpoint;
                        current.minX = midpoint;
                    }
                    if (i == columns.size() - 1) current.maxX = 10000;
                }
                return columns;
            }
        }
        return null;
    }

    static ScheduleTableParser.ParseResult parseTransposed(
            List<ScheduleTableParser.PhysicalRow> physicalRows,
            List<ScheduleTableParser.GridTimeColumn> timeColumns) {
        ScheduleTableParser.ParseResult result = new ScheduleTableParser.ParseResult();
        int headerIndex = -1;
        for (int i = 0; i < physicalRows.size(); i++) {
            if (ScheduleTableParser.extractTimeRange(physicalRows.get(i).getFullText(), true) != null) {
                headerIndex = i;
                break;
            }
        }

        for (int i = 0; i < physicalRows.size(); i++) {
            if (i == headerIndex) continue;
            ScheduleTableParser.PhysicalRow row = physicalRows.get(i);
            List<String> days = ScheduleTableParser.extractDays(row.getFullText());
            if (days.isEmpty()) continue;
            String dayCode = days.get(0);

            for (ScheduleTableParser.GridTimeColumn column : timeColumns) {
                StringBuilder cellText = new StringBuilder();
                for (ScheduleTableParser.Word word : row.words) {
                    double centerX = word.getCenterX();
                    if (centerX < column.minX || centerX > column.maxX
                            || ScheduleTableParser.extractTimeRange(word.text, false) != null
                            || isExplicitGridDayWord(word.text)) {
                        continue;
                    }
                    if (cellText.length() > 0) cellText.append(" ");
                    cellText.append(word.text);
                }

                String subject = ScheduleTableParser.cleanSubjectText(cellText.toString());
                if (subject.length() >= 2) {
                    ScheduleTableParser.ParsedMeeting meeting = new ScheduleTableParser.ParsedMeeting();
                    meeting.subject = subject;
                    meeting.day = dayCode;
                    meeting.startTime = column.startTimeFormatted;
                    meeting.endTime = column.endTimeFormatted;
                    ScheduleTableParser.checkTimeFlags(meeting);
                    result.meetings.add(meeting);
                }
            }
        }

        finishGridResult(result);
        return result;
    }

    private static boolean isExplicitGridDayWord(String text) {
        if (text == null) return false;
        String upper = text.trim().toUpperCase(Locale.US);
        if (upper.equals("DAY") || upper.equals("DAYS") || upper.equals("SUBJECT")
                || upper.equals("COURSE") || upper.equals("DESCRIPTION")
                || upper.equals("TIME") || upper.equals("ROOM") || upper.equals("SECTION")
                || upper.equals("CODE") || upper.equals("CLASS")) {
            return false;
        }
        return upper.matches("MON|TUE|WED|THU|FRI|SAT|SUN|MONDAY|TUESDAY|WEDNESDAY|THURSDAY|"
                + "FRIDAY|SATURDAY|SUNDAY|LUNES|MARTES|MIYERKULES|HUWEBES|BIYERNES|SABADO|"
                + "LINGGO|LUN|MAR|MIY|HUW|BIY|SAB|LIN|TH|SA|SU");
    }

    private static boolean isTimeToken(String text) {
        return text.equalsIgnoreCase("AM") || text.equalsIgnoreCase("PM")
                || text.equals("-") || text.equalsIgnoreCase("to")
                || text.matches(".*\\d{1,2}[:.]\\d{2}.*");
    }

    private static void finishGridResult(ScheduleTableParser.ParseResult result) {
        ScheduleTableParser.deduplicateMeetings(result.meetings);
        ScheduleTableParser.checkMeetingOverlaps(result.meetings);
        if (result.meetings.isEmpty()) {
            result.qualityGateFailed = true;
            result.qualityGateMessage = QUALITY_GATE_MESSAGE;
        }
    }
}
