package com.cobbletowers.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.RewardTableDefinition;
import com.cobbletowers.modifier.ModifierEffects;
import com.cobbletowers.persistence.LedgerEntry;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Turning a slice of a ledger into what it is worth: growth, reward-percent, and what is skipped. */
class RewardValuationTest {

    private static final ResourceLocation TOWER = ResourceLocation.fromNamespaceAndPath("cobbletowers", "neutral");
    private static final long SEED = 555L;

    private static RewardTableDefinition table(String json) {
        return RewardTableDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("cobbletowers", "rewards"),
                JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    @DisplayName("an empty ledger prices to nothing")
    void emptyLedger() {
        RewardTableDefinition table = table("""
                {"schema_version": 1, "display_name": "x",
                 "tiers": {"opponent_defeated": [{"item": "minecraft:stone"}]}}""");

        assertEquals(List.of(), RewardValuation.value(SEED, List.of(), table, ModifierEffects.NONE));
    }

    @Test
    @DisplayName("a forfeited ledger entry is never priced")
    void forfeitedIsSkipped() {
        RewardTableDefinition table = table("""
                {"schema_version": 1, "display_name": "x",
                 "tiers": {"opponent_defeated": [{"item": "minecraft:stone"}]}}""");
        LedgerEntry forfeited = LedgerEntry.forfeited(3, TOWER, 1L);

        assertEquals(List.of(), RewardValuation.value(SEED, List.of(forfeited), table, ModifierEffects.NONE));
    }

    @Test
    @DisplayName("a tier the table rolls nothing for grants nothing")
    void emptyTierGrantsNothing() {
        RewardTableDefinition table = table("""
                {"schema_version": 1, "display_name": "x",
                 "tiers": {"boss_defeated": [{"item": "minecraft:diamond"}]}}""");
        LedgerEntry opponent = LedgerEntry.opponentDefeated(1, TOWER, UUID.randomUUID(), 1L);

        assertEquals(List.of(), RewardValuation.value(SEED, List.of(opponent), table, ModifierEffects.NONE));
    }

    @Test
    @DisplayName("growth scales a later floor's grant above an earlier one's")
    void growthScalesWithFloor() {
        RewardTableDefinition table = table("""
                {"schema_version": 1, "display_name": "x", "growth_percent_per_floor": 50,
                 "tiers": {"floor_cleared": [{"item": "minecraft:emerald", "min_amount": 10, "max_amount": 10}]}}""");

        int floorOne = RewardValuation.value(SEED, List.of(LedgerEntry.floorCleared(1, TOWER, 1L)),
                table, ModifierEffects.NONE).get(0).amount();
        int floorTen = RewardValuation.value(SEED, List.of(LedgerEntry.floorCleared(10, TOWER, 1L)),
                table, ModifierEffects.NONE).get(0).amount();

        assertEquals(15, floorOne, "10 + 10*50%*1/100");
        assertEquals(60, floorTen, "10 + 10*50%*10/100");
        assertTrue(floorTen > floorOne);
    }

    @Test
    @DisplayName("no growth configured leaves the roll exactly as rolled")
    void zeroGrowthIsUnchanged() {
        RewardTableDefinition table = table("""
                {"schema_version": 1, "display_name": "x",
                 "tiers": {"floor_cleared": [{"item": "minecraft:emerald", "min_amount": 7, "max_amount": 7}]}}""");

        int amount = RewardValuation.value(SEED, List.of(LedgerEntry.floorCleared(10, TOWER, 1L)),
                table, ModifierEffects.NONE).get(0).amount();

        assertEquals(7, amount);
    }

    @Test
    @DisplayName("a run's reward-percent modifier scales the finished roll once, last")
    void rewardPercentAppliedLast() {
        RewardTableDefinition table = table("""
                {"schema_version": 1, "display_name": "x",
                 "tiers": {"floor_cleared": [{"item": "minecraft:emerald", "min_amount": 10, "max_amount": 10}]}}""");
        ModifierEffects doubled = new ModifierEffects(0, 0, 0, 100, 200, List.of(), true, true,
                Optional.empty(), Optional.empty(), 0);

        int amount = RewardValuation.value(SEED, List.of(LedgerEntry.floorCleared(1, TOWER, 1L)),
                table, doubled).get(0).amount();

        assertEquals(20, amount, "10 doubled by a 200% reward modifier");
    }

    @Test
    @DisplayName("every ledger kind that reaches here maps to the tier it belongs in")
    void kindsMapToTiers() {
        RewardTableDefinition table = table("""
                {"schema_version": 1, "display_name": "x",
                 "tiers": {
                   "opponent_defeated": [{"item": "minecraft:stone", "min_amount": 1, "max_amount": 1}],
                   "boss_defeated": [{"item": "minecraft:diamond", "min_amount": 1, "max_amount": 1}],
                   "floor_cleared": [{"item": "minecraft:emerald", "min_amount": 1, "max_amount": 1}]
                 }}""");
        List<LedgerEntry> ledger = List.of(
                LedgerEntry.opponentDefeated(1, TOWER, UUID.randomUUID(), 1L),
                LedgerEntry.bossDefeated(1, TOWER, 1L),
                LedgerEntry.floorCleared(1, TOWER, 1L));

        List<RewardValuation.Grant> grants = RewardValuation.value(SEED, ledger, table, ModifierEffects.NONE);

        assertEquals(3, grants.size());
        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "stone"), grants.get(0).item());
        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "diamond"), grants.get(1).item());
        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "emerald"), grants.get(2).item());
    }
}
