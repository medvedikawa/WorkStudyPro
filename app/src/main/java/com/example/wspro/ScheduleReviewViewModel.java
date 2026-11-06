package com.example.wspro;

import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

public class ScheduleReviewViewModel extends ViewModel {
    final List<TimeSlotItem> schoolDraft = new ArrayList<>();
    final List<TimeSlotItem> workDraft = new ArrayList<>();
    final List<TimeSlotItem> schoolOriginals = new ArrayList<>();
    final List<TimeSlotItem> workOriginals = new ArrayList<>();
    final List<String> skippedLines = new ArrayList<>();
    boolean schoolLoaded;
    boolean workLoaded;
    boolean scanParsed;
    boolean skippedExpanded = true;
    int activeTab;
    int activeFilter;
}
