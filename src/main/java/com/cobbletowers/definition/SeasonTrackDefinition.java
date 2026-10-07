package com.cobbletowers.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * The free season track (P36b): a ladder of steps, each costing the same number of points and granting some items and some cosmetics.
 * Authored in {@code data/<namespace>/cobbletowers/season_tracks/*.json}; the same track serves every season.
 *
 * <p>A grant names an item id, and may write {@code {season}} where the season's number belongs, so one file says
 * {@code cobbletowers:season_trim_template_{season}} for every season. {@code cobbletowers:cobble_dollar} and
 * {@code cobbleraids:raid_points} are the reserved currency ids the reward delivery already credits; anything else must be a real item
 * (an absent optional mod's item is skipped with a log line, never fatal). Cosmetics are plain names the player keeps for good
 * ({@code banner_1}, {@code title_champion}); P36d displays them.
 */
public record SeasonTrackDefinition(int stepCost, List<Step> steps) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    /**
     * One grant. {@code components} is empty for a plain item; otherwise it is the item as the game's own item tag text
     * ({@code {id:"minecraft:blue_banner",count:1,components:{...}}}, P36d) so a grant can be a banner with patterns and a name.
     * {@code label} is what the delivery message calls it. {@code components} and {@code label} may use {@code {season}},
     * {@code {season_name}} and {@code {color}} (the spotlight region's dye).
     */
    public record Grant(String item, int amount, String components, String label) {
        public Grant {
            if (item == null || item.isBlank()) throw new IllegalArgumentException("a grant needs an item");
            if (amount < 1) throw new IllegalArgumentException("a grant's amount must be >= 1, got " + amount);
            components = components == null ? "" : components;
            label = label == null ? "" : label;
        }

        /** A plain grant of an item or a currency. */
        public Grant(String item, int amount) {
            this(item, amount, "", "");
        }
    }

    public record Step(int number, List<Grant> grants, List<String> cosmetics) {
        public Step {
            grants = List.copyOf(grants);
            cosmetics = List.copyOf(cosmetics);
        }
    }

    public SeasonTrackDefinition {
        if (stepCost < 1) throw new IllegalArgumentException("step_cost must be >= 1, got " + stepCost);
        steps = List.copyOf(steps);
    }

    public int stepCount() {
        return steps.size();
    }

    /** The most points the track can take: every step paid for. */
    public int totalPoints() {
        return stepCost * steps.size();
    }

    /** How many steps {@code points} has reached (never more than the track has). */
    public int stepsFor(int points) {
        return Math.min(steps.size(), Math.max(0, points) / stepCost);
    }

    /** The grants of a JSON object's {@code grants} array (item, amount, optional components and label). */
    public static List<Grant> grants(JsonObject json) {
        List<Grant> grants = new ArrayList<>();
        if (json.has("grants")) {
            for (JsonElement element : json.getAsJsonArray("grants")) {
                JsonObject grant = element.getAsJsonObject();
                grants.add(new Grant(TowerJson.requireString(grant, "item"), TowerJson.integer(grant, "amount", 1),
                        TowerJson.string(grant, "components", ""), TowerJson.string(grant, "label", "")));
            }
        }
        return grants;
    }

    /** Extra rewards for one step, from a datapack or the owner's config: they are appended to the step (P37). */
    public record AddStep(int step, List<Grant> grants, List<String> cosmetics) {
        public AddStep {
            if (step < 1) throw new IllegalArgumentException("add_steps: step must be >= 1, got " + step);
            grants = List.copyOf(grants);
            cosmetics = List.copyOf(cosmetics);
        }
    }

    /** The {@code add_steps} array of a JSON object (empty when there is none). */
    public static List<AddStep> addSteps(JsonObject root) {
        List<AddStep> added = new ArrayList<>();
        if (root.has("add_steps")) {
            for (JsonElement element : root.getAsJsonArray("add_steps")) {
                JsonObject json = element.getAsJsonObject();
                added.add(new AddStep(TowerJson.requireInt(json, "step"), grants(json), TowerJson.strings(json, "cosmetics")));
            }
        }
        return added;
    }

    /** This track with {@code added} appended to its steps; a step past the end extends the track with empty steps first. */
    public SeasonTrackDefinition withAdded(List<AddStep> added) {
        if (added.isEmpty()) return this;
        List<List<Grant>> grants = new ArrayList<>();
        List<List<String>> cosmetics = new ArrayList<>();
        for (Step step : steps) {
            grants.add(new ArrayList<>(step.grants()));
            cosmetics.add(new ArrayList<>(step.cosmetics()));
        }
        for (AddStep add : added) {
            while (grants.size() < add.step()) {
                grants.add(new ArrayList<>());
                cosmetics.add(new ArrayList<>());
            }
            grants.get(add.step() - 1).addAll(add.grants());
            cosmetics.get(add.step() - 1).addAll(add.cosmetics());
        }
        List<Step> merged = new ArrayList<>();
        for (int i = 0; i < grants.size(); i++) merged.add(new Step(i + 1, grants.get(i), cosmetics.get(i)));
        return new SeasonTrackDefinition(stepCost, merged);
    }

    public static SeasonTrackDefinition fromJson(JsonObject root) {
        int version = TowerJson.requireInt(root, "schema_version");
        if (version != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schema_version " + version + " is not supported; expected " + SUPPORTED_SCHEMA_VERSION);
        }
        List<Step> steps = new ArrayList<>();
        JsonArray array = root.has("steps") ? root.getAsJsonArray("steps") : new JsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonObject step = array.get(i).getAsJsonObject();
            steps.add(new Step(i + 1, grants(step), TowerJson.strings(step, "cosmetics")));
        }
        return new SeasonTrackDefinition(TowerJson.integer(root, "step_cost", 75), steps);
    }
}
