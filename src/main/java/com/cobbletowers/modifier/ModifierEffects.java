package com.cobbletowers.modifier;

import com.cobbletowers.definition.ModifierDefinition;
import java.util.List;

/**
 * What a run's accumulated modifiers add up to.
 *
 * <p>This is the whole of the "typed, not monolithic" promise in TDS #56: modifiers are summed once,
 * here, into the parameters the draw functions already take. Nothing downstream asks which modifiers
 * a run is carrying, and no encounter code contains a conditional naming one.
 *
 * <p><b>No level arithmetic lives here.</b> An offset is carried to {@code TowerLevelPolicy}, which
 * is the one place tower level maths exists (TDS #45). Adding a clamp here would make two.
 */
public record ModifierEffects(
        int levelOffset,
        int extraOpponents,
        int bossLevelOffset,
        int bossHealthPercent,
        int rewardPercent) {

    /** A run carrying nothing: every field is the value that changes nothing. */
    public static final ModifierEffects NONE = new ModifierEffects(0, 0, 0, 100, 100);

    /**
     * Sums a run's modifiers.
     *
     * <p>Offsets add; percentages <b>compound</b>, because two modifiers that each say "a quarter
     * more health" should give more than one of them does, and adding percentages would make three
     * of them mean something quite different from what each one claims.
     *
     * <p>Integer arithmetic throughout, so the same list always gives the same numbers. The rounding
     * is order-sensitive in the last digit, which is why the caller passes the list in draft order
     * -- a stable order the run itself records -- rather than anything derived from a map.
     */
    public static ModifierEffects of(List<ModifierDefinition> modifiers) {
        int level = 0;
        int opponents = 0;
        int bossLevel = 0;
        int bossHealth = 100;
        int reward = 100;
        for (ModifierDefinition modifier : modifiers) {
            ModifierDefinition.Effect effect = modifier.effect();
            level += effect.levelOffset();
            opponents += effect.extraOpponents();
            bossLevel += effect.bossLevelOffset();
            bossHealth = bossHealth * effect.bossHealthPercent() / 100;
            reward = reward * effect.rewardPercent() / 100;
        }
        // A pool of zero is not a boss, it is a corpse: compounding reductions could reach it, and a
        // boss that dies to the first hit would read as a bug rather than as a drafted advantage.
        return new ModifierEffects(level, opponents, bossLevel, Math.max(bossHealth, 1), Math.max(reward, 0));
    }

    /** Applies the boss health percentage to a pool the adapter would otherwise have used. */
    public long applyBossHealth(long baseline) {
        return Math.max(1L, baseline * bossHealthPercent / 100);
    }
}
