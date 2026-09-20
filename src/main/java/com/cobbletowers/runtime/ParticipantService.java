package com.cobbletowers.runtime;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.participant.CombatState;
import com.cobbletowers.api.tower.participant.ConnectionState;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.spectator.SpectatorPresentation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The one place a participant's state changes.
 *
 * <p>P1 built the three axes -- connection, combat, membership -- arguing that one enum could not
 * hold "knocked out and disconnected", and that a reconnect should restore what a player left rather
 * than guess. Nothing drove them until now: every participant in every run so far has been
 * {@code joined()} and stayed that way. This phase uses them, and adds no new state to do it.
 *
 * <p>Split the way the transition service is split: {@link #changed} and {@link #revivedAtIntermission}
 * are pure -- a run in, a run out -- and the {@code update} pair around them is the thin part that
 * writes. A rule cannot then come to mean two different things in the two handlers that trigger it,
 * and every rule here is testable without a server.
 */
public final class ParticipantService {

    /**
     * How long a disconnected player keeps their place, and their run keeps its cell.
     *
     * <p>Five minutes: long enough to survive a crash or a client restart on a modded pack, short
     * enough that one person logging off does not hold a cell and its chunk tickets all evening.
     * TDS #36 asks for this to be configurable; there is still no config system, and a named constant
     * with one caller is better than inventing one for a single number.
     */
    public static final long RECONNECT_WINDOW_MILLIS = 5L * 60 * 1000;

    private ParticipantService() {}

    /**
     * The run with one participant's state changed, or the same run when nothing moved.
     *
     * <p>Pure. {@code change} is applied to the participant's state and nowhere else, so a rule about
     * the connection axis cannot reach the combat axis by accident -- the reason the axes were
     * separated in the first place.
     */
    public static PersistedRun changed(PersistedRun run, UUID playerId, UnaryOperator<ParticipantState> change,
                                       long now) {
        List<PersistedParticipant> updated = new ArrayList<>(run.participants().size());
        boolean moved = false;
        for (PersistedParticipant participant : run.participants()) {
            if (!participant.playerId().equals(playerId)) {
                updated.add(participant);
                continue;
            }
            ParticipantState next = change.apply(participant.state());
            if (!next.equals(participant.state())) moved = true;
            updated.add(new PersistedParticipant(participant.playerId(), next, participant.registeredPokemon()));
        }
        return moved ? withParticipants(run, updated, now) : run;
    }

    /** Applies a change to one participant and writes the run. Returns the run as it now stands. */
    public static Optional<PersistedRun> update(MinecraftServer server, UUID runId, UUID playerId,
                                                UnaryOperator<ParticipantState> change, long now) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) return Optional.empty();

        PersistedRun written = changed(found.get(), playerId, change, now);
        if (written == found.get()) return found;
        // Not a checkpoint: a participant's state is recoverable from the run it belongs to, and a
        // disconnect is not a moment worth a synchronous write. The floor's own checkpoints carry it.
        TowerRuns.save(server, written, false);
        return Optional.of(written);
    }

    /** The run this player is in, if any. */
    public static Optional<PersistedRun> runOf(UUID playerId) {
        return TowerRuns.forPlayer(playerId);
    }

    public static Optional<ParticipantState> stateOf(PersistedRun run, UUID playerId) {
        return run.participants().stream()
                .filter(participant -> participant.playerId().equals(playerId))
                .map(PersistedParticipant::state)
                .findFirst();
    }

    /** True when nobody in the run is here and able to play it. */
    public static boolean nobodyLeft(PersistedRun run) {
        return run.participants().stream().noneMatch(participant -> participant.state().isInRun()
                && participant.state().connection() == ConnectionState.ONLINE);
    }

    /**
     * The floor is over and its spectators are promised the next intermission.
     *
     * <p>A separate state from spectating, because they are separate facts: one says they are out of
     * a floor being fought, the other that the floor is done and they are coming back. A player who
     * reads "you rejoin at the intermission" is reading this one.
     */
    public static PersistedRun pendingRevival(PersistedRun run, long now) {
        return mapStates(run, now, state -> state.isSpectating() ? state.revivePending() : state);
    }

    /**
     * Everyone waiting on an intermission returns to the fight.
     *
     * <p>Spectators are taken too, not only those already marked pending: a player who was knocked
     * out during a floor that then ended some other way -- a boss aborted, a run resumed -- is owed
     * the same intermission as everybody else, and the alternative is a participant stuck spectating
     * a floor that is no longer being fought.
     */
    public static PersistedRun revivedAtIntermission(PersistedRun run, long now) {
        return mapStates(run, now, state -> state.isSpectating() || state.combat() == CombatState.REVIVE_PENDING
                ? state.revived() : state);
    }

    public static void markRevivePending(MinecraftServer server, UUID runId, long now) {
        TowerRuns.get(runId).ifPresent(run -> {
            PersistedRun written = pendingRevival(run, now);
            if (written == run) return;
            TowerRuns.save(server, written, false);
            TowerLog.info("Run {}: its spectators return at the intermission", runId);
        });
    }

    public static void reviveAtIntermission(MinecraftServer server, UUID runId, long now) {
        TowerRuns.get(runId).ifPresent(run -> {
            PersistedRun written = revivedAtIntermission(run, now);
            if (written == run) return;
            // Read from the run as it stood before the write: exactly who this revival is taking off
            // spectating, so their camera is released the same moment their combat state leaves it.
            for (PersistedParticipant participant : run.participants()) {
                if (!participant.state().isSpectating() && participant.state().combat() != CombatState.REVIVE_PENDING) {
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
                if (player != null) SpectatorPresentation.stopSpectating(player);
            }
            TowerRuns.save(server, written, false);
            TowerLog.info("Run {} revived its spectators at the intermission", runId);
        });
    }

    private static PersistedRun mapStates(PersistedRun run, long now, UnaryOperator<ParticipantState> change) {
        List<PersistedParticipant> updated = new ArrayList<>(run.participants().size());
        boolean moved = false;
        for (PersistedParticipant participant : run.participants()) {
            ParticipantState next = change.apply(participant.state());
            if (!next.equals(participant.state())) moved = true;
            updated.add(new PersistedParticipant(participant.playerId(), next, participant.registeredPokemon()));
        }
        return moved ? withParticipants(run, updated, now) : run;
    }

    private static PersistedRun withParticipants(PersistedRun run, List<PersistedParticipant> participants, long now) {
        return new PersistedRun(run.runId(), run.schemaVersion(), run.towerId(), run.towerRevision(),
                run.towerDigest(), run.rulesetRevision(), run.structureRevision(), run.seed(), run.floorIndex(),
                run.state(), participants, run.lastCheckpoint(), run.committedTransactions(), now, run.cell(),
                run.ledger(), run.modifiers(), run.lastBankedFloor(), run.vendorPurchases());
    }
}
