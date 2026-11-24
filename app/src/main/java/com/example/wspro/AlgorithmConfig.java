package com.example.wspro;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class AlgorithmConfig {

    private static final String PREFERENCES = "WorkStudyAlgorithmConfig";
    private static final String KEY_CONFIG = "global_config";

    public static final int DEADLINE = 0;
    public static final int TASK_PERFORMANCE = 1;
    public static final int GROUP_WORK = 2;
    public static final int LABORATORY = 3;
    public static final int ASSIGNMENT = 4;
    public static final int WORK_SHIFT_COLLISION = 5;
    public static final int WEIGHT_COUNT = 6;

    private static final String[] WEIGHT_KEYS = {
            "deadline_proximity", "task_performance", "group_work", "laboratory",
            "assignment", "work_shift_collision"
    };

    public final int moderateLoadPercent;
    public final int highLoadPercent;
    private final int[] weights;

    public AlgorithmConfig(int[] weights, int moderateLoadPercent, int highLoadPercent) {
        if (weights == null || weights.length != WEIGHT_COUNT) {
            throw new IllegalArgumentException("Exactly six algorithm weights are required.");
        }
        this.weights = weights.clone();
        this.moderateLoadPercent = moderateLoadPercent;
        this.highLoadPercent = highLoadPercent;
        validate();
    }

    public static AlgorithmConfig defaults() {
        return new AlgorithmConfig(new int[]{35, 20, 15, 10, 10, 10}, 60, 85);
    }

    public int getWeight(int index) {
        return weights[index];
    }

    public int[] getWeights() {
        return weights.clone();
    }

    public int getWeightTotal() {
        int total = 0;
        for (int weight : weights) total += weight;
        return total;
    }

    public void validate() {
        for (int weight : weights) {
            if (weight < 0 || weight > 100) {
                throw new IllegalArgumentException("Weights must be between 0 and 100.");
            }
        }
        if (getWeightTotal() != 100) {
            throw new IllegalArgumentException("Algorithm weights must total 100.");
        }
        if (moderateLoadPercent < 10 || moderateLoadPercent > 95
                || highLoadPercent < 20 || highLoadPercent > 100
                || moderateLoadPercent >= highLoadPercent) {
            throw new IllegalArgumentException("Load thresholds must be valid and increasing.");
        }
    }

    public JsonObject toJson() {
        JsonObject weightObject = new JsonObject();
        for (int i = 0; i < WEIGHT_COUNT; i++) {
            weightObject.addProperty(WEIGHT_KEYS[i], weights[i]);
        }
        JsonObject thresholdObject = new JsonObject();
        thresholdObject.addProperty("moderate_load", moderateLoadPercent);
        thresholdObject.addProperty("high_load", highLoadPercent);
        JsonObject result = new JsonObject();
        result.add("weights", weightObject);
        result.add("thresholds", thresholdObject);
        return result;
    }

    public static AlgorithmConfig fromJson(JsonObject json) {
        if (json == null || !json.has("weights") || !json.get("weights").isJsonObject()
                || !json.has("thresholds") || !json.get("thresholds").isJsonObject()) {
            throw new IllegalArgumentException("Algorithm configuration is incomplete.");
        }
        JsonObject weightObject = json.getAsJsonObject("weights");
        JsonObject thresholdObject = json.getAsJsonObject("thresholds");
        int[] weights = new int[WEIGHT_COUNT];
        for (int i = 0; i < WEIGHT_COUNT; i++) {
            weights[i] = requiredInt(weightObject, WEIGHT_KEYS[i]);
        }
        return new AlgorithmConfig(weights,
                requiredInt(thresholdObject, "moderate_load"),
                requiredInt(thresholdObject, "high_load"));
    }

    public static AlgorithmConfig load(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String stored = preferences.getString(KEY_CONFIG, null);
        if (stored == null) return defaults();
        try {
            return fromJson(com.google.gson.JsonParser.parseString(stored).getAsJsonObject());
        } catch (RuntimeException e) {
            android.util.Log.w("AlgorithmConfig", "Invalid cached algorithm configuration; using defaults.", e);
            return defaults();
        }
    }

    public static void save(Context context, AlgorithmConfig config) {
        if (context == null || config == null) {
            throw new IllegalArgumentException("Context and configuration are required.");
        }
        config.validate();
        context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(KEY_CONFIG, config.toJson().toString()).apply();
    }

    private static int requiredInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing numeric algorithm setting: " + key);
        }
        try {
            java.math.BigDecimal number = element.getAsBigDecimal();
            if (number.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("Algorithm setting must be a whole number: " + key);
            }
            return number.intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid numeric algorithm setting: " + key, e);
        }
    }
}
