package com.example.wspro;

import java.util.List;

public class LoadCalculator {

    public enum LoadBand {
        LOW,
        MODERATE,
        HIGH
    }

    public static class LoadResult {
        public final double totalHours;
        public final double classHours;
        public final double workHours;
        public final int weeklyLimit;
        public final LoadBand band;

        public LoadResult(double totalHours, double classHours, double workHours, int weeklyLimit, LoadBand band) {
            this.totalHours = totalHours;
            this.classHours = classHours;
            this.workHours = workHours;
            this.weeklyLimit = weeklyLimit;
            this.band = band;
        }

        public int getRoundedTotal() {
            return (int) Math.round(totalHours);
        }

        public int getRoundedClass() {
            return (int) Math.round(classHours);
        }

        public int getRoundedWork() {
            return (int) Math.round(workHours);
        }

        public boolean isHigh() {
            return band == LoadBand.HIGH;
        }
    }

    public static LoadResult calculate(List<ScheduleItem> schedules, int weeklyLimit) {
        int limit = Math.max(1, weeklyLimit);
        double totalHours = 0;
        double classHours = 0;
        double workHours = 0;

        if (schedules != null) {
            for (ScheduleItem item : schedules) {
                int start = ConflictChecker.timeToMinutes(item.getStartTime());
                int end = ConflictChecker.timeToMinutes(item.getEndTime());
                if (end > start) {
                    double hours = (end - start) / 60.0;
                    totalHours += hours;
                    if (item.isWork()) {
                        workHours += hours;
                    } else {
                        classHours += hours;
                    }
                }
            }
        }

        LoadBand band;
        if (totalHours > limit) {
            band = LoadBand.HIGH;
        } else if (totalHours >= limit * 0.75) {
            band = LoadBand.MODERATE;
        } else {
            band = LoadBand.LOW;
        }

        return new LoadResult(totalHours, classHours, workHours, limit, band);
    }
}
