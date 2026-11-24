package com.example.wspro;

import com.google.gson.JsonObject;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AlgorithmConfigTest {

    @Test
    public void defaultsHaveValidWeightTotalAndThresholds() {
        AlgorithmConfig config = AlgorithmConfig.defaults();

        assertEquals(100, config.getWeightTotal());
        assertEquals(60, config.moderateLoadPercent);
        assertEquals(85, config.highLoadPercent);
    }

    @Test
    public void jsonRoundTripsAllSettings() {
        AlgorithmConfig config = AlgorithmConfig.fromJson(AlgorithmConfig.defaults().toJson());

        assertEquals(35, config.getWeight(AlgorithmConfig.DEADLINE));
        assertEquals(15, config.getWeight(AlgorithmConfig.GROUP_WORK));
        assertEquals(10, config.getWeight(AlgorithmConfig.WORK_SHIFT_COLLISION));
        assertEquals(60, config.moderateLoadPercent);
        assertEquals(85, config.highLoadPercent);
    }

    @Test
    public void loadThresholdsChangeTheLoadBand() {
        java.util.List<ScheduleItem> schedules = java.util.List.of(
                new ScheduleItem("Class", "MON", "9:00 AM", "9:00 PM", false));
        AlgorithmConfig config = new AlgorithmConfig(
                AlgorithmConfig.defaults().getWeights(), 70, 90);

        LoadCalculator.LoadResult result = LoadCalculator.calculate(schedules, 20, config);

        assertEquals(LoadCalculator.LoadBand.LOW, result.band);
    }

    @Test
    public void rejectsFractionalWeightValues() {
        JsonObject json = AlgorithmConfig.defaults().toJson();
        json.getAsJsonObject("weights").addProperty("deadline_proximity", 35.5);

        assertInvalid(json);
    }

    @Test
    public void rejectsWeightsThatDoNotTotalOneHundred() {
        JsonObject json = AlgorithmConfig.defaults().toJson();
        json.getAsJsonObject("weights").addProperty("assignment", 9);

        assertInvalid(json);
    }

    @Test
    public void rejectsOutOfOrderLoadThresholds() {
        JsonObject json = AlgorithmConfig.defaults().toJson();
        json.getAsJsonObject("thresholds").addProperty("moderate_load", 90);

        assertInvalid(json);
    }

    private void assertInvalid(JsonObject json) {
        try {
            AlgorithmConfig.fromJson(json);
            fail("Expected invalid algorithm configuration to be rejected.");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }
}
