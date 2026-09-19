package com.cobbletowers.reward;

import com.cobbletowers.api.reward.RewardKind;
import com.cobbletowers.definition.RewardTableDefinition;
import com.cobbletowers.modifier.ModifierEffects;
import com.cobbletowers.persistence.LedgerEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Turns a slice of a run's ledger into what it is actually worth.
 *
 * <p>Pure -- no server, no store -- so every rule here (the growth step, where reward-percent lands)
 * is a unit test with no Minecraft in reach, the way every other draw in this codebase is.
 *
 * <p>{@code POOL_FORFEITED} never reaches here: a caller only ever passes the slice of the ledger a
 * run is actually banking, and a forfeited run's ledger is never banked at all.
 */
public final class RewardValuation {

    private RewardValuation() {}

    /** One item, at the amount it was actually worth once growth and the run's modifiers applied. */
    public record Grant(ResourceLocation item, int amount) {}

    /**
     * What {@code priced} is worth from {@code table}, scaled by floor depth and by {@code effects}'s
     * reward percentage.
     *
     * <p>Growth is linear, the same additive shape {@code TowerLevelPolicy} scales level by, but as a
     * rate rather than a flat step: {@code amount + amount * growthPercentPerFloor * floorIndex /
     * 100}. Reward-percent is applied last, once per grant, through {@link
     * ModifierEffects#applyReward}, mirroring how {@code applyBossHealth} is the one place the boss's
     * own percentage is ever applied.
     *
     * @param runSeed what every roll here derives from, so a crash cannot reroll a grant (TDS #29)
     */
    public static List<Grant> value(long runSeed, List<LedgerEntry> priced, RewardTableDefinition table,
                                    ModifierEffects effects) {
        List<Grant> grants = new ArrayList<>();
        int n = 0;
        for (LedgerEntry entry : priced) {
            Optional<RewardKind> kind = toRewardKind(entry.kind());
            n++;
            if (kind.isEmpty()) continue;
            List<RewardTableDefinition.Entry> pool = table.entriesFor(kind.get());
            if (pool.isEmpty()) continue;

            RewardTableDefinition.Entry rolled = RewardDraw.pickItem(runSeed, entry.floorIndex(), n, pool);
            int amount = RewardDraw.rollAmount(runSeed, entry.floorIndex(), n, rolled.minAmount(), rolled.maxAmount());
            int grown = amount + amount * table.growthPercentPerFloor() * entry.floorIndex() / 100;
            int worth = effects.applyReward(grown);
            if (worth > 0) grants.add(new Grant(rolled.item(), worth));
        }
        return List.copyOf(grants);
    }

    private static Optional<RewardKind> toRewardKind(LedgerEntry.Kind kind) {
        return switch (kind) {
            case OPPONENT_DEFEATED -> Optional.of(RewardKind.OPPONENT_DEFEATED);
            case BOSS_DEFEATED -> Optional.of(RewardKind.BOSS_DEFEATED);
            case FLOOR_CLEARED -> Optional.of(RewardKind.FLOOR_CLEARED);
            case POOL_FORFEITED -> Optional.empty();
        };
    }
}
