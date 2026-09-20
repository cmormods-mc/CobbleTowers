package com.cobbletowers.modifier;

import com.cobbletowers.definition.ModifierDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        int rewardPercent,
        List<String> bannedMoves,
        boolean switchingAllowed,
        boolean itemsAllowed,
        Optional<String> weather,
        Optional<String> terrain,
        int scoutingBonus) {

    /** A run carrying nothing: every field is the value that changes nothing. */
    public static final ModifierEffects NONE = new ModifierEffects(0, 0, 0, 100, 100,
            List.of(), true, true, Optional.empty(), Optional.empty(), 0);

    public ModifierEffects {
        bannedMoves = List.copyOf(bannedMoves);
    }

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
        List<String> banned = new ArrayList<>();
        boolean switching = true;
        boolean items = true;
        Optional<String> weather = Optional.empty();
        Optional<String> terrain = Optional.empty();
        int scoutingBonus = 0;
        for (ModifierDefinition modifier : modifiers) {
            ModifierDefinition.Effect effect = modifier.effect();
            level += effect.levelOffset();
            opponents += effect.extraOpponents();
            bossLevel += effect.bossLevelOffset();
            bossHealth = bossHealth * effect.bossHealthPercent() / 100;
            reward = reward * effect.rewardPercent() / 100;
            scoutingBonus += effect.scoutingBonus();

            for (String move : effect.bannedMoves()) {
                if (!banned.contains(move)) banned.add(move);
            }
            // A permission, once withdrawn, stays withdrawn. Two modifiers cannot disagree about
            // whether switching is allowed in a way that lets the party keep it -- the restrictive
            // answer is the one the party drafted.
            switching = switching && effect.allowSwitching();
            items = items && effect.allowItems();
            // A field is one condition, so the LAST drafted wins rather than the first. A party that
            // drafts rain and later drafts sun has chosen sun; the alternative silently ignores the
            // card they just voted for. Groups are how content stops the two being held at once.
            if (effect.weather().isPresent()) weather = effect.weather();
            if (effect.terrain().isPresent()) terrain = effect.terrain();
        }
        // A pool of zero is not a boss, it is a corpse: compounding reductions could reach it, and a
        // boss that dies to the first hit would read as a bug rather than as a drafted advantage.
        return new ModifierEffects(level, opponents, bossLevel, Math.max(bossHealth, 1), Math.max(reward, 0),
                banned, switching, items, weather, terrain, scoutingBonus);
    }

    /** Whether anything here changes how the boss battle itself is fought. */
    public boolean changesBattleRules() {
        return !bannedMoves.isEmpty() || !switchingAllowed || !itemsAllowed
                || weather.isPresent() || terrain.isPresent() || bossHealthPercent != 100;
    }

    /** Applies the boss health percentage to a pool the adapter would otherwise have used. */
    public long applyBossHealth(long baseline) {
        return Math.max(1L, baseline * bossHealthPercent / 100);
    }

    /** Applies the reward percentage to an amount {@link com.cobbletowers.reward.RewardValuation} rolled. */
    public int applyReward(int amount) {
        return Math.max(0, amount * rewardPercent / 100);
    }
}
