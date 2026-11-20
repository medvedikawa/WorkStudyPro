package com.example.wspro;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ScheduleStandardListParser {
    private enum HeaderType {
        SUBJECT, SECTION, DAYS, TIME, ROOM, INSTRUCTOR, UNKNOWN
    }

    private static class ColumnInfo {
        HeaderType type;
        double minX;
        double maxX;

        ColumnInfo(HeaderType type, double minX, double maxX) {
            this.type = type;
            this.minX = minX;
            this.maxX = maxX;
        }
    }

    private static class CourseAnchor {
        String subject = "";
        String section = "";
        double topY;
        double bottomY;
        double centerY;
    }

    private static class RawRowMeeting {
        ScheduleTableParser.PhysicalRow row;
        String startTimeFormatted;
        String endTimeFormatted;
        List<String> parsedDays = new ArrayList<>();
        String roomRaw = "";
        String sectionRaw = "";
        String subjectRaw = "";
        boolean isDayMissing;
    }

    private ScheduleStandardListParser() {
    }

    static ScheduleTableParser.ParseResult parse(
            List<ScheduleTableParser.PhysicalRow> physicalRows,
            List<ScheduleTableParser.Word> words) {
        ScheduleTableParser.ParseResult result = new ScheduleTableParser.ParseResult();

        ScheduleTableParser.PhysicalRow headerRow = null;
        int headerRowIndex = -1;
        List<ColumnInfo> columns = new ArrayList<>();

        for (int i = 0; i < physicalRows.size(); i++) {
            ScheduleTableParser.PhysicalRow row = physicalRows.get(i);
            List<ColumnInfo> detectedColumns = detectHeaderColumns(row);
            if (detectedColumns.size() >= 2) {
                headerRow = row;
                headerRowIndex = i;
                columns = detectedColumns;
                break;
            }
        }

        double tableTopY = 0;
        double tableBottomY = Double.MAX_VALUE;
        if (headerRow != null) {
            tableTopY = headerRow.bottom;
        } else {
            for (ScheduleTableParser.PhysicalRow row : physicalRows) {
                if (hasValidTimeRange(row.getFullText())) {
                    tableTopY = row.top;
                    break;
                }
            }
        }

        for (ScheduleTableParser.PhysicalRow row : physicalRows) {
            if (row.centerY < tableTopY) continue;

            String lineText = row.getFullText();
            if (isFinancialOrFooterRow(lineText)) {
                if (row.top < tableBottomY) tableBottomY = row.top;
            }
        }

        if (tableBottomY == Double.MAX_VALUE) {
            double maxBottom = 0;
            for (ScheduleTableParser.Word word : words) {
                if (word.bottom > maxBottom) maxBottom = word.bottom;
            }
            tableBottomY = maxBottom + 10;
        }

        List<ScheduleTableParser.PhysicalRow> tableRows = new ArrayList<>();
        for (int i = 0; i < physicalRows.size(); i++) {
            ScheduleTableParser.PhysicalRow row = physicalRows.get(i);
            if (headerRow != null && i == headerRowIndex) continue;
            if (row.centerY >= tableTopY && row.top < tableBottomY && !isFinancialOrFooterRow(row.getFullText())) {
                tableRows.add(row);
            }
        }

        List<RawRowMeeting> rawMeetings = new ArrayList<>();
        for (ScheduleTableParser.PhysicalRow row : tableRows) {
            String rowText = row.getFullText();
            String timeCell = extractColumnText(row, columns, HeaderType.TIME);
            String daysCell = extractColumnText(row, columns, HeaderType.DAYS);
            String subjectCell = extractColumnText(row, columns, HeaderType.SUBJECT);
            String sectionCell = extractColumnText(row, columns, HeaderType.SECTION);
            String roomCell = extractColumnText(row, columns, HeaderType.ROOM);

            boolean inTimeColumn = !timeCell.isEmpty();
            String[] timeRange = ScheduleTableParser.extractTimeRange(timeCell, true);
            if (timeRange == null) {
                timeRange = ScheduleTableParser.extractTimeRange(rowText, inTimeColumn);
            }

            if (timeRange != null) {
                RawRowMeeting meeting = new RawRowMeeting();
                meeting.row = row;
                meeting.startTimeFormatted = timeRange[0];
                meeting.endTimeFormatted = timeRange[1];
                meeting.parsedDays = ScheduleTableParser.extractDays(
                        !daysCell.isEmpty() ? daysCell : rowText);
                meeting.roomRaw = roomCell;
                meeting.sectionRaw = sectionCell;
                meeting.subjectRaw = subjectCell;
                rawMeetings.add(meeting);
            } else if (isSkippedLineCandidate(rowText)) {
                result.skippedLines.add(rowText.trim());
            }
        }

        List<CourseAnchor> anchors = buildCourseAnchors(tableRows, columns);
        for (RawRowMeeting meeting : rawMeetings) {
            if (meeting.parsedDays.isEmpty()) {
                meeting.parsedDays.add("");
                meeting.isDayMissing = true;
            }
        }

        for (RawRowMeeting rawMeeting : rawMeetings) {
            CourseAnchor bestAnchor = findActiveAnchor(rawMeeting.row.centerY, anchors);
            String subject = bestAnchor != null ? bestAnchor.subject : rawMeeting.subjectRaw;
            if (subject == null || subject.trim().isEmpty()) {
                subject = extractFallbackSubject(rawMeeting.row, columns);
            }

            String section = bestAnchor != null ? bestAnchor.section : rawMeeting.sectionRaw;
            String room = rawMeeting.roomRaw;

            for (String dayCode : rawMeeting.parsedDays) {
                ScheduleTableParser.ParsedMeeting meeting = new ScheduleTableParser.ParsedMeeting();
                meeting.subject = subject != null ? ScheduleTableParser.cleanSubjectText(subject) : "";
                meeting.section = section != null ? section.trim() : "";
                meeting.room = room.trim();
                meeting.day = dayCode != null ? dayCode.trim() : "";
                meeting.startTime = rawMeeting.startTimeFormatted;
                meeting.endTime = rawMeeting.endTimeFormatted;

                if (rawMeeting.isDayMissing || meeting.day.isEmpty()) {
                    meeting.flags.add(ScheduleTableParser.Flag.DAY_MISSING);
                }
                if (headerRow == null && !meeting.subject.isEmpty()) {
                    meeting.flags.add(ScheduleTableParser.Flag.SUBJECT_GUESSED);
                }
                if (meeting.subject.isEmpty()) {
                    meeting.flags.add(ScheduleTableParser.Flag.SUBJECT_MISSING);
                } else if (isSubjectUnusual(meeting.subject)) {
                    meeting.flags.add(ScheduleTableParser.Flag.SUBJECT_UNUSUAL);
                }

                ScheduleTableParser.checkTimeFlags(meeting);
                result.meetings.add(meeting);
            }
        }

        ScheduleTableParser.deduplicateMeetings(result.meetings);
        ScheduleTableParser.checkMeetingOverlaps(result.meetings);
        if (result.meetings.isEmpty() || (headerRow == null && rawMeetings.size() < 2)) {
            result.qualityGateFailed = true;
            result.qualityGateMessage = "We couldn't read this clearly. Try again in better light, or add your classes manually.";
        }
        return result;
    }

    private static List<ColumnInfo> detectHeaderColumns(ScheduleTableParser.PhysicalRow row) {
        List<ColumnInfo> columns = new ArrayList<>();
        ColumnInfo currentColumn = null;

        for (ScheduleTableParser.Word word : row.words) {
            String text = word.text.toLowerCase(Locale.US);
            HeaderType type = HeaderType.UNKNOWN;
            if (text.contains("course") || text.contains("subject")
                    || text.contains("desc") || text.contains("title")) {
                type = HeaderType.SUBJECT;
            } else if (text.contains("sec") || text.contains("class") || text.contains("code")) {
                type = HeaderType.SECTION;
            } else if (text.contains("day") || text.contains("days")) {
                type = HeaderType.DAYS;
            } else if (text.contains("time") || text.contains("sched")) {
                type = HeaderType.TIME;
            } else if (text.contains("room") || text.contains("rm") || text.contains("venue")) {
                type = HeaderType.ROOM;
            } else if (text.contains("facult") || text.contains("instruct") || text.contains("prof")) {
                type = HeaderType.INSTRUCTOR;
            }

            if (type != HeaderType.UNKNOWN) {
                if (currentColumn != null && currentColumn.type == type) {
                    currentColumn.maxX = Math.max(currentColumn.maxX, word.right);
                } else {
                    currentColumn = new ColumnInfo(type, word.left, word.right);
                    columns.add(currentColumn);
                }
            }
        }

        if (columns.isEmpty()) return columns;
        columns.sort(Comparator.comparingDouble(column -> column.minX));
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo current = columns.get(i);
            if (i == 0) {
                current.minX = 0;
            } else {
                columns.get(i - 1).maxX = current.minX;
            }
            if (i == columns.size() - 1) current.maxX = 10000;
        }
        return columns;
    }

    private static String extractColumnText(
            ScheduleTableParser.PhysicalRow row, List<ColumnInfo> columns, HeaderType type) {
        if (columns.isEmpty()) return "";

        ColumnInfo target = null;
        for (ColumnInfo column : columns) {
            if (column.type == type) {
                target = column;
                break;
            }
        }
        if (target == null) return "";

        StringBuilder text = new StringBuilder();
        for (ScheduleTableParser.Word word : row.words) {
            double centerX = word.getCenterX();
            if (centerX >= target.minX && centerX <= target.maxX) {
                if (text.length() > 0) text.append(" ");
                text.append(word.text);
            }
        }
        return text.toString();
    }

    private static boolean isFinancialOrFooterRow(String lineText) {
        if (lineText == null || lineText.trim().isEmpty()) return false;
        String lower = lineText.toLowerCase(Locale.US);
        return lower.contains("charges") || lower.contains("assessment") || lower.contains("payment")
                || lower.contains("balance") || lower.contains("tuition") || lower.contains("fee")
                || lower.contains("financial") || lower.contains("billing") || lower.contains("due")
                || lower.contains("account summary") || lower.contains("total units") || lower.contains("total");
    }

    private static boolean hasValidTimeRange(String text) {
        return ScheduleTableParser.extractTimeRange(text, false) != null;
    }

    private static List<CourseAnchor> buildCourseAnchors(
            List<ScheduleTableParser.PhysicalRow> tableRows, List<ColumnInfo> columns) {
        List<CourseAnchor> anchors = new ArrayList<>();
        for (int i = 0; i < tableRows.size(); i++) {
            ScheduleTableParser.PhysicalRow row = tableRows.get(i);
            String section = extractColumnText(row, columns, HeaderType.SECTION);
            String subjectCell = extractColumnText(row, columns, HeaderType.SUBJECT);

            boolean hasSection = !section.isEmpty() && section.length() >= 2;
            String rawSubject = subjectCell.isEmpty() ? row.getFullText() : subjectCell;
            String cleanedSubject = ScheduleTableParser.cleanSubjectText(rawSubject);
            boolean hasSubject = !cleanedSubject.isEmpty()
                    && cleanedSubject.length() > 1
                    && !isFinancialOrFooterRow(cleanedSubject)
                    && !cleanedSubject.startsWith("(") && !cleanedSubject.startsWith("-") && !cleanedSubject.startsWith("&");

            boolean isAnchorRow = hasSection || hasSubject;
            if (!isAnchorRow) continue;

            CourseAnchor anchor = new CourseAnchor();
            anchor.section = section;
            anchor.topY = row.top;
            anchor.bottomY = row.bottom;
            anchor.centerY = row.centerY;
            StringBuilder subject = new StringBuilder(cleanedSubject);

            int nextIndex = i + 1;
            while (nextIndex < tableRows.size()) {
                ScheduleTableParser.PhysicalRow nextRow = tableRows.get(nextIndex);
                String nextSection = extractColumnText(nextRow, columns, HeaderType.SECTION);
                String nextSubjectCell = extractColumnText(nextRow, columns, HeaderType.SUBJECT);

                boolean nextHasSection = !nextSection.isEmpty() && nextSection.length() >= 2;

                if (!nextHasSection) {
                    String continuation = nextSubjectCell.isEmpty() ? nextRow.getFullText() : nextSubjectCell;
                    if (isContinuationLine(continuation)) {
                        String cleanContinuation = ScheduleTableParser.cleanSubjectText(continuation);
                        if (!cleanContinuation.isEmpty()) {
                            subject.append(" ").append(cleanContinuation);
                        }
                        anchor.bottomY = nextRow.bottom;
                        anchor.centerY = (anchor.topY + anchor.bottomY) / 2.0;
                        nextIndex++;
                        continue;
                    } else if (nextSubjectCell.trim().isEmpty()) {
                        anchor.bottomY = nextRow.bottom;
                        anchor.centerY = (anchor.topY + anchor.bottomY) / 2.0;
                        nextIndex++;
                        continue;
                    }
                }
                break;
            }

            anchor.subject = ScheduleTableParser.cleanSubjectText(subject.toString());
            anchors.add(anchor);
        }
        anchors.sort(Comparator.comparingDouble(a -> a.topY));
        return anchors;
    }

    private static boolean isContinuationLine(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String trimmed = text.trim();
        if (trimmed.startsWith("(") || trimmed.startsWith("-") || trimmed.startsWith("&")) {
            return true;
        }
        if (isFinancialOrFooterRow(trimmed)) return false;
        return trimmed.matches(".*[a-zA-Z]{3,}.*");
    }

    private static CourseAnchor findActiveAnchor(double y, List<CourseAnchor> anchors) {
        if (anchors.isEmpty()) return null;
        CourseAnchor active = null;
        for (CourseAnchor anchor : anchors) {
            if (anchor.topY - 5 <= y) {
                active = anchor;
            } else {
                break;
            }
        }
        return active != null ? active : anchors.get(0);
    }

    private static String extractFallbackSubject(
            ScheduleTableParser.PhysicalRow row, List<ColumnInfo> columns) {
        String subject = extractColumnText(row, columns, HeaderType.SUBJECT);
        if (!subject.isEmpty()) return ScheduleTableParser.cleanSubjectText(subject);

        StringBuilder fallback = new StringBuilder();
        for (String part : row.getFullText().split("\\s+")) {
            if (part.matches("[a-zA-Z()&.\\-]{2,}")) {
                if (fallback.length() > 0) fallback.append(" ");
                fallback.append(part);
            }
        }
        return fallback.toString();
    }

    private static boolean isSubjectUnusual(String subject) {
        if (subject == null || subject.length() < 3) return true;
        return subject.matches("(?i).*(COM\\d+|RM\\d+|LIB\\d+|AMP\\d+).*");
    }

    private static boolean isSkippedLineCandidate(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.length() < 3) return false;
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.contains("TBA") || upper.contains("ARRANGED")
                || upper.contains("ONLINE") || upper.contains("ASYNC")) {
            return true;
        }
        return trimmed.matches(".*[a-zA-Z]{3,}.*");
    }
}
