package com.cobbletowers.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.MasteryTrackDefinition;
import com.cobbletowers.definition.SeasonTrackDefinition;
import com.cobbletowers.definition.SeasonTrackRegistry;
import com.cobbletowers.track.TrackConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MasteryTrackTest {

    @AfterEach
    void reset() {
        TrackConfig.set(new TrackConfig(false, Optional.empty(), List.of()));
        MasteryTracks.setLoaded(Map.of());
    }

    private static MasteryTrackDefinition file(String json) {
        return MasteryTrackDefinition.fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    /** The original P31 table, written out the way it used to be hard-coded. */
    private static MasteryPerks.Perks original(int level) {
        int clamped = Math.max(0, Math.min(30, level));
        int vendor = clamped >= 30 ? 10 : clamped >= 15 ? 6 : clamped >= 5 ? 3 : 0;
        int dollars = clamped >= 30 ? 15 : clamped >= 20 ? 10 : clamped >= 10 ? 5 : 0;
        int raid = clamped >= 25 ? 10 : 0;
        return new MasteryPerks.Perks(vendor, dollars, raid);
    }

    private static String originalRank(int level) {
        int clamped = Math.max(0, Math.min(30, level));
        if (clamped >= 30) return "Champion";
        if (clamped >= 25) return "Master";
        if (clamped >= 20) return "Diamond";
        if (clamped >= 15) return "Platinum";
        if (clamped >= 10) return "Gold";
        if (clamped >= 5) return "Silver";
        return clamped >= 1 ? "Bronze" : "Unranked";
    }

    @Test
    @DisplayName("the shipped default track reproduces the original ranks and perks at every level 0..30")
    void shippedDefaultMatchesTheOriginalTable() {
        for (int level = 0; level <= 30; level++) {
            assertEquals(original(level), MasteryPerks.at(level), "perks at " + level);
            assertEquals(originalRank(level), MasteryPerks.rankOf(level), "rank at " + level);
        }
        assertEquals(5, MasteryPerks.nextRankAt(1));
        assertEquals(-1, MasteryPerks.nextRankAt(30));
    }

    @Test
    @DisplayName("a level past 30 keeps the top rank and perks until a file says otherwise")
    void pastThirty() {
        assertEquals(original(30), MasteryPerks.at(45));
        assertEquals("Champion", MasteryPerks.rankOf(45));
        TrackConfig.set(new TrackConfig(false, Optional.of(file("{\"schema_version\":1,\"ranks\":[{\"level\":40,\"name\":\"Mythic\"}],"
                + "\"levels\":[{\"level\":40,\"perks\":{\"vendor_discount_percent\":12}}]}")), List.of()));
        assertEquals("Champion", MasteryPerks.rankOf(39));
        assertEquals("Mythic", MasteryPerks.rankOf(40));
        assertEquals(12, MasteryPerks.at(41).vendorDiscountPercent());
        assertEquals(10, MasteryPerks.at(39).vendorDiscountPercent());
        assertEquals(40, MasteryPerks.nextRankAt(30));
    }

    @Test
    @DisplayName("files merge: grants concatenate, perks and ranks are last-wins, a tower's file lands on top of the shared one")
    void merging() {
        var tide = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge");
        var other = ResourceLocation.fromNamespaceAndPath("cobbletowers", "rootvale");
        MasteryTracks.setLoaded(Map.of(
                ResourceLocation.fromNamespaceAndPath("a", "base"), file("{\"schema_version\":1,\"ranks\":[{\"level\":1,\"name\":\"Rookie\"}],"
                        + "\"levels\":[{\"level\":3,\"grants\":[{\"item\":\"minecraft:apple\",\"amount\":2}],\"cosmetics\":[\"x\"]}]}"),
                ResourceLocation.fromNamespaceAndPath("b", "addon"), file("{\"schema_version\":1,\"levels\":[{\"level\":3,"
                        + "\"grants\":[{\"item\":\"minecraft:bread\"}],\"cosmetics\":[\"x\",\"y\"]}]}"),
                ResourceLocation.fromNamespaceAndPath("c", "tide"), file("{\"schema_version\":1,\"tower\":\"cobbletowers:tideforge\","
                        + "\"ranks\":[{\"level\":1,\"name\":\"Tidecaller\"}]}")));
        MasteryTrack shared = MasteryTracks.forTower(other);
        assertEquals("Rookie", shared.rankOf(1));
        assertEquals(2, shared.node(3).grants().size());
        assertEquals(List.of("x", "y"), shared.node(3).cosmetics());
        assertEquals("Tidecaller", MasteryTracks.forTower(tide).rankOf(1));
        assertEquals("Rookie", MasteryTracks.forTower(other).rankOf(1), "another tower is unaffected");
    }

    @Test
    @DisplayName("the owner's config is merged after every datapack file")
    void configLast() {
        MasteryTracks.setLoaded(Map.of(ResourceLocation.fromNamespaceAndPath("a", "base"),
                file("{\"schema_version\":1,\"levels\":[{\"level\":5,\"perks\":{\"vendor_discount_percent\":3}}]}")));
        TrackConfig.set(TrackConfig.parse(JsonParser.parseString("{\"auto_claim\":true,\"mastery\":{\"levels\":[{\"level\":5,"
                + "\"perks\":{\"vendor_discount_percent\":9}}]}}").getAsJsonObject()));
        assertTrue(TrackConfig.current().autoClaim());
        assertEquals(9, MasteryPerks.at(5).vendorDiscountPercent());
    }

    @Test
    @DisplayName("a bad file is rejected with a clear reason")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> file("{\"schema_version\":2}"));
        assertThrows(IllegalArgumentException.class, () -> file("{\"schema_version\":1,\"levels\":[{\"level\":0}]}"));
        assertThrows(IllegalArgumentException.class,
                () -> file("{\"schema_version\":1,\"levels\":[{\"level\":2,\"perks\":{\"vendor_discount_percent\":120}}]}"));
    }

    @Test
    @DisplayName("add_steps append to a season step and extend the track past its end; the owner's go last")
    void seasonAddSteps() {
        JsonObject base = JsonParser.parseString("{\"schema_version\":1,\"step_cost\":10,\"steps\":["
                + "{\"grants\":[{\"item\":\"minecraft:apple\"}]},{\"grants\":[{\"item\":\"minecraft:bread\"}]}]}").getAsJsonObject();
        JsonObject addon = JsonParser.parseString("{\"schema_version\":1,\"add_steps\":[{\"step\":2,\"grants\":[{\"item\":\"minecraft:gold_ingot\"}]},"
                + "{\"step\":4,\"cosmetics\":[\"title_x\"]}]}").getAsJsonObject();
        var files = new java.util.TreeMap<ResourceLocation, SeasonTrackRegistry.File>();
        files.put(ResourceLocation.fromNamespaceAndPath("a", "base"),
                new SeasonTrackRegistry.File(SeasonTrackDefinition.fromJson(base), SeasonTrackDefinition.addSteps(base)));
        files.put(ResourceLocation.fromNamespaceAndPath("b", "addon"),
                new SeasonTrackRegistry.File(SeasonTrackDefinition.fromJson(addon), SeasonTrackDefinition.addSteps(addon)));
        var owner = List.of(new SeasonTrackDefinition.AddStep(1, List.of(new SeasonTrackDefinition.Grant("minecraft:carrot", 3)), List.of()));
        SeasonTrackDefinition merged = SeasonTrackRegistry.merged(files, owner).orElseThrow();
        assertEquals(4, merged.stepCount());
        assertEquals(10, merged.stepCost());
        assertEquals(2, merged.steps().get(0).grants().size());
        assertEquals(2, merged.steps().get(1).grants().size());
        assertEquals(List.of(), merged.steps().get(2).grants());
        assertEquals(List.of("title_x"), merged.steps().get(3).cosmetics());
    }
}
