package com.cobbletowers.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ArmorSetTest {

    private static final List<String> SET_IDS = List.of("challenger", "tideforge", "rootvale", "duskvale",
            "recruit", "tidewalker", "sprout", "duskwanderer", "paragon", "leviathan", "heartwood", "nightfall");
    /** Set id to its tower and tier (P38): tier I and II drop from the boss tables, tier III from the F10 champion's bonus pool. */
    private static final Map<String, String[]> TOWER_AND_TIER = Map.ofEntries(
            Map.entry("challenger", new String[] {"neutral", "2"}), Map.entry("tideforge", new String[] {"tideforge", "2"}),
            Map.entry("rootvale", new String[] {"rootvale", "2"}), Map.entry("duskvale", new String[] {"duskvale", "2"}),
            Map.entry("recruit", new String[] {"neutral", "1"}), Map.entry("tidewalker", new String[] {"tideforge", "1"}),
            Map.entry("sprout", new String[] {"rootvale", "1"}), Map.entry("duskwanderer", new String[] {"duskvale", "1"}),
            Map.entry("paragon", new String[] {"neutral", "3"}), Map.entry("leviathan", new String[] {"tideforge", "3"}),
            Map.entry("heartwood", new String[] {"rootvale", "3"}), Map.entry("nightfall", new String[] {"duskvale", "3"}));
    private static final List<String> SLOT_NAMES = List.of("helmet", "chestplate", "leggings", "boots");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobbletowers", path);
    }

    private static JsonObject read(String resource) throws IOException {
        try (InputStream in = ArmorSetTest.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("missing resource " + resource);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static ArmorSetDefinition bundled(String set) throws IOException {
        return ArmorSetDefinition.fromJson(id(set), read("/data/cobbletowers/cobbletowers/armor_sets/" + set + ".json"));
    }

    private static Map<String, ResourceLocation> wearing(String set, String... slots) {
        Map<String, ResourceLocation> worn = new LinkedHashMap<>();
        for (String slot : slots) {
            String item = switch (slot) {
                case "head" -> "helmet";
                case "chest" -> "chestplate";
                case "legs" -> "leggings";
                default -> "boots";
            };
            worn.put(slot, id(set + "_" + item));
        }
        return worn;
    }

    private static JsonObject setJson(String bonusesJson) {
        return JsonParser.parseString("{\"schema_version\":1,\"display_name\":\"T\",\"pieces\":{\"head\":\"cobbletowers:t_helmet\","
                + "\"chest\":\"cobbletowers:t_chestplate\"},\"bonuses\":" + bonusesJson + "}").getAsJsonObject();
    }

    // ---- the shipped sets ----------------------------------------------------------------------------------------

    @Test
    void everyBundledSetParsesAndItsPiecesFollowTheNamingConvention() throws IOException {
        for (String set : SET_IDS) {
            ArmorSetDefinition definition = bundled(set);
            assertEquals(4, definition.pieces().size(), set);
            assertEquals(id(set + "_helmet"), definition.pieces().get("head"));
            assertEquals(id(set + "_boots"), definition.pieces().get("feet"));
            assertTrue(definition.bonuses().stream().anyMatch(b -> b.pieces() == 4), set + " has a full-set bonus");
            assertTrue(definition.bonuses().stream().anyMatch(b -> b.pieces() == 2), set + " has a two-piece bonus");
        }
    }

    @Test
    void everySetCoversAllFourBonusKinds() throws IOException {
        for (String set : SET_IDS) {
            List<SetBonus> bonuses = bundled(set).bonuses();
            assertTrue(bonuses.stream().anyMatch(b -> b instanceof SetBonus.PlayerAttribute), set + " player");
            assertTrue(bonuses.stream().anyMatch(b -> b instanceof SetBonus.CobblemonModifier), set + " cobblemon");
            assertTrue(bonuses.stream().anyMatch(b -> b instanceof SetBonus.BattleEffects), set + " battle");
            assertTrue(bonuses.stream().anyMatch(b -> b instanceof SetBonus.TowerModifier), set + " tower");
        }
    }

    @Test
    void everyPieceHasItsModelIconLayersAndLangKey() throws IOException {
        JsonObject lang = read("/assets/cobbletowers/lang/en_us.json");
        for (String set : SET_IDS) {
            for (String number : List.of("1", "2")) {
                assertTrue(exists("/assets/cobbletowers/textures/models/armor/" + set + "_layer_" + number + ".png"), set + " layer " + number);
            }
            for (String slot : SLOT_NAMES) {
                String name = set + "_" + slot;
                assertTrue(exists("/assets/cobbletowers/textures/item/" + name + ".png"), name + " icon");
                JsonObject model = read("/assets/cobbletowers/models/item/" + name + ".json");
                assertEquals("cobbletowers:item/" + name,
                        model.getAsJsonObject("textures").get("layer0").getAsString(), name + " model points at its icon");
                assertTrue(lang.has("item.cobbletowers." + name), name + " lang key");
            }
        }
    }

    private static boolean exists(String resource) {
        return ArmorSetTest.class.getResource(resource) != null;
    }

    @Test
    void theBundledRewardTablesDropEverySetWhereItsTierSaysAndNowhereElse() throws IOException {
        for (String set : SET_IDS) {
            String tower = TOWER_AND_TIER.get(set)[0];
            int tier = Integer.parseInt(TOWER_AND_TIER.get(set)[1]);
            JsonObject root = read("/data/cobbletowers/cobbletowers/reward_tables/" + tower + ".json");
            JsonArray boss = root.getAsJsonObject("tiers").getAsJsonArray("boss_defeated");
            JsonArray cleared = root.getAsJsonObject("tiers").getAsJsonArray("floor_cleared");
            JsonArray champion = root.getAsJsonObject("milestones").getAsJsonObject("champion").getAsJsonArray("bonus_pool");
            JsonArray guaranteed = root.getAsJsonObject("milestones").getAsJsonObject("champion").getAsJsonArray("guaranteed");
            for (String slot : SLOT_NAMES) {
                String item = "cobbletowers:" + set + "_" + slot;
                assertEquals(tier != 3, has(boss, item), tower + " boss_defeated and " + item);
                assertEquals(tier == 1 || set.equals(tower) || set.equals("challenger"), has(cleared, item), tower + " floor_cleared and " + item);
                assertEquals(tier == 3, has(champion, item), tower + " F10 champion bonus pool and " + item);
                assertTrue(!has(guaranteed, item), item + " is never a guaranteed drop");
            }
        }
    }

    private static boolean has(JsonArray entries, String item) {
        return entries.asList().stream().anyMatch(e -> e.getAsJsonObject().get("item").getAsString().equals(item));
    }

    // ---- resolving what is worn ----------------------------------------------------------------------------------

    @Test
    void nothingWornSwitchesNothingOn() throws IOException {
        assertTrue(SetBonusResolver.resolve(Map.of(), List.of(bundled("tideforge"))).isNone());
    }

    @Test
    void twoPiecesGiveTheTwoPieceBonusesOnly() throws IOException {
        ActiveBonuses active = SetBonusResolver.resolve(wearing("tideforge", "head", "feet"), List.of(bundled("tideforge")));
        assertEquals(2, active.attributes().size());
        assertEquals(10, active.catchRatePercent());
        assertTrue(active.battleEffects().isEmpty(), "no full-set battle effects yet");
        assertEquals(0, active.raidPointsPercent());
    }

    @Test
    void aFullSetAddsTheFullSetBonusesOnTopOfTheTwoPieceOnes() throws IOException {
        ActiveBonuses active = SetBonusResolver.resolve(wearing("tideforge", "head", "chest", "legs", "feet"), List.of(bundled("tideforge")));
        assertEquals(2, active.attributes().size());
        assertEquals(10, active.catchRatePercent());
        assertEquals(2, active.battleEffects().size());
        assertEquals(10, active.raidPointsPercent());
    }

    @Test
    void onePieceIsNotEnough() throws IOException {
        assertTrue(SetBonusResolver.resolve(wearing("rootvale", "chest"), List.of(bundled("rootvale"))).isNone());
    }

    @Test
    void aMixedOutfitGetsBothTwoPieceBonusesAndNeitherFullSet() throws IOException {
        Map<String, ResourceLocation> worn = new LinkedHashMap<>();
        worn.putAll(wearing("tideforge", "head", "chest"));
        worn.putAll(wearing("rootvale", "legs", "feet"));
        ActiveBonuses active = SetBonusResolver.resolve(worn, List.of(bundled("tideforge"), bundled("rootvale")));
        assertEquals(10, active.catchRatePercent(), "tideforge two-piece");
        assertEquals(10, active.xpPercent(), "rootvale two-piece");
        assertTrue(active.battleEffects().isEmpty());
        assertEquals(0, active.vendorDiscountPercent());
    }

    @Test
    void aPieceInTheWrongSlotCountsForNothing() throws IOException {
        Map<String, ResourceLocation> worn = new LinkedHashMap<>();
        worn.put("head", id("tideforge_boots"));
        worn.put("chest", id("tideforge_chestplate"));
        assertTrue(SetBonusResolver.resolve(worn, List.of(bundled("tideforge"))).isNone(), "one real piece only");
    }

    @Test
    void attributeModifierKeysAreStableAndUniqueAcrossBonusesAndSets() throws IOException {
        Map<String, ResourceLocation> worn = new LinkedHashMap<>();
        worn.putAll(wearing("tideforge", "head", "chest"));
        worn.putAll(wearing("challenger", "legs", "feet"));
        ActiveBonuses first = SetBonusResolver.resolve(worn, List.of(bundled("tideforge"), bundled("challenger")));
        ActiveBonuses second = SetBonusResolver.resolve(worn, List.of(bundled("tideforge"), bundled("challenger")));
        assertEquals(first, second);
        long distinct = first.attributes().stream().map(ActiveBonuses.Attribute::key).distinct().count();
        assertEquals(first.attributes().size(), distinct);
    }

    @Test
    void stackedPercentsAreCapped() {
        ArmorSetDefinition greedy = ArmorSetDefinition.fromJson(id("t"), setJson(
                "[{\"pieces\":1,\"kind\":\"cobblemon\",\"modifier\":\"xp_percent\",\"percent\":90},"
                        + "{\"pieces\":1,\"kind\":\"cobblemon\",\"modifier\":\"xp_percent\",\"percent\":90},"
                        + "{\"pieces\":1,\"kind\":\"tower\",\"modifier\":\"vendor_discount_percent\",\"percent\":40},"
                        + "{\"pieces\":1,\"kind\":\"tower\",\"modifier\":\"vendor_discount_percent\",\"percent\":40}]"));
        ActiveBonuses active = SetBonusResolver.resolve(Map.of("head", id("t_helmet")), List.of(greedy));
        assertEquals(100, active.xpPercent());
        assertEquals(50, active.vendorDiscountPercent());
    }

    // ---- parsing -------------------------------------------------------------------------------------------------

    @Test
    void badSetsAreRejectedWithAReason() {
        String[] bad = {
            "[{\"pieces\":0,\"kind\":\"cobblemon\",\"modifier\":\"xp_percent\",\"percent\":5}]",
            "[{\"pieces\":5,\"kind\":\"cobblemon\",\"modifier\":\"xp_percent\",\"percent\":5}]",
            "[{\"pieces\":2,\"kind\":\"wizardry\"}]",
            "[{\"pieces\":2,\"kind\":\"cobblemon\",\"modifier\":\"free_masterballs\",\"percent\":5}]",
            "[{\"pieces\":2,\"kind\":\"cobblemon\",\"modifier\":\"xp_percent\",\"percent\":0}]",
            "[{\"pieces\":2,\"kind\":\"cobblemon\",\"modifier\":\"xp_percent\",\"percent\":101}]",
            "[{\"pieces\":2,\"kind\":\"tower\",\"modifier\":\"vendor_discount_percent\",\"percent\":51}]",
            "[{\"pieces\":2,\"kind\":\"player\",\"attribute\":\"minecraft:generic.luck\",\"amount\":1e9}]",
            "[{\"pieces\":2,\"kind\":\"player\",\"attribute\":\"minecraft:generic.luck\",\"operation\":\"multiply\",\"amount\":1}]",
            "[{\"pieces\":2,\"kind\":\"player\",\"attribute\":\"minecraft:generic.luck\"}]",
            "[{\"pieces\":4,\"kind\":\"battle\",\"effects\":[]}]",
            "[{\"pieces\":4,\"kind\":\"battle\",\"effects\":[{\"op\":\"eval\",\"code\":\"1\"}]}]",
            "[{\"pieces\":4,\"kind\":\"battle\",\"effects\":[{\"op\":\"weather\",\"id\":\"primordialsea\"}]}]",
            "[{\"pieces\":4,\"kind\":\"battle\"}]",
            "[5]",
            "\"nope\"",
        };
        for (String bonuses : bad) {
            assertThrows(RuntimeException.class, () -> ArmorSetDefinition.fromJson(id("t"), setJson(bonuses)), bonuses);
        }
    }

    @Test
    void badStructureIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ArmorSetDefinition.fromJson(id("t"),
                JsonParser.parseString("{\"schema_version\":2,\"display_name\":\"T\",\"pieces\":{\"head\":\"a:b\"}}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ArmorSetDefinition.fromJson(id("t"),
                JsonParser.parseString("{\"schema_version\":1,\"display_name\":\"T\",\"pieces\":{\"tail\":\"a:b\"}}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ArmorSetDefinition.fromJson(id("t"),
                JsonParser.parseString("{\"schema_version\":1,\"display_name\":\"T\",\"pieces\":{}}").getAsJsonObject()));
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i < 17; i++) many.append(i > 0 ? "," : "").append("{\"pieces\":1,\"kind\":\"cobblemon\",\"modifier\":\"xp_percent\",\"percent\":1}");
        assertThrows(IllegalArgumentException.class, () -> ArmorSetDefinition.fromJson(id("t"), setJson(many.append("]").toString())));
    }

    // ---- the tower arithmetic ------------------------------------------------------------------------------------

    @Test
    void scalingAndDiscountsRoundSensiblyAndNeverBreakTheirFloor() {
        assertEquals(11, ArmorBonusEffects.scale(10, 10));
        assertEquals(10, ArmorBonusEffects.scale(10, 0));
        assertEquals(0, ArmorBonusEffects.scale(0, 50));
        assertEquals(2, ArmorBonusEffects.scale(1, 50), "rounds to nearest");
        assertEquals(Integer.MAX_VALUE, ArmorBonusEffects.scale(Integer.MAX_VALUE, 100), "no overflow");
        assertEquals(90, ArmorBonusEffects.discountedPrice(100, 10));
        assertEquals(100, ArmorBonusEffects.discountedPrice(100, 0));
        assertEquals(1, ArmorBonusEffects.discountedPrice(1, 50), "never free");
        assertEquals(0, ArmorBonusEffects.discountedPrice(0, 50));
    }
}
