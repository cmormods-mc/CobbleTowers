package com.cobbletowers.battle.cobbleraids;

import com.cobbleraids.api.encounter.CobbleRaidsEncounters;
import com.cobbleraids.api.encounter.EncounterListener;
import com.cobbleraids.api.encounter.EncounterPolicy;
import com.cobbleraids.api.encounter.EncounterRules;
import com.cobbleraids.api.encounter.EncounterRequest;
import com.cobbleraids.api.encounter.EncounterResult;
import com.cobbleraids.api.encounter.LeaveReason;
import com.cobbleraids.api.encounter.StartResult;
import com.cobbletowers.TowerLog;
import com.cobbletowers.modifier.ModifierEffects;
import com.cobbletowers.encounter.BossDraw;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The boss half of a floor, run by CobbleRaids (TDS #52, #53).
 *
 * <p>The only place this mod names the CobbleRaids API, as {@code battle/cobblemon/} is the only
 * place it names a Cobblemon battle. Two adapters, so a floor's rules read the same whichever engine
 * is executing the fight.
 *
 * <p>This is also what makes the boss multiplayer without any work here: CobbleRaids already runs one
 * to four players against a shared-health boss, proven at four players in Milestone 1. The ordinary
 * encounters are one battle each precisely so that this can be the shared one.
 */
public final class TowerBossAdapter {

    /** Who owns these encounters, so CobbleRaids can tell a tower boss from a wild raid. */
    private static final ResourceLocation OWNER = ResourceLocation.fromNamespaceAndPath("cobbletowers", "floor");

    /**
     * The policy the spike proved: no catching, no raid rewards, no raid progression or history, and
     * damage and PP carried back to the party.
     *
     * <p>Carryover is TDS #16 -- a tower floor is fought with what the party has left. The rest is
     * the tower owning its own economy: a boss beaten here feeds the unclaimed pool, and must not
     * also pay out as a wild raid would.
     */
    static final EncounterPolicy POLICY = EncounterPolicy.none().withCarryover(true, true);

    /** What a running boss belongs to. Ids only; a live reference here would pin its level. */
    public record Binding(UUID runId, int floorIndex, ResourceLocation definition, int level) {}

    private static final Map<UUID, Binding> BY_ENCOUNTER = new HashMap<>();
    private static final Map<UUID, UUID> ENCOUNTER_BY_RUN = new HashMap<>();

    private TowerBossAdapter() {}

    /** What happens when a boss ends. Implemented by the floor, called by this. */
    public interface Listener {
        void onBossEnded(MinecraftServer server, Binding binding, EncounterResult result);

        default void onParticipantLeft(MinecraftServer server, Binding binding, UUID playerId, LeaveReason reason) {}
    }

    private static Listener listener = new Listener() {
        @Override
        public void onBossEnded(MinecraftServer server, Binding binding, EncounterResult result) {}
    };

    public static void install(Listener floorListener) {
        listener = floorListener;
    }

    /**
     * Starts this floor's boss against everybody still standing.
     *
     * @return the encounter id, or empty when CobbleRaids refused -- an unknown definition, a missing
     *         Showdown integration, too many players. All of those are technical faults, never a loss.
     */
    public static Optional<UUID> start(MinecraftServer server, ServerLevel level, List<ServerPlayer> players,
                                       BossDraw.Boss boss, BlockPos where, UUID runId, int floorIndex) {
        return start(server, level, players, boss, where, runId, floorIndex, ModifierEffects.NONE);
    }

    /**
     * The same start, under the rules a run has drafted (TDS #2: the challenge affects the next boss).
     *
     * <p>This is the whole of the modifier-to-CobbleRaids boundary, and it is one method on one
     * adapter on purpose -- the GATE's rule that external-mod integration lives behind an adapter, so
     * a version change there has the smallest blast radius here. Nothing else in CobbleTowers knows
     * that {@code EncounterRules} exists.
     *
     * <p>Only the <b>boss</b> is fought under these. A floor's prerequisite battles are ordinary
     * Cobblemon battles run by our own adapter, which CobbleRaids is no part of -- and that matches
     * #2 exactly, which says the selected challenge affects the next boss.
     */
    public static Optional<UUID> start(MinecraftServer server, ServerLevel level, List<ServerPlayer> players,
                                       BossDraw.Boss boss, BlockPos where, UUID runId, int floorIndex,
                                       ModifierEffects effects) {
        if (players.isEmpty()) return Optional.empty();
        if (players.size() > EncounterRequest.MAX_PLAYERS) {
            TowerLog.error("Floor {} of run {} has {} players; CobbleRaids takes at most {}",
                    floorIndex, runId, players.size(), EncounterRequest.MAX_PLAYERS);
            return Optional.empty();
        }

        UUID encounterId = UUID.randomUUID();
        EncounterRules rules = rulesFor(effects);
        // Said from this side as well as CobbleRaids', so a rule that goes missing can be pinned to
        // the boundary it went missing at rather than argued about from one end (TDS #60).
        TowerLog.info("Floor {} of run {} starts its boss {}", floorIndex, runId,
                rules.restrictsAnything() ? "under " + rules : "under ordinary rules");
        EncounterRequest request = new EncounterRequest(OWNER, encounterId, players, boss.definition(), level,
                Vec3.atBottomCenterOf(where), boss.level(),
                // No health override: CobbleRaids scales the pool from its own definition and the
                // party size, which is the behaviour the spike measured.
                OptionalLong.empty(), POLICY, rules);

        StartResult result;
        try {
            result = CobbleRaidsEncounters.start(request, new FloorListener(server));
        } catch (RuntimeException ex) {
            TowerLog.error("Starting the boss for floor {} of run {} threw", floorIndex, runId, ex);
            return Optional.empty();
        }
        if (result instanceof StartResult.Refused refused) {
            TowerLog.error("CobbleRaids refused the boss for floor {} of run {}: {}",
                    floorIndex, runId, refused.reason());
            return Optional.empty();
        }

        UUID started = ((StartResult.Started) result).encounterId();
        Binding binding = new Binding(runId, floorIndex, boss.definition(), boss.level());
        BY_ENCOUNTER.put(started, binding);
        ENCOUNTER_BY_RUN.put(runId, started);
        TowerLog.info("Floor {} boss {} started at level {} for {} player(s), encounter {}",
                floorIndex, boss.definition(), boss.level(), players.size(), started);
        return Optional.of(started);
    }

    /**
     * A run's drafted effects as the rules CobbleRaids understands.
     *
     * <p>A translation and nothing else: no decision is taken here that {@code ModifierEffects} did
     * not already take, so the two cannot drift into disagreeing about what a party drafted.
     */
    private static EncounterRules rulesFor(ModifierEffects effects) {
        if (!effects.changesBattleRules()) return EncounterRules.none();
        return EncounterRules.none()
                .withBannedMoves(effects.bannedMoves())
                .withSwitching(effects.switchingAllowed())
                .withItems(effects.itemsAllowed())
                .withWeather(effects.weather())
                .withTerrain(effects.terrain())
                .withHealthPercent(effects.bossHealthPercent());
    }

    /** Ends a run's boss without it being fought out: abandoning, parking, shutting down. */
    public static boolean abort(UUID runId) {
        UUID encounterId = ENCOUNTER_BY_RUN.remove(runId);
        if (encounterId == null) return false;
        BY_ENCOUNTER.remove(encounterId);
        try {
            // CobbleRaids removes the boss itself on abort, which is what keeps the cell clean enough
            // for P3's sweep to release it rather than quarantine it.
            return CobbleRaidsEncounters.abort(encounterId);
        } catch (RuntimeException ex) {
            TowerLog.error("Aborting the boss of run {} threw", runId, ex);
            return false;
        }
    }

    public static boolean isFighting(UUID runId) {
        UUID encounterId = ENCOUNTER_BY_RUN.get(runId);
        return encounterId != null && CobbleRaidsEncounters.isActive(encounterId);
    }

    public static Optional<Binding> of(UUID runId) {
        UUID encounterId = ENCOUNTER_BY_RUN.get(runId);
        return encounterId == null ? Optional.empty() : Optional.ofNullable(BY_ENCOUNTER.get(encounterId));
    }

    public static int active() {
        return BY_ENCOUNTER.size();
    }

    public static int onServerStopped() {
        int held = BY_ENCOUNTER.size();
        BY_ENCOUNTER.clear();
        ENCOUNTER_BY_RUN.clear();
        return held;
    }

    /** Routes CobbleRaids' callbacks back to the floor that asked for the boss. */
    private record FloorListener(MinecraftServer server) implements EncounterListener {

        @Override
        public void onParticipantLeft(UUID encounterId, UUID playerId, LeaveReason reason) {
            Binding binding = BY_ENCOUNTER.get(encounterId);
            if (binding == null) return;
            try {
                listener.onParticipantLeft(server, binding, playerId, reason);
            } catch (RuntimeException ex) {
                // Contained for the same reason the Cobblemon adapter's handler is: this is called
                // from the other mod's battle path, and throwing back into it costs more than a floor.
                TowerLog.error("Handling a player leaving the boss of run {} threw", binding.runId(), ex);
            }
        }

        @Override
        public void onEnded(EncounterResult result) {
            Binding binding = BY_ENCOUNTER.remove(result.encounterId());
            if (binding == null) return;
            ENCOUNTER_BY_RUN.remove(binding.runId(), result.encounterId());
            try {
                listener.onBossEnded(server, binding, result);
            } catch (RuntimeException ex) {
                TowerLog.error("Handling the end of the boss of run {} threw", binding.runId(), ex);
            }
        }
    }
}
