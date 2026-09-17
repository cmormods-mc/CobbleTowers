package com.cobbletowers.api.tower;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * A run, as an addon may read it: identifiers, logical state and immutable views.
 *
 * <p>Deliberately no live Minecraft or Cobblemon objects, and no way to change anything. A run is
 * owned by its tower; an addon observes it through {@link com.cobbletowers.api.event.TowerRunListener}
 * and reads it here.
 */
public interface TowerRunView {

    /** The run's own id: authoritative, and independent of who started it (TDS #12). */
    UUID runId();

    ResourceLocation towerId();

    RunState state();

    /** 1-based; the floor the run is on, or the one it last reached. */
    int floorIndex();

    /** Absent before the first floor is prepared. */
    Optional<FloorView> currentFloor();

    /** Every participant, including those who left or are spectating. */
    List<ParticipantView> participants();

    /** The seed every deterministic choice in this run derives from (TDS #29). */
    long seed();
}
