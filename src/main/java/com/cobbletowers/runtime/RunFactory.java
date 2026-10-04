package com.cobbletowers.runtime;

import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.definition.RulesetDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.definition.PlaylistRegistry;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.RunOptions;
import com.cobbletowers.persistence.RunModifierState;
import com.cobbletowers.ascension.AscensionPolicy;
import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.modifier.ForcedModifiers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        return create(content, towerId, players, Map.of(), seed, now);
    }

    /**
     * As {@link #create(TowerContent, ResourceLocation, List, long, long)}, registering each player's
     * Pokemon from {@code parties} (player id to Cobblemon Pokemon uuids, in party order). A player
     * with no entry registers nothing. The caller reads the live party, so this stays free of
     * Cobblemon.
     */
    public static Optional<PersistedRun> create(TowerContent content, ResourceLocation towerId,
                                                List<UUID> players, Map<UUID, List<UUID>> parties,
                                                long seed, long now) {
        return create(content, towerId, players, parties, 0, seed, now);
    }

    /**
     * As above, starting directly at Ascension {@code ascension} (P30): the run begins on that Ascension's first floor with
     * the modifiers Ascensions 1 through {@code ascension} would have forced already in force. Ignored (0) for a tower
     * that does not ascend. Who may start where is the lobby's decision, not this factory's.
     */
    public static Optional<PersistedRun> create(TowerContent content, ResourceLocation towerId,
                                                List<UUID> players, Map<UUID, List<UUID>> parties,
                                                int ascension, long seed, long now) {
        return create(content, towerId, players, parties, ascension, RunOptions.NONE, seed, now);
    }

    /**
     * As above, under {@code options} (P32): a playlist whose forced modifiers the run starts holding, and whether it is an
     * attempt at a trial. The options are written once and never change.
     */
    public static Optional<PersistedRun> create(TowerContent content, ResourceLocation towerId,
                                                List<UUID> players, Map<UUID, List<UUID>> parties,
                                                int ascension, RunOptions options, long seed, long now) {
        return create(content, towerId, players, parties, ascension, options, List.of(), seed, now);
    }

    /**
     * As above, also starting with {@code extraModifiers} in force: a trial's mutators (P32). Unknown ids are skipped.
     */
    public static Optional<PersistedRun> create(TowerContent content, ResourceLocation towerId,
                                                List<UUID> players, Map<UUID, List<UUID>> parties,
                                                int ascension, RunOptions options, List<ResourceLocation> extraModifiers,
                                                long seed, long now) {
        TowerDefinition tower = content.towers().get(towerId);
        if (tower == null) return Optional.empty();
        int startAscension = tower.ascension() ? Math.max(0, ascension) : 0;
        int startFloor = AscensionPolicy.firstFloorOf(startAscension, tower.floorCount());
        RunModifierState startModifiers = RunModifierState.EMPTY;
        for (ModifierDefinition forced : ForcedModifiers.drawAll(content.draftablePool(towerId, FIRST_FLOOR), List.of(),
                seed, startAscension)) {
            startModifiers = startModifiers.accumulating(forced.id());
        }
        // A playlist's house rules (Hardcore forces Empty Pockets) are modifiers the run starts holding.
        for (ResourceLocation forcedId : options.playlist().flatMap(PlaylistRegistry::get)
                .map(playlist -> playlist.forcedModifiers()).orElse(List.of())) {
            if (content.modifier(forcedId).isPresent()) startModifiers = startModifiers.accumulating(forcedId);
        }
        for (ResourceLocation extra : extraModifiers) {
            if (content.modifier(extra).isPresent()) startModifiers = startModifiers.accumulating(extra);
        }

        RulesetDefinition ruleset = com.cobbletowers.definition.RulesetResolver.forTower(content, tower, options.playlist());
        String digest = content.summary(towerId).map(summary -> summary.contentDigest()).orElse("");

        List<PersistedParticipant> participants = new ArrayList<>(players.size());
        for (UUID player : players) {
            // The party as it stood at creation (a snapshot, so reshuffling it between floors does not
            // rewrite the run). Party rules are validated later, against the live party.
            participants.add(new PersistedParticipant(player, ParticipantState.joined(),
                    parties.getOrDefault(player, List.of())));
        }

        return Optional.of(new PersistedRun(UUID.randomUUID(), PersistedRun.SCHEMA_VERSION, towerId,
                tower.revision(), digest, ruleset == null ? 0 : ruleset.revision(),
                // No structures exist yet; the field is versioned independently so P4 can fill it
                // without touching the rest of the schema.
                0, seed, startFloor, RunState.CREATED, participants, Optional.empty(), List.of(), now,
                // No cell until the run reaches ALLOCATING_INSTANCE and one is leased to it.
                OptionalInt.empty(),
                // Nothing earned yet; the pool fills as floors are cleared and is banked at cash-out.
                List.of(),
                // Nothing drafted yet (the first draft opens at the first intermission); a direct start at an
                // Ascension begins with the modifiers its earlier Ascensions forced.
                startModifiers,
                // Nothing banked yet.
                0,
                // Nothing bought yet; the vendor did not exist before this run's floor is reached.
                Map.of(),
                options));
    }
}
