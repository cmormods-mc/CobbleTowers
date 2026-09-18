package com.cobbletowers.runtime;

import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.definition.RulesetDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Builds a run in its opening state, pinning the content it will be played against. */
public final class RunFactory {

    /**
     * Floors are numbered from 1, the way {@code FloorDefinition.index} and the tower's own
     * floor-numbering check are, so a run starts on floor 1 rather than on a zeroth floor that no
     * tower has.
     */
    public static final int FIRST_FLOOR = 1;

    private RunFactory() {}

    /**
     * A new run against {@code towerId}, or empty when no such tower is loaded.
     *
     * <p>The tower's revision and content digest are pinned here and never re-read: that is what
     * lets a later load tell "the tower was edited under this run" from "the tower is gone" (TDS
     * #40). The seed is stored for the same reason -- an interrupted encounter is rebuilt from it
     * rather than rerolled (TDS #29).
     */
    public static Optional<PersistedRun> create(TowerContent content, ResourceLocation towerId,
                                                List<UUID> players, long seed, long now) {
        TowerDefinition tower = content.towers().get(towerId);
        if (tower == null) return Optional.empty();

        RulesetDefinition ruleset = content.rulesets().get(tower.rulesetId());
        String digest = content.summary(towerId).map(summary -> summary.contentDigest()).orElse("");

        List<PersistedParticipant> participants = new ArrayList<>(players.size());
        for (UUID player : players) {
            // No registered Pokemon yet: the party is captured when it is validated, which is a
            // floor's worth of work away and belongs to the phase that owns party rules.
            participants.add(new PersistedParticipant(player, ParticipantState.joined(), List.of()));
        }

        return Optional.of(new PersistedRun(UUID.randomUUID(), PersistedRun.SCHEMA_VERSION, towerId,
                tower.revision(), digest, ruleset == null ? 0 : ruleset.revision(),
                // No structures exist yet; the field is versioned independently so P4 can fill it
                // without touching the rest of the schema.
                0, seed, FIRST_FLOOR, RunState.CREATED, participants, Optional.empty(), List.of(), now,
                // No cell until the run reaches ALLOCATING_INSTANCE and one is leased to it.
                OptionalInt.empty()));
    }
}
