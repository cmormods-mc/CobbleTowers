package com.cobbletowers.definition;

import com.cobbletowers.persistence.PersistedRun;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Which ruleset a run is played under (P32): a floor's override, else the tower's, then narrowed by the playlist. The
 * one place that answers it.
 */
public final class RulesetResolver {

    private RulesetResolver() {}

    /** The effective ruleset for a run on a floor, or null when the tower or its ruleset is not loaded. */
    public static RulesetDefinition forRun(TowerContent content, PersistedRun run, Optional<ResourceLocation> floorOverride) {
        TowerDefinition tower = content.towers().get(run.towerId());
        if (tower == null) return null;
        RulesetDefinition ruleset = narrowed(content.rulesets().get(floorOverride.orElse(tower.rulesetId())), run.options().playlist());
        return locked(ruleset, run.options().enemyLevelLock());
    }

    /** A trial's level lock (P32): every enemy is exactly this level, so results compare. */
    public static RulesetDefinition locked(RulesetDefinition ruleset, int lock) {
        if (ruleset == null || lock <= 0) return ruleset;
        return ruleset.withLevelsAndParty(lock, lock, ruleset.registeredPartySize());
    }

    /** The tower's own ruleset narrowed by a playlist (the lobby, before a run exists). */
    public static RulesetDefinition forTower(TowerContent content, TowerDefinition tower, Optional<ResourceLocation> playlist) {
        if (tower == null) return null;
        return narrowed(content.rulesets().get(tower.rulesetId()), playlist);
    }

    private static RulesetDefinition narrowed(RulesetDefinition base, Optional<ResourceLocation> playlist) {
        if (base == null || playlist.isEmpty()) return base;
        return PlaylistRegistry.get(playlist.get()).map(found -> found.narrow(base)).orElse(base);
    }
}
