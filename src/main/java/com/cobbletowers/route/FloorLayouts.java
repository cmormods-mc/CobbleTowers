package com.cobbletowers.route;

import com.cobbletowers.definition.FloorDefinition;
import com.cobbletowers.definition.FloorLayout;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.persistence.PersistedRun;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * What a floor of a run is built from (P50 stage 2): the layout of the run's own tower, unless a detour sent the party to another
 * region's building for that floor. Every place that needs a floor's layout asks here, so they cannot disagree about which building
 * the party is in.
 */
public final class FloorLayouts {

    private FloorLayouts() {}

    public static Optional<FloorLayout> layoutOf(TowerContent content, PersistedRun run, int floorIndex) {
        Optional<FloorLayout> own = content.floorAt(run.towerId(), floorIndex).flatMap(FloorDefinition::layout);
        Optional<ResourceLocation> arena = run.modifiers().arenaAt(floorIndex);
        if (arena.isEmpty() || arena.get().equals(run.towerId())) return own;
        // the same floor of the other tower: its anchors are for its own building, so the pair always fit
        return content.floorAt(arena.get(), floorIndex).flatMap(FloorDefinition::layout).or(() -> own);
    }

    /** The tower whose building a floor is fought in: the detour's, or the run's own. */
    public static ResourceLocation placeOf(PersistedRun run, int floorIndex) {
        return run.modifiers().arenaAt(floorIndex).orElse(run.towerId());
    }
}
