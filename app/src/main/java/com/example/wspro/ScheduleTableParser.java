package com.example.wspro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java Schedule Table Parser (no Android or ML Kit dependencies).
 * Rebuilds two-dimensional schedule tables geometrically from bounding boxes.
 * Supports standard list tables, weekly grid layouts (days as columns), and transposed grid layouts (times as columns).
 */
public class ScheduleTableParser {

    public static class Word {
        public final String text;
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;

        public Word(String text, int left, int top, int right, int bottom) {
            this.text = text != null ? text.trim() : "";
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int getWidth() { return Math.max(1, right - left); }
        public int getHeight() { return Math.max(1, bottom - top); }
        public double getCenterX() { return (left + right) / 2.0; }
        public double getCenterY() { return (top + bottom) / 2.0; }
    }

    public enum Flag {
        DAY_MISSING,
        DAY_AMBIGUOUS,
        SUBJECT_GUESSED,
        SUBJECT_UNUSUAL,
        SUBJECT_MISSING,
        TIME_AMBIGUOUS,
        END_BEFORE_START,
        DURATION_OVER_8H,
        OVERLAPS_ANOTHER,
        OVERNIGHT,
        UNITS_MISMATCH
    }

    public static class ParsedMeeting {
        public String subject = "";
        public String units = "";
        public String section = "";
        public String room = "";
        public String day = "";
        public String startTime = "";
        public String endTime = "";
        public Set<Flag> flags = EnumSet.noneOf(Flag.class);

        @Override
        public String toString() {
            return "ParsedMeeting{" +
                    "subject='" + subject + '\'' +
                    ", units='" + units + '\'' +
                    ", day='" + day + '\'' +
                    ", startTime='" + startTime + '\'' +
                    ", endTime='" + endTime + '\'' +
                    ", room='" + room + '\'' +
                    ", flags=" + flags +
                    '}';
        }
    }

    public static class ParseResult {
        public final List<ParsedMeeting> meetings = new ArrayList<>();
        public final List<String> skippedLines = new ArrayList<>();
        public Double totalUnitsPrinted = null;
        public Double totalUnitsCalculated = null;
        public boolean unitsMismatch = false;
        public boolean qualityGateFailed = false;
        public String qualityGateMessage = null;
    }

    private enum HeaderType {
        SUBJECT, UNITS, SECTION, DAYS, TIME, ROOM, INSTRUCTOR, UNKNOWN
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

    private static class GridDayColumn {
        String dayCode;
        double minX;
        double maxX;

        GridDayColumn(String dayCode, double minX, double maxX) {
            this.dayCode = dayCode;
            this.minX = minX;
            this.maxX = maxX;
        }
    }

    private static class GridTimeColumn {
        String startTimeFormatted;
        String endTimeFormatted;
        double minX;
        double maxX;

        GridTimeColumn(String startTimeFormatted, String endTimeFormatted, double minX, double maxX) {
            this.startTimeFormatted = startTimeFormatted;
            this.endTimeFormatted = endTimeFormatted;
            this.minX = minX;
            this.maxX = maxX;
        }
    }

    private static class PhysicalRow {
        List<Word> words = new ArrayList<>();
        double top;
        double bottom;
        double centerY;

        PhysicalRow(Word first) {
            words.add(first);
            top = first.top;
            bottom = first.bottom;
            centerY = first.getCenterY();
        }

        void addWord(Word w) {
            words.add(w);
            top = Math.min(top, w.top);
            bottom = Math.max(bottom, w.bottom);
            centerY = (top + bottom) / 2.0;
        }

        String getFullText() {
            StringBuilder sb = new StringBuilder();
            for (Word w : words) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(w.text);
            }
            return sb.toString();
        }
    }

    private static class CourseAnchor {
        String subject = "";
        String units = "";
        String section = "";
        double topY;
        double bottomY;
        double centerY;
    }

    private static class RawRowMeeting {
        PhysicalRow row;
        String startTimeFormatted;
        String endTimeFormatted;
        List<String> parsedDays = new ArrayList<>();
        String roomRaw = "";
        String unitsRaw = "";
        String sectionRaw = "";
        String subjectRaw = "";
        boolean isDayMissing = false;
    }

    public static ParseResult parse(List<Word> rawWords) {
        ParseResult result = new ParseResult();
        if (rawWords == null || rawWords.isEmpty()) {
            result.qualityGateFailed = true;
            result.qualityGateMessage = "We couldn't read this clearly. Try again in better light, or add your classes manually.";
            return result;
        }

        // 1. Clean & Sort words
        List<Word> words = new ArrayList<>();
        for (Word w : rawWords) {
            if (w != null && w.text != null && !w.text.trim().isEmpty()) {
                words.add(w);
            }
        }
        if (words.isEmpty()) {
            result.qualityGateFailed = true;
            result.qualityGateMessage = "We couldn't read this clearly. Try again in better light, or add your classes manually.";
            return result;
        }

        // Calculate median height
        List<Integer> heights = new ArrayList<>();
        for (Word w : words) heights.add(w.getHeight());
        Collections.sort(heights);
        int medianHeight = heights.get(heights.size() / 2);

        // Cluster into physical rows
        List<PhysicalRow> physicalRows = clusterIntoRows(words, medianHeight);

        // 2. Try Weekly-Grid Layout (Days as columns)
        List<GridDayColumn> gridDayCols = detectWeeklyGridHeader(physicalRows);
        if (gridDayCols != null && gridDayCols.size() >= 2) {
            return parseWeeklyGridTable(physicalRows, gridDayCols);
        }

        // 3. Try Transposed-Grid Layout (Times as columns)
        List<GridTimeColumn> gridTimeCols = detectTransposedGridHeader(physicalRows);
        if (gridTimeCols != null && gridTimeCols.size() >= 2) {
            return parseTransposedGridTable(physicalRows, words, gridTimeCols);
        }

        // 4. Fallback to Standard List Table Parser
        return parseStandardListTable(physicalRows, words);
    }

    // =========================================================================
    // WEEKLY GRID PARSING (Days as Columns, Times as Rows)
    // =========================================================================

    private static boolean isExplicitGridDayWord(String text) {
        if (text == null) return false;
        String upper = text.trim().toUpperCase(Locale.US);
        if (upper.equals("DAY") || upper.equals("DAYS") || upper.equals("SUBJECT") || upper.equals("COURSE") ||
            upper.equals("DESCRIPTION") || upper.equals("UNITS") || upper.equals("TIME") ||
            upper.equals("ROOM") || upper.equals("SECTION") || upper.equals("CODE") || upper.equals("CLASS")) {
            return false;
        }
        return upper.matches("MON|TUE|WED|THU|FRI|SAT|SUN|MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY|LUNES|MARTES|MIYERKULES|HUWEBES|BIYERNES|SABADO|LINGGO|LUN|MAR|MIY|HUW|BIY|SAB|LIN|TH|SA|SU");
    }

    private static List<GridDayColumn> detectWeeklyGridHeader(List<PhysicalRow> physicalRows) {
        for (PhysicalRow row : physicalRows) {
            List<GridDayColumn> rawCols = new ArrayList<>();
            double maxHeaderLeftColX = 0;

            for (Word w : row.words) {
                if (isExplicitGridDayWord(w.text)) {
                    List<String> parsed = parseDayGroupToken(w.text);
                    if (!parsed.isEmpty() && !parsed.get(0).isEmpty()) {
                        rawCols.add(new GridDayColumn(parsed.get(0), w.left, w.right));
                    }
                } else if (w.text.equalsIgnoreCase("time") || w.text.equalsIgnoreCase("sched")) {
                    maxHeaderLeftColX = Math.max(maxHeaderLeftColX, w.right);
                }
            }

            if (rawCols.size() >= 2) {
                Collections.sort(rawCols, Comparator.comparingDouble(c -> c.minX));

                for (int i = 0; i < rawCols.size(); i++) {
                    GridDayColumn curr = rawCols.get(i);
                    if (i == 0) {
                        curr.minX = Math.max(maxHeaderLeftColX, curr.minX - 30);
                    } else {
                        GridDayColumn prev = rawCols.get(i - 1);
                        double mid = (prev.maxX + curr.minX) / 2.0;
                        prev.maxX = mid;
                        curr.minX = mid;
                    }
                    if (i == rawCols.size() - 1) {
                        curr.maxX = 10000;
                    }
                }
                return rawCols;
            }
        }
        return null;
    }

    private static ParseResult parseWeeklyGridTable(List<PhysicalRow> physicalRows, List<GridDayColumn> gridDayCols) {
        ParseResult result = new ParseResult();

        for (PhysicalRow row : physicalRows) {
            String rowText = row.getFullText();
            String[] timeRange = extractTimeRange(rowText, false);
            if (timeRange == null) continue;

            String startTime = timeRange[0];
            String endTime = timeRange[1];

            for (GridDayColumn gCol : gridDayCols) {
                StringBuilder cellText = new StringBuilder();
                for (Word w : row.words) {
                    double cx = w.getCenterX();
                    if (cx >= gCol.minX && cx <= gCol.maxX) {
                        if (isExplicitGridDayWord(w.text)) continue;
                        if (w.text.equalsIgnoreCase("AM") || w.text.equalsIgnoreCase("PM") || w.text.equals("-") || w.text.equalsIgnoreCase("to")) continue;
                        if (w.text.matches(".*\\d{1,2}[:.]\\d{2}.*")) continue;

                        if (cellText.length() > 0) cellText.append(" ");
                        cellText.append(w.text);
                    }
                }

                String subject = cleanSubjectText(cellText.toString());
                if (subject.length() >= 2 && !subject.matches("(?i)^(time|room|sec|units|sched|day)$")) {
                    ParsedMeeting pm = new ParsedMeeting();
                    pm.subject = subject;
                    pm.day = gCol.dayCode;
                    pm.startTime = startTime;
                    pm.endTime = endTime;

                    checkTimeFlags(pm);
                    result.meetings.add(pm);
                }
            }
        }

        deduplicateMeetings(result.meetings);
        checkMeetingOverlaps(result.meetings);

        if (result.meetings.isEmpty()) {
            result.qualityGateFailed = true;
            result.qualityGateMessage = "We couldn't read this clearly. Try again in better light, or add your classes manually.";
        }

        return result;
    }

    // =========================================================================
    // TRANSPOSED GRID PARSING (Times as Columns, Days as Rows)
    // =========================================================================

    private static List<GridTimeColumn> detectTransposedGridHeader(List<PhysicalRow> physicalRows) {
        for (PhysicalRow row : physicalRows) {
            String fullRowText = row.getFullText();
            String[] timeRange = extractTimeRange(fullRowText, true);
            if (timeRange == null) continue;

            List<GridTimeColumn> rawCols = new ArrayList<>();
            Pattern p = Pattern.compile("(\\d{1,2}(?:[:.]\\d{2})?\\s*(?:AM|PM)?)\\s*[-–—to,]+\\s*(\\d{1,2}(?:[:.]\\d{2})?\\s*(?:AM|PM)?)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(fullRowText);

            while (m.find()) {
                String s = formatTime(m.group(1));
                String e = formatTime(m.group(2));
                if (s != null && e != null) {
                    int startChar = m.start();
                    int endChar = m.end();
                    double minX = Double.MAX_VALUE, maxX = 0;
                    int curPos = 0;
                    for (Word w : row.words) {
                        int wLen = w.text.length();
                        if (curPos + wLen >= startChar && curPos < endChar) {
                            minX = Math.min(minX, w.left);
                            maxX = Math.max(maxX, w.right);
                        }
                        curPos += wLen + 1;
                    }
                    if (minX == Double.MAX_VALUE) {
                        minX = 100;
                        maxX = 300;
                    }
                    rawCols.add(new GridTimeColumn(s, e, minX, maxX));
                }
            }

            if (rawCols.size() >= 2) {
                Collections.sort(rawCols, Comparator.comparingDouble(c -> c.minX));
                for (int i = 0; i < rawCols.size(); i++) {
                    GridTimeColumn curr = rawCols.get(i);
                    if (i == 0) {
                        curr.minX = 100;
                    } else {
                        GridTimeColumn prev = rawCols.get(i - 1);
                        double mid = (prev.maxX + curr.minX) / 2.0;
                        prev.maxX = mid;
                        curr.minX = mid;
                    }
                    if (i == rawCols.size() - 1) {
                        curr.maxX = 10000;
                    }
                }
                return rawCols;
            }
        }
        return null;
    }

    private static ParseResult parseTransposedGridTable(List<PhysicalRow> physicalRows, List<Word> words, List<GridTimeColumn> gridTimeCols) {
        ParseResult result = new ParseResult();
        boolean sheetHasSaOrSu = checkSheetHasSaOrSu(words);

        int headerIdx = -1;
        for (int i = 0; i < physicalRows.size(); i++) {
            if (extractTimeRange(physicalRows.get(i).getFullText(), true) != null) {
                headerIdx = i;
                break;
            }
        }

        for (int i = 0; i < physicalRows.size(); i++) {
            if (i == headerIdx) continue;
            PhysicalRow row = physicalRows.get(i);
            String rowText = row.getFullText();

            List<String> days = extractDays(rowText, sheetHasSaOrSu);
            if (days.isEmpty()) continue;

            String dayCode = days.get(0);

            for (GridTimeColumn gCol : gridTimeCols) {
                StringBuilder cellText = new StringBuilder();
                for (Word w : row.words) {
                    double cx = w.getCenterX();
                    if (cx >= gCol.minX && cx <= gCol.maxX) {
                        if (extractTimeRange(w.text, false) != null) continue;
                        if (isExplicitGridDayWord(w.text)) continue;

                        if (cellText.length() > 0) cellText.append(" ");
                        cellText.append(w.text);
                    }
                }

                String subject = cleanSubjectText(cellText.toString());
                if (subject.length() >= 2) {
                    ParsedMeeting pm = new ParsedMeeting();
                    pm.subject = subject;
                    pm.day = dayCode;
                    pm.startTime = gCol.startTimeFormatted;
                    pm.endTime = gCol.endTimeFormatted;

                    checkTimeFlags(pm);
                    result.meetings.add(pm);
                }
            }
        }

        deduplicateMeetings(result.meetings);
        checkMeetingOverlaps(result.meetings);

        if (result.meetings.isEmpty()) {
            result.qualityGateFailed = true;
            result.qualityGateMessage = "We couldn't read this clearly. Try again in better light, or add your classes manually.";
        }

        return result;
    }

    // =========================================================================
    // STANDARD LIST TABLE PARSING (Subject | Days | Time | Room)
    // =========================================================================

    private static ParseResult parseStandardListTable(List<PhysicalRow> physicalRows, List<Word> words) {
        ParseResult result = new ParseResult();

        // Identify Header Row & Columns
        PhysicalRow headerRow = null;
        int headerRowIndex = -1;
        List<ColumnInfo> columns = new ArrayList<>();

        for (int i = 0; i < physicalRows.size(); i++) {
            PhysicalRow row = physicalRows.get(i);
            List<ColumnInfo> detectedCols = detectHeaderColumns(row);
            if (detectedCols.size() >= 2) {
                headerRow = row;
                headerRowIndex = i;
                columns = detectedCols;
                break;
            }
        }

        // Determine Table Bounds (Section A)
        double tableTopY = 0;
        double tableBottomY = Double.MAX_VALUE;

        if (headerRow != null) {
            tableTopY = headerRow.bottom;
        } else {
            for (PhysicalRow row : physicalRows) {
                if (hasValidTimeRange(row.getFullText(), false)) {
                    tableTopY = row.top;
                    break;
                }
            }
        }

        // Search for footer keyword below tableTopY
        String[] footerKeywords = {"total", "total units", "financial", "charges", "assessment", "payment", "balance"};
        for (int i = 0; i < physicalRows.size(); i++) {
            PhysicalRow row = physicalRows.get(i);
            if (row.centerY < tableTopY) continue;

            String lineTextLower = row.getFullText().toLowerCase(Locale.US);
            for (String kw : footerKeywords) {
                if (lineTextLower.contains(kw)) {
                    if (row.top < tableBottomY) {
                        tableBottomY = row.top;
                    }
                    if (lineTextLower.contains("total units") || lineTextLower.contains("total")) {
                        Double printed = parsePrintedUnits(row.getFullText());
                        if (printed != null) {
                            result.totalUnitsPrinted = printed;
                        }
                    }
                    break;
                }
            }
        }

        if (tableBottomY == Double.MAX_VALUE) {
            double maxBottom = 0;
            for (Word w : words) {
                if (w.bottom > maxBottom) maxBottom = w.bottom;
            }
            tableBottomY = maxBottom + 10;
        }

        // Collect rows inside table region
        List<PhysicalRow> tableRows = new ArrayList<>();
        for (int i = 0; i < physicalRows.size(); i++) {
            PhysicalRow row = physicalRows.get(i);
            if (headerRow != null && i == headerRowIndex) continue;
            if (row.centerY >= tableTopY && row.centerY <= tableBottomY) {
                tableRows.add(row);
            }
        }

        boolean sheetHasSaOrSu = checkSheetHasSaOrSu(words);
        List<RawRowMeeting> rawMeetings = new ArrayList<>();

        for (int i = 0; i < tableRows.size(); i++) {
            PhysicalRow row = tableRows.get(i);
            String rowText = row.getFullText();

            String timeCell = extractColumnText(row, columns, HeaderType.TIME);
            String daysCell = extractColumnText(row, columns, HeaderType.DAYS);
            String subjCell = extractColumnText(row, columns, HeaderType.SUBJECT);
            String unitsCell = extractColumnText(row, columns, HeaderType.UNITS);
            String secCell = extractColumnText(row, columns, HeaderType.SECTION);
            String roomCell = extractColumnText(row, columns, HeaderType.ROOM);

            unitsCell = extractUnitsNumber(unitsCell);

            boolean inTimeColumn = !timeCell.isEmpty();

            // Strict time range extraction for current row only
            String[] timeRange = extractTimeRange(timeCell, true);
            if (timeRange == null) {
                timeRange = extractTimeRange(rowText, inTimeColumn);
            }

            if (timeRange != null) {
                RawRowMeeting rm = new RawRowMeeting();
                rm.row = row;
                rm.startTimeFormatted = timeRange[0];
                rm.endTimeFormatted = timeRange[1];

                String dayText = !daysCell.isEmpty() ? daysCell : rowText;
                rm.parsedDays = extractDays(dayText, sheetHasSaOrSu);

                rm.roomRaw = roomCell;
                rm.unitsRaw = unitsCell;
                rm.sectionRaw = secCell;
                rm.subjectRaw = subjCell;

                rawMeetings.add(rm);
            } else {
                if (isSkippedLineCandidate(rowText)) {
                    result.skippedLines.add(rowText.trim());
                }
            }
        }

        List<CourseAnchor> anchors = buildCourseAnchors(tableRows, columns);

        // Keep unreadable day as empty string with DAY_MISSING flag
        for (int i = 0; i < rawMeetings.size(); i++) {
            RawRowMeeting rm = rawMeetings.get(i);
            if (rm.parsedDays.isEmpty()) {
                rm.parsedDays.add("");
                rm.isDayMissing = true;
            }
        }

        double totalCalculatedUnits = 0.0;
        Set<String> processedAnchorSubjects = new HashSet<>();

        for (RawRowMeeting rm : rawMeetings) {
            CourseAnchor bestAnchor = findNearestAnchor(rm.row.centerY, anchors);

            String subject = bestAnchor != null ? bestAnchor.subject : rm.subjectRaw;
            if (subject == null || subject.trim().isEmpty()) {
                subject = extractFallbackSubject(rm.row, columns);
            }

            String units = bestAnchor != null ? bestAnchor.units : rm.unitsRaw;
            String section = bestAnchor != null ? bestAnchor.section : rm.sectionRaw;
            String room = !rm.roomRaw.isEmpty() ? rm.roomRaw : "";

            if (units != null && !units.trim().isEmpty() && bestAnchor != null) {
                if (!processedAnchorSubjects.contains(bestAnchor.subject)) {
                    processedAnchorSubjects.add(bestAnchor.subject);
                    try {
                        totalCalculatedUnits += Double.parseDouble(units.trim());
                    } catch (NumberFormatException ignored) {}
                }
            }

            for (String dayCode : rm.parsedDays) {
                ParsedMeeting pm = new ParsedMeeting();
                pm.subject = subject != null ? cleanSubjectText(subject) : "";
                pm.units = units != null ? units.trim() : "";
                pm.section = section != null ? section.trim() : "";
                pm.room = room != null ? room.trim() : "";
                pm.day = dayCode != null ? dayCode.trim() : "";
                pm.startTime = rm.startTimeFormatted;
                pm.endTime = rm.endTimeFormatted;

                if (rm.isDayMissing || pm.day.isEmpty()) {
                    pm.flags.add(Flag.DAY_MISSING);
                }
                if (headerRow == null && !pm.subject.isEmpty()) {
                    pm.flags.add(Flag.SUBJECT_GUESSED);
                }
                if (pm.subject.isEmpty()) {
                    pm.flags.add(Flag.SUBJECT_MISSING);
                } else if (isSubjectUnusual(pm.subject)) {
                    pm.flags.add(Flag.SUBJECT_UNUSUAL);
                }

                checkTimeFlags(pm);
                result.meetings.add(pm);
            }
        }

        deduplicateMeetings(result.meetings);

        result.totalUnitsCalculated = totalCalculatedUnits;
        if (result.totalUnitsPrinted != null) {
            if (Math.abs(result.totalUnitsPrinted - totalCalculatedUnits) > 0.01) {
                result.unitsMismatch = true;
                for (ParsedMeeting pm : result.meetings) {
                    pm.flags.add(Flag.UNITS_MISMATCH);
                }
            }
        }

        checkMeetingOverlaps(result.meetings);

        if (result.meetings.isEmpty() || (headerRow == null && rawMeetings.size() < 2)) {
            result.qualityGateFailed = true;
            result.qualityGateMessage = "We couldn't read this clearly. Try again in better light, or add your classes manually.";
        }

        return result;
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private static void deduplicateMeetings(List<ParsedMeeting> meetings) {
        if (meetings == null || meetings.size() <= 1) return;
        List<ParsedMeeting> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ParsedMeeting pm : meetings) {
            String key = (pm.subject != null ? pm.subject.trim().toLowerCase(Locale.US) : "") + "|" +
                         (pm.day != null ? pm.day.trim().toUpperCase(Locale.US) : "") + "|" +
                         (pm.startTime != null ? pm.startTime.trim() : "") + "|" +
                         (pm.endTime != null ? pm.endTime.trim() : "");

            if (!seen.contains(key)) {
                seen.add(key);
                unique.add(pm);
            }
        }
        meetings.clear();
        meetings.addAll(unique);
    }

    private static List<PhysicalRow> clusterIntoRows(List<Word> words, int medianHeight) {
        List<Word> sorted = new ArrayList<>(words);
        Collections.sort(sorted, Comparator.comparingDouble(Word::getCenterY));

        List<PhysicalRow> rows = new ArrayList<>();
        double threshold = Math.max(6.0, 0.35 * medianHeight);

        for (Word w : sorted) {
            PhysicalRow matchedRow = null;
            for (PhysicalRow r : rows) {
                if (Math.abs(r.centerY - w.getCenterY()) <= threshold) {
                    matchedRow = r;
                    break;
                }
            }
            if (matchedRow != null) {
                matchedRow.addWord(w);
            } else {
                rows.add(new PhysicalRow(w));
            }
        }

        Collections.sort(rows, Comparator.comparingDouble(r -> r.top));
        for (PhysicalRow r : rows) {
            Collections.sort(r.words, Comparator.comparingDouble(w -> w.left));
        }

        return rows;
    }

    private static List<ColumnInfo> detectHeaderColumns(PhysicalRow row) {
        List<ColumnInfo> rawCols = new ArrayList<>();
        ColumnInfo currentCol = null;

        for (Word w : row.words) {
            String txt = w.text.toLowerCase(Locale.US);
            HeaderType type = HeaderType.UNKNOWN;

            if (txt.contains("course") || txt.contains("subject") || txt.contains("desc") || txt.contains("title")) {
                type = HeaderType.SUBJECT;
            } else if (txt.contains("unit") || txt.contains("credit")) {
                type = HeaderType.UNITS;
            } else if (txt.contains("sec") || txt.contains("class") || txt.contains("code")) {
                type = HeaderType.SECTION;
            } else if (txt.contains("day") || txt.contains("days")) {
                type = HeaderType.DAYS;
            } else if (txt.contains("time") || txt.contains("sched")) {
                type = HeaderType.TIME;
            } else if (txt.contains("room") || txt.contains("rm") || txt.contains("venue")) {
                type = HeaderType.ROOM;
            } else if (txt.contains("facult") || txt.contains("instruct") || txt.contains("prof")) {
                type = HeaderType.INSTRUCTOR;
            }

            if (type != HeaderType.UNKNOWN) {
                if (currentCol != null && currentCol.type == type) {
                    currentCol.maxX = Math.max(currentCol.maxX, w.right);
                } else {
                    currentCol = new ColumnInfo(type, w.left, w.right);
                    rawCols.add(currentCol);
                }
            }
        }

        if (rawCols.isEmpty()) return rawCols;

        Collections.sort(rawCols, Comparator.comparingDouble(c -> c.minX));

        for (int i = 0; i < rawCols.size(); i++) {
            ColumnInfo curr = rawCols.get(i);
            if (i == 0) {
                curr.minX = 0;
            } else {
                ColumnInfo prev = rawCols.get(i - 1);
                prev.maxX = curr.minX;
            }
            if (i == rawCols.size() - 1) {
                curr.maxX = 10000;
            }
        }

        return rawCols;
    }

    private static String extractColumnText(PhysicalRow row, List<ColumnInfo> columns, HeaderType type) {
        if (columns.isEmpty()) return "";

        ColumnInfo target = null;
        for (ColumnInfo col : columns) {
            if (col.type == type) {
                target = col;
                break;
            }
        }
        if (target == null) return "";

        StringBuilder sb = new StringBuilder();
        for (Word w : row.words) {
            double cx = w.getCenterX();
            if (cx >= target.minX && cx <= target.maxX) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(w.text);
            }
        }
        return sb.toString();
    }

    private static String extractUnitsNumber(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        Matcher m = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b").matcher(text);
        if (m.find()) {
            return m.group(0);
        }
        return "";
    }

    private static boolean hasValidTimeRange(String text, boolean inTimeColumn) {
        return extractTimeRange(text, inTimeColumn) != null;
    }

    private static String[] extractTimeRange(String text, boolean inTimeColumn) {
        if (text == null || text.trim().isEmpty()) return null;

        String cleaned = text.replaceAll("[I|l]", "1").replaceAll("(?<=\\d)[oO]", "0");
        cleaned = cleaned.replaceAll("(?i)p\\.\\s*m\\.", "PM").replaceAll("(?i)a\\.\\s*m\\.", "AM");
        cleaned = cleaned.replaceAll("(?i)p\\s+m", "PM").replaceAll("(?i)a\\s+m", "AM");

        Pattern pattern = Pattern.compile(
                "(\\d{1,2}(?:[:.]\\d{2})?\\s*(?:AM|PM)?)\\s*[-–—to,]+\\s*(\\d{1,2}(?:[:.]\\d{2})?\\s*(?:AM|PM)?)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(cleaned);

        while (matcher.find()) {
            String rawStart = matcher.group(1) != null ? matcher.group(1).trim() : "";
            String rawEnd = matcher.group(2) != null ? matcher.group(2).trim() : "";

            boolean hasColonOrMeridiem = rawStart.contains(":") || rawStart.contains(".") ||
                    rawEnd.contains(":") || rawEnd.contains(".") ||
                    rawStart.toUpperCase(Locale.ROOT).contains("AM") || rawStart.toUpperCase(Locale.ROOT).contains("PM") ||
                    rawEnd.toUpperCase(Locale.ROOT).contains("AM") || rawEnd.toUpperCase(Locale.ROOT).contains("PM");

            if (!hasColonOrMeridiem && !inTimeColumn) {
                continue;
            }

            if (rawStart.matches("\\d{4}") && rawEnd.matches("\\d{4}")) {
                try {
                    int y1 = Integer.parseInt(rawStart);
                    int y2 = Integer.parseInt(rawEnd);
                    if (y1 >= 1900 && y1 <= 2100 && y2 >= 1900 && y2 <= 2100) {
                        continue;
                    }
                } catch (NumberFormatException ignored) {}
            }

            String upperEnd = rawEnd.toUpperCase(Locale.ROOT);
            String endMeridiem = upperEnd.contains("PM") ? "PM" : (upperEnd.contains("AM") ? "AM" : "");
            String upperStart = rawStart.toUpperCase(Locale.ROOT);

            if (!upperStart.contains("AM") && !upperStart.contains("PM") && !endMeridiem.isEmpty()) {
                rawStart = rawStart + endMeridiem;
            }

            String startFormatted = formatTime(rawStart);
            String endFormatted = formatTime(rawEnd);

            if (startFormatted != null && endFormatted != null) {
                if (!startFormatted.equals(endFormatted)) {
                    return new String[]{startFormatted, endFormatted};
                }
            }
        }
        return null;
    }

    private static String formatTime(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("\\.", ":").toUpperCase(Locale.US);

        boolean isPm = s.contains("PM");
        boolean isAm = s.contains("AM");
        s = s.replaceAll("[^0-9:]", "");

        if (s.isEmpty()) return null;

        int hour, min;
        if (s.contains(":")) {
            String[] parts = s.split(":");
            if (parts.length < 2) return null;
            try {
                hour = Integer.parseInt(parts[0]);
                min = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            try {
                int val = Integer.parseInt(s);
                if (val >= 100) {
                    hour = val / 100;
                    min = val % 100;
                } else {
                    hour = val;
                    min = 0;
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        if (hour > 23 || min > 59) return null;

        if (isPm && hour < 12) hour += 12;
        if (isAm && hour == 12) hour = 0;

        String meridiem = (hour >= 12) ? "PM" : "AM";
        int hour12 = hour % 12;
        if (hour12 == 0) hour12 = 12;

        return String.format(Locale.US, "%d:%02d %s", hour12, min, meridiem);
    }

    private static boolean checkSheetHasSaOrSu(List<Word> words) {
        for (Word w : words) {
            String t = w.text.trim().toUpperCase(Locale.ROOT);
            if (t.equals("SA") || t.equals("SU") || t.equals("SAT") || t.equals("SUN") || t.equals("SABADO") || t.equals("LINGGO")) {
                return true;
            }
        }
        return false;
    }

    private static List<String> extractDays(String text, boolean sheetHasSaOrSu) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;

        String cleaned = text.replaceAll("(?i)\\bT\\s+h\\b", "Th")
                             .replaceAll("(?i)\\bS\\s+a\\b", "Sa")
                             .replaceAll("(?i)\\bS\\s+u\\b", "Su");

        Pattern rangePattern = Pattern.compile("(?i)(MON|LUN|M)\\s*[-–—to]+\\s*(FRI|BIY|F)");
        if (rangePattern.matcher(cleaned).find()) {
            return Arrays.asList("MON", "TUE", "WED", "THU", "FRI");
        }

        String[] tokens = cleaned.split("[,\\s/\\-]+");
        for (String tok : tokens) {
            if (tok.isEmpty()) continue;
            List<String> parsed = parseDayGroupToken(tok);
            result.addAll(parsed);
        }

        return result;
    }

    private static List<String> parseDayGroupToken(String token) {
        List<String> res = new ArrayList<>();
        String t = token.trim();
        if (t.isEmpty()) return res;

        // Map common OCR day misreads
        t = t.replaceAll("5", "S")
             .replaceAll("\\$", "S")
             .replaceAll("(?i)VV", "W")
             .replaceAll("(?i)Tn", "Th");

        String upper = t.toUpperCase(Locale.US);
        if (upper.equals("AM") || upper.equals("PM") || upper.equals("DAY") || upper.equals("DAYS") ||
            upper.equals("TIME") || upper.equals("ROOM") || upper.equals("SECTION") || upper.equals("UNITS") ||
            upper.equals("COURSE") || upper.equals("SUBJECT") || upper.equals("TOTAL")) {
            return res;
        }

        if (upper.startsWith("MON") || upper.startsWith("LUN") || upper.equals("M")) { res.add("MON"); return res; }
        if (upper.startsWith("TUE") || upper.startsWith("MAR") || upper.equals("T")) { res.add("TUE"); return res; }
        if (upper.startsWith("WED") || upper.startsWith("MIY") || upper.equals("W")) { res.add("WED"); return res; }
        if (upper.startsWith("THU") || upper.startsWith("HUW") || upper.equals("TH") || upper.equals("R")) { res.add("THU"); return res; }
        if (upper.startsWith("FRI") || upper.startsWith("BIY") || upper.equals("F")) { res.add("FRI"); return res; }
        if (upper.startsWith("SAT") || upper.startsWith("SAB") || upper.equals("S") || upper.equals("SA")) { res.add("SAT"); return res; }
        if (upper.startsWith("SUN") || upper.startsWith("LIN") || upper.equals("SU")) { res.add("SUN"); return res; }

        int i = 0;
        while (i < t.length()) {
            char c = t.charAt(i);
            if (c == 'T' || c == 't') {
                if (i + 1 < t.length() && (t.charAt(i + 1) == 'h' || t.charAt(i + 1) == 'H')) {
                    res.add("THU");
                    i += 2;
                } else {
                    res.add("TUE");
                    i++;
                }
            } else if (c == 'R' || c == 'r') {
                res.add("THU");
                i++;
            } else if (c == 'S' || c == 's') {
                if (i + 1 < t.length() && (t.charAt(i + 1) == 'a' || t.charAt(i + 1) == 'A')) {
                    res.add("SAT");
                    i += 2;
                } else if (i + 1 < t.length() && (t.charAt(i + 1) == 'u' || t.charAt(i + 1) == 'U')) {
                    res.add("SUN");
                    i += 2;
                } else {
                    res.add("SAT");
                    i++;
                }
            } else if (c == 'M' || c == 'm') {
                res.add("MON");
                i++;
            } else if (c == 'W' || c == 'w') {
                res.add("WED");
                i++;
            } else if (c == 'F' || c == 'f') {
                res.add("FRI");
                i++;
            } else {
                i++;
            }
        }

        return res;
    }

    private static List<CourseAnchor> buildCourseAnchors(List<PhysicalRow> tableRows, List<ColumnInfo> columns) {
        List<CourseAnchor> anchors = new ArrayList<>();

        for (int i = 0; i < tableRows.size(); i++) {
            PhysicalRow row = tableRows.get(i);
            String unitsCell = extractColumnText(row, columns, HeaderType.UNITS);
            String secCell = extractColumnText(row, columns, HeaderType.SECTION);
            String subjCell = extractColumnText(row, columns, HeaderType.SUBJECT);

            unitsCell = extractUnitsNumber(unitsCell);

            boolean isAnchorRow = false;
            if (!unitsCell.isEmpty() && unitsCell.matches(".*\\d+.*")) isAnchorRow = true;
            if (!secCell.isEmpty() && secCell.length() >= 2) isAnchorRow = true;

            if (isAnchorRow) {
                CourseAnchor ca = new CourseAnchor();
                ca.units = unitsCell;
                ca.section = secCell;
                ca.topY = row.top;
                ca.bottomY = row.bottom;
                ca.centerY = row.centerY;

                StringBuilder subj = new StringBuilder(subjCell.isEmpty() ? row.getFullText() : subjCell);

                int j = i + 1;
                while (j < tableRows.size()) {
                    PhysicalRow nextRow = tableRows.get(j);
                    String nextUnits = extractColumnText(nextRow, columns, HeaderType.UNITS);
                    String nextSec = extractColumnText(nextRow, columns, HeaderType.SECTION);
                    String nextSubj = extractColumnText(nextRow, columns, HeaderType.SUBJECT);

                    nextUnits = extractUnitsNumber(nextUnits);

                    if ((nextUnits.isEmpty() || !nextUnits.matches(".*\\d+.*")) && nextSec.isEmpty()) {
                        String contText = nextSubj.isEmpty() ? nextRow.getFullText() : nextSubj;
                        if (isContinuationLine(contText)) {
                            subj.append(" ").append(contText);
                            ca.bottomY = nextRow.bottom;
                            ca.centerY = (ca.topY + ca.bottomY) / 2.0;
                            j++;
                            continue;
                        }
                    }
                    break;
                }

                ca.subject = cleanSubjectText(subj.toString());
                anchors.add(ca);
            }
        }

        return anchors;
    }

    private static boolean isContinuationLine(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String t = text.trim();
        if (t.startsWith("(") || t.startsWith("-") || t.startsWith("&")) return true;
        if (hasValidTimeRange(t, false)) return false;
        return t.matches(".*[a-zA-Z]{3,}.*");
    }

    private static CourseAnchor findNearestAnchor(double y, List<CourseAnchor> anchors) {
        if (anchors.isEmpty()) return null;
        CourseAnchor best = anchors.get(0);
        double minDiff = Math.abs(best.centerY - y);

        for (int i = 1; i < anchors.size(); i++) {
            CourseAnchor ca = anchors.get(i);
            double diff = Math.abs(ca.centerY - y);
            if (diff < minDiff) {
                minDiff = diff;
                best = ca;
            }
        }
        return best;
    }

    private static String extractFallbackSubject(PhysicalRow row, List<ColumnInfo> columns) {
        String subjCell = extractColumnText(row, columns, HeaderType.SUBJECT);
        if (!subjCell.isEmpty()) return cleanSubjectText(subjCell);

        String full = row.getFullText();
        String[] parts = full.split("\\s+");
        StringBuilder sb = new StringBuilder();

        for (String p : parts) {
            if (p.matches("[a-zA-Z()&.\\-]{2,}")) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(p);
            }
        }
        return sb.toString();
    }

    private static String cleanSubjectText(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("(?i)\\b(COM\\d+|RM\\d+|LIB\\d+|AMP\\d+)\\b", "");
        s = s.replaceAll("\\b\\d\\.\\d{2}\\b", "").trim();
        s = s.replaceAll("^[|\\[\\]_:\\-.,\\s]+", "").replaceAll("[|\\[\\]_:\\-.,\\s]+$", "");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static boolean isSubjectUnusual(String subject) {
        if (subject == null || subject.length() < 3) return true;
        if (subject.matches("(?i).*(COM\\d+|RM\\d+|LIB\\d+|AMP\\d+).*")) return true;
        return false;
    }

    private static boolean isSkippedLineCandidate(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.length() < 3) return false;
        String upper = t.toUpperCase(Locale.ROOT);
        if (upper.contains("TBA") || upper.contains("ARRANGED") || upper.contains("ONLINE") || upper.contains("ASYNC")) {
            return true;
        }
        return t.matches(".*[a-zA-Z]{3,}.*");
    }

    private static Double parsePrintedUnits(String text) {
        Pattern p = Pattern.compile("(?i)total\\s*(?:units)?\\s*:?\\s*(\\d+(?:\\.\\d+)?)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static void checkTimeFlags(ParsedMeeting pm) {
        int startMin = timeToMinutes(pm.startTime);
        int endMin = timeToMinutes(pm.endTime);

        if (startMin >= 0 && endMin >= 0) {
            if (endMin < startMin) {
                pm.flags.add(Flag.OVERNIGHT);
                pm.flags.add(Flag.END_BEFORE_START);
                int duration = (endMin + 1440) - startMin;
                if (duration > 480) pm.flags.add(Flag.DURATION_OVER_8H);
            } else {
                int duration = endMin - startMin;
                if (duration > 480) pm.flags.add(Flag.DURATION_OVER_8H);
            }
        }
    }

    private static int timeToMinutes(String timeFormatted) {
        if (timeFormatted == null || timeFormatted.isEmpty()) return -1;
        try {
            String[] parts = timeFormatted.split("[:\\s]");
            if (parts.length < 3) return -1;
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            String meridiem = parts[2].toUpperCase(Locale.ROOT);

            if (meridiem.equals("PM") && h < 12) h += 12;
            if (meridiem.equals("AM") && h == 12) h = 0;
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    private static void checkMeetingOverlaps(List<ParsedMeeting> meetings) {
        for (int i = 0; i < meetings.size(); i++) {
            ParsedMeeting m1 = meetings.get(i);
            int start1 = timeToMinutes(m1.startTime);
            int end1 = timeToMinutes(m1.endTime);
            if (start1 < 0 || end1 < 0) continue;
            if (end1 < start1) end1 += 1440;

            for (int j = i + 1; j < meetings.size(); j++) {
                ParsedMeeting m2 = meetings.get(j);
                if (m1.day.isEmpty() || !m1.day.equalsIgnoreCase(m2.day)) continue;

                int start2 = timeToMinutes(m2.startTime);
                int end2 = timeToMinutes(m2.endTime);
                if (start2 < 0 || end2 < 0) continue;
                if (end2 < start2) end2 += 1440;

                if (start1 < end2 && start2 < end1) {
                    m1.flags.add(Flag.OVERLAPS_ANOTHER);
                    m2.flags.add(Flag.OVERLAPS_ANOTHER);
                }
            }
        }
    }
}
