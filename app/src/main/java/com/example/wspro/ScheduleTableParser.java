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

    static class GridDayColumn {
        String dayCode;
        double minX;
        double maxX;

        GridDayColumn(String dayCode, double minX, double maxX) {
            this.dayCode = dayCode;
            this.minX = minX;
            this.maxX = maxX;
        }
    }

    static class GridTimeColumn {
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

    static class PhysicalRow {
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
        List<GridDayColumn> gridDayCols = ScheduleGridTableParser.detectWeeklyHeader(physicalRows);
        if (gridDayCols != null && gridDayCols.size() >= 2) {
            return ScheduleGridTableParser.parseWeekly(physicalRows, gridDayCols);
        }

        // 3. Try Transposed-Grid Layout (Times as columns)
        List<GridTimeColumn> gridTimeCols =
                ScheduleGridTableParser.detectTransposedHeader(physicalRows);
        if (gridTimeCols != null && gridTimeCols.size() >= 2) {
            return ScheduleGridTableParser.parseTransposed(physicalRows, gridTimeCols);
        }

        // 4. Fallback to Standard List Table Parser
        return ScheduleStandardListParser.parse(physicalRows, words);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    static void deduplicateMeetings(List<ParsedMeeting> meetings) {
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

    static String[] extractTimeRange(String text, boolean inTimeColumn) {
        if (text == null || text.trim().isEmpty()) return null;

        String cleaned = text.replaceAll("[I|l]", "1").replaceAll("(?<=\\d)[oO]", "0");
        cleaned = cleaned.replaceAll("(?i)p\\.\\s*m\\.", "PM").replaceAll("(?i)a\\.\\s*m\\.", "AM");
        cleaned = cleaned.replaceAll("(?i)p\\s+m", "PM").replaceAll("(?i)a\\s+m", "AM");

        Pattern pattern = Pattern.compile(
                "(?<![A-Za-z0-9])(\\d{1,2}(?:[:.]\\d{2})?\\s*(?:AM|PM)?)\\s*[-–—to,]+\\s*(\\d{1,2}(?:[:.]\\d{2})?\\s*(?:AM|PM)?)(?![A-Za-z0-9])",
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

    static String formatTime(String raw) {
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
            if (!isPm && !isAm) {
                return null;
            }
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

        if (hour < 0 || hour > 23 || min < 0 || min > 59) return null;
        if ((isPm || isAm) && hour > 12) return null;

        if (isPm && hour < 12) hour += 12;
        if (isAm && hour == 12) hour = 0;

        String meridiem = (hour >= 12) ? "PM" : "AM";
        int hour12 = hour % 12;
        if (hour12 == 0) hour12 = 12;

        return String.format(Locale.US, "%d:%02d %s", hour12, min, meridiem);
    }

    static List<String> extractDays(String text) {
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

    static List<String> parseDayGroupToken(String token) {
        List<String> res = new ArrayList<>();
        if (token == null) return res;
        String t = token.trim();
        if (t.isEmpty()) return res;

        if (t.matches(".*\\d.*")) {
            return res;
        }

        t = t.replaceAll("5", "S")
             .replaceAll("\\$", "S")
             .replaceAll("(?i)VV", "W")
             .replaceAll("(?i)Tn", "Th");

        String upper = t.toUpperCase(Locale.US);
        if (upper.equals("AM") || upper.equals("PM") || upper.equals("DAY") || upper.equals("DAYS") ||
            upper.equals("TIME") || upper.equals("ROOM") || upper.equals("SECTION") || upper.equals("UNITS") ||
            upper.equals("COURSE") || upper.equals("SUBJECT") || upper.equals("TOTAL") || upper.equals("CLASS") ||
            upper.equals("CODE") || upper.equals("CREDIT") || upper.equals("TITLE") || upper.equals("DESC") ||
            upper.equals("DESCRIPTION") || upper.equals("SCHED") || upper.equals("VENUE")) {
            return res;
        }

        int i = 0;
        int len = t.length();
        while (i < len) {
            String remainingUpper = upper.substring(i);
            int matchedLen = 0;
            String dayCode = null;

            if (remainingUpper.startsWith("MONDAY") || remainingUpper.startsWith("LUNES")) {
                dayCode = "MON"; matchedLen = remainingUpper.startsWith("MONDAY") ? 6 : 5;
            } else if (remainingUpper.startsWith("TUESDAY") || remainingUpper.startsWith("MARTES")) {
                dayCode = "TUE"; matchedLen = remainingUpper.startsWith("TUESDAY") ? 7 : 6;
            } else if (remainingUpper.startsWith("WEDNESDAY") || remainingUpper.startsWith("MIYERKULES")) {
                dayCode = "WED"; matchedLen = remainingUpper.startsWith("WEDNESDAY") ? 9 : 10;
            } else if (remainingUpper.startsWith("THURSDAY") || remainingUpper.startsWith("HUWEBES")) {
                dayCode = "THU"; matchedLen = remainingUpper.startsWith("THURSDAY") ? 8 : 7;
            } else if (remainingUpper.startsWith("FRIDAY") || remainingUpper.startsWith("BIYERNES")) {
                dayCode = "FRI"; matchedLen = remainingUpper.startsWith("FRIDAY") ? 6 : 8;
            } else if (remainingUpper.startsWith("SATURDAY") || remainingUpper.startsWith("SABADO")) {
                dayCode = "SAT"; matchedLen = remainingUpper.startsWith("SATURDAY") ? 8 : 6;
            } else if (remainingUpper.startsWith("SUNDAY") || remainingUpper.startsWith("LINGGO")) {
                dayCode = "SUN"; matchedLen = 6;
            } else if (remainingUpper.startsWith("MON") || remainingUpper.startsWith("LUN")) {
                dayCode = "MON"; matchedLen = 3;
            } else if (remainingUpper.startsWith("TUE") || remainingUpper.startsWith("MAR")) {
                dayCode = "TUE"; matchedLen = 3;
            } else if (remainingUpper.startsWith("WED") || remainingUpper.startsWith("MIY")) {
                dayCode = "WED"; matchedLen = 3;
            } else if (remainingUpper.startsWith("THU") || remainingUpper.startsWith("HUW")) {
                dayCode = "THU"; matchedLen = 3;
            } else if (remainingUpper.startsWith("FRI") || remainingUpper.startsWith("BIY")) {
                dayCode = "FRI"; matchedLen = 3;
            } else if (remainingUpper.startsWith("SAT") || remainingUpper.startsWith("SAB")) {
                dayCode = "SAT"; matchedLen = 3;
            } else if (remainingUpper.startsWith("SUN") || remainingUpper.startsWith("LIN")) {
                dayCode = "SUN"; matchedLen = 3;
            } else if (remainingUpper.startsWith("TH") || remainingUpper.startsWith("HU")) {
                dayCode = "THU"; matchedLen = 2;
            } else if (remainingUpper.startsWith("TU")) {
                dayCode = "TUE"; matchedLen = 2;
            } else if (remainingUpper.startsWith("SA")) {
                dayCode = "SAT"; matchedLen = 2;
            } else if (remainingUpper.startsWith("SU")) {
                dayCode = "SUN"; matchedLen = 2;
            } else if (remainingUpper.startsWith("MO")) {
                dayCode = "MON"; matchedLen = 2;
            } else if (remainingUpper.startsWith("WE")) {
                dayCode = "WED"; matchedLen = 2;
            } else if (remainingUpper.startsWith("FR")) {
                dayCode = "FRI"; matchedLen = 2;
            } else if (remainingUpper.startsWith("M")) {
                dayCode = "MON"; matchedLen = 1;
            } else if (remainingUpper.startsWith("T")) {
                dayCode = "TUE"; matchedLen = 1;
            } else if (remainingUpper.startsWith("W")) {
                dayCode = "WED"; matchedLen = 1;
            } else if (remainingUpper.startsWith("R")) {
                dayCode = "THU"; matchedLen = 1;
            } else if (remainingUpper.startsWith("F")) {
                dayCode = "FRI"; matchedLen = 1;
            } else if (remainingUpper.startsWith("S")) {
                dayCode = "SAT"; matchedLen = 1;
            }

            if (dayCode == null || matchedLen == 0) {
                return new ArrayList<>();
            }

            res.add(dayCode);
            i += matchedLen;
        }

        return res;
    }

    static String cleanSubjectText(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("(?i)\\b(COM\\d+|RM\\d+|LIB\\d+|AMP\\d+)\\b", "");
        s = s.replaceAll("(?i)\\b(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY|LUNES|MARTES|MIYERKULES|HUWEBES|BIYERNES|SABADO|LINGGO|MON|TUE|WED|THU|FRI|SAT|SUN|LUN|MAR|MIY|HUW|BIY|SAB|LIN|TTh|MWF|TH|TU|SA|SU|MO|WE|FR|Th|Tu|Sa|Su|Hu|M|T|W|R|F|S)\\b", "");
        s = s.replaceAll("(?i)\\b\\d{1,2}[:.]\\d{2}\\s*(?:AM|PM)?\\b", "");
        s = s.replaceAll("(?i)\\b\\d{1,2}\\s*(?:AM|PM)\\b", "");
        s = s.replaceAll("\\b\\d\\.\\d{2}\\b", "").trim();
        s = s.replaceAll("^[|\\[\\]_:\\-.,\\s]+", "").replaceAll("[|\\[\\]_:\\-.,\\s]+$", "");
        return s.replaceAll("\\s+", " ").trim();
    }

    static void checkTimeFlags(ParsedMeeting pm) {
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

    static void checkMeetingOverlaps(List<ParsedMeeting> meetings) {
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
