package com.cobbletowers.storage;

import com.cobbletowers.ServerState;
import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.participant.MembershipState;
import com.cobbletowers.battle.cobblemon.PartyStorage;
import com.cobbletowers.persistence.PartyJournalEntry;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.TowerPartyJournalStore;
import com.cobbletowers.runtime.TowerRuns;
import com.cobbletowers.storage.PartyArrangement.Original;
import com.cobbletowers.storage.PartyArrangement.Plan;
import com.cobbletowers.storage.PartyArrangement.Restoration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Moves a player's registered Pokemon into the party at run start and puts everything back after (P18); see {@code
 * docs/design/P18-registration-chooser.md}. Order is journal, flush, then move; restoring is idempotent.
 */
public final class PartyJournalService {

    /** How a lock-in went. */
    public enum Lock {
        /** The party now is exactly the chosen Pokemon. */
        LOCKED,
        /** It already was, so nothing moved and nothing was journaled. */
        UNCHANGED,
        TOO_MANY,
        NOT_OWNED,
        NO_ROOM,
        /** A battle is on, or storage refused a move; nothing is left half done. */
        FAILED
    }

    /** Roughly once a second. */
    private static final int SWEEP_EVERY_TICKS = 20;
    private static int ticks;
    /** Players already told their boxes are full, so the once-a-second sweep does not repeat itself. */
    private static final java.util.Set<UUID> TOLD_FULL = new java.util.HashSet<>();

    static {
        ServerState.onStop(TOLD_FULL::clear);
    }

    private PartyJournalService() {}

    public static void install() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % SWEEP_EVERY_TICKS != 0) return;
            try {
                reconcile(server);
            } catch (RuntimeException ex) {
                TowerLog.error("The party journal sweep failed", ex);
            }
        });
    }

    /** What a lock-in would do, without doing it. Used to refuse a start before anyone is moved. */
    public static Plan dryRun(ServerPlayer player, List<UUID> chosen) {
        PartyStorage.Snapshot snapshot = PartyStorage.snapshot(player);
        return PartyArrangement.plan(snapshot.contents(), snapshot.pcSlots(), chosen);
    }

    /** Journals, flushes, then rearranges {@code player}'s party to exactly {@code chosen}. */
    public static Lock lockIn(MinecraftServer server, UUID runId, ServerPlayer player, List<UUID> chosen) {
        if (PartyStorage.inBattle(player)) return Lock.FAILED;
        // A journal still standing means an earlier run's Pokemon are not yet back. Put them back first,
        // so the new journal records the real layout and not a half-moved one.
        TowerPartyJournalStore store = TowerPartyJournalStore.get(server);
        if (store.entryFor(player.getUUID()).isPresent()) restoreNow(server, player);
        if (store.hasPending(player.getUUID())
                || com.cobbletowers.battle.cobblemon.RentalStorage.hasRentalInParty(player)) return Lock.FAILED;

        PartyStorage.Snapshot snapshot = PartyStorage.snapshot(player);
        Plan plan = PartyArrangement.plan(snapshot.contents(), snapshot.pcSlots(), chosen);
        if (!plan.ok()) {
            return switch (plan.failure()) {
                case TOO_MANY -> Lock.TOO_MANY;
                case NOT_OWNED -> Lock.NOT_OWNED;
                case NO_ROOM -> Lock.NO_ROOM;
            };
        }
        if (plan.originals().isEmpty()) return Lock.UNCHANGED;

        // The whole safety argument: written and flushed before a single Pokemon moves.
        store.put(new PartyJournalEntry(player.getUUID(), runId, plan.originals()));
        store.checkpoint(server);

        Map<UUID, Slot> from = new HashMap<>();
        for (Original original : plan.originals()) from.put(original.pokemon(), original.slot());
        boolean clean = PartyStorage.apply(player, plan.placements(), from);
        if (!clean) {
            // Whatever landed is journaled; put it all back now rather than leave a half-made party.
            restoreNow(server, player);
            return Lock.FAILED;
        }
        TowerLog.info("Registered {} Pokemon for {} in run {} ({} moved)", chosen.size(), player.getUUID(), runId,
                plan.placements().size());
        return Lock.LOCKED;
    }

    /**
     * Puts a player's collection back. The journal is retired only after verified restoration;
     * missing originals and refused moves retain it for recovery.
     */
    public static void restoreNow(MinecraftServer server, ServerPlayer player) {
        TowerPartyJournalStore store = TowerPartyJournalStore.get(server);
        Optional<PartyJournalEntry> found = store.entryFor(player.getUUID());
        if (found.isEmpty()) return;
        PartyJournalEntry entry = found.get();
        if (!mayRestoreParty(player)) return;

        // Rentals first (P33): they occupy the party, and the originals cannot come back to a full one. Deleted by
        // the ids the
        // journal recorded before they were created, so a crash mid-creation leaves nothing behind either.
        if (!entry.rentals().isEmpty()) {
            int removed = com.cobbletowers.battle.cobblemon.RentalStorage.removeAll(player, entry.rentals()::contains);
            TowerLog.info("Deleted {} rental Pokemon of {} from run {}", removed, player.getUUID(), entry.runId());
        }

        PartyStorage.Snapshot snapshot = PartyStorage.snapshot(player);
        // Check recorded ids too: a damaged rental tag must not cause us to forget an outstanding loan.
        if (!java.util.Collections.disjoint(snapshot.contents().values(), entry.rentals())) {
            TowerLog.errorOnce("rental-removal-" + player.getUUID(),
                    "Rental removal is incomplete for " + player.getUUID() + "; party journal kept for recovery");
            return;
        }
        Restoration restoration = PartyArrangement.restore(snapshot.contents(), snapshot.pcSlots(), entry.originals());

        Map<UUID, Slot> now = new HashMap<>();
        for (Map.Entry<Slot, UUID> held : snapshot.contents().entrySet()) now.put(held.getValue(), held.getKey());
        if (!PartyStorage.apply(player, restoration.placements(), now)) {
            TowerLog.warn("Party restoration for {} rolled back; keeping the journal for retry", player.getUUID());
            return;
        }

        // Store callbacks may refuse a move without throwing. Verify the actual layout before retiring the journal.
        if (!restorationComplete(restoration, PartyStorage.snapshot(player).contents())) {
            TowerLog.errorOnce("restore-incomplete-" + player.getUUID(),
                    "Party restoration is incomplete for " + player.getUUID() + "; journal retained for recovery");
            if (TOLD_FULL.add(player.getUUID())) player.sendSystemMessage(Component.literal(
                    "Your party restoration is pending. Your tower journal has been kept; please contact an administrator if it persists."));
            return;
        }

        if (!restoration.stranded().isEmpty()) {
            // Nowhere to put them: keep the journal so a later sweep can try again once there is room.
            if (TOLD_FULL.add(player.getUUID())) {
                // Said once: the sweep retries every second until the player makes room.
                TowerLog.error("Could not restore {} Pokemon of {}: no free slot anywhere. Journal kept.",
                        restoration.stranded().size(), player.getUUID());
                player.sendSystemMessage(Component.literal("Some of your Pokemon could not be put back: your party"
                        + " and boxes are full. Free a slot and they will be restored."));
            }
            return;
        }
        TOLD_FULL.remove(player.getUUID());
        store.remove(player.getUUID());
        store.checkpoint(server);

        TowerLog.info("Restored {}'s Pokemon from run {} ({} moved, {} missing, {} elsewhere)", player.getUUID(),
                entry.runId(), restoration.placements().size(), restoration.missing().size(), restoration.relocated().size());
        if (!restoration.placements().isEmpty()) {
            player.sendSystemMessage(Component.literal("Your party was put back as it was before the tower."));
        }
        if (!restoration.relocated().isEmpty()) {
            player.sendSystemMessage(Component.literal(restoration.relocated().size()
                    + " Pokemon went to the nearest free slot because their old one was taken."));
        }
    }

    /**
     * Restores every player whose run is over (gone, finished or left): one idempotent path for a normal ending, a
     * leave, a crash and an offline player. Skips anyone mid-battle; the next sweep retries.
     */
    public static void reconcile(MinecraftServer server) {
        TowerPartyJournalStore store = TowerPartyJournalStore.get(server);
        for (PartyJournalEntry entry : store.all()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.player());
            if (player == null) continue;
            // One player's broken entry must not keep everyone else's Pokemon from coming back.
            try {
                Optional<PersistedRun> run = TowerRuns.get(entry.runId());
                Optional<PersistedRun> current = TowerRuns.forPlayer(entry.player());
                // Old recovery work must never rearrange the party or terminate a newer run.
                if (current.isPresent() && current.get().state().isLive()
                        && !current.get().runId().equals(entry.runId())) continue;
                if (rentalNeedsExit(entry, run,
                        player.level().dimension().equals(com.cobbletowers.instance.TowerDimension.LEVEL), player.isAlive())) {
                    com.cobbletowers.runtime.TowerPresence.leave(server, player, System.currentTimeMillis());
                }
                if (!runIsOverFor(entry)) continue;
                // A parked rental run cannot safely resume once its borrowed team is returned.
                if (!entry.rentals().isEmpty() && run.isPresent()
                        && run.get().state() == com.cobbletowers.api.tower.RunState.RECOVERY_REQUIRED) {
                    com.cobbletowers.runtime.RunTransitionService.apply(server, entry.runId(),
                            com.cobbletowers.api.tower.RunEvent.RECOVERY_ABANDONED, System.currentTimeMillis());
                }
                com.cobbletowers.battle.cobblemon.CobblemonBattleAdapter.endRentalBattleOf(player, entry.rentals());
                if (!mayRestoreParty(player)) continue;
                restoreNow(server, player);
            } catch (RuntimeException ex) {
                TowerLog.errorOnce("restore-" + entry.player(), "Could not restore the Pokemon of " + entry.player(), ex);
            }
        }
    }

    /**
     * A withdrawn owned-boss actor is frozen and fights on clones. Collection placement may be
     * restored, while Cobblemon retains the actor's battle lock until the shared fight finishes.
     * Active or unrelated battles continue to prohibit storage changes.
     */
    public static boolean mayRestoreParty(ServerPlayer player) {
        return !PartyStorage.inBattle(player)
                || com.cobbletowers.battle.cobbleraids.TowerBossAdapter.hasWithdrawnParticipant(player);
    }

    static boolean restorationComplete(Restoration restoration, Map<Slot, UUID> actual) {
        return restoration.missing().isEmpty() && restoration.placements().entrySet().stream()
                .allMatch(move -> move.getKey().equals(actual.get(move.getValue())));
    }

    /** Unexpected death or displacement ends a rental membership, but startup has not teleported players yet. */
    static boolean rentalNeedsExit(PartyJournalEntry entry, Optional<PersistedRun> run, boolean inTower, boolean alive) {
        if (entry.rentals().isEmpty() || run.isEmpty() || !run.get().state().isLive()) return false;
        if (!alive) return true;
        return !inTower && switch (run.get().state()) {
            case FLOOR_READY, ENCOUNTER_ACTIVE, FLOOR_RESOLVING, INTERMISSION, NEXT_FLOOR_READY -> true;
            default -> false;
        };
    }

    /** True when the run that moved these Pokemon no longer needs them where they are. */
    static boolean runIsOverFor(PartyJournalEntry entry) {
        Optional<PersistedRun> run = TowerRuns.get(entry.runId());
        if (run.isEmpty() || run.get().isRetired()
                || (!entry.rentals().isEmpty() && !run.get().state().isLive())) return true;
        for (PersistedParticipant participant : run.get().participants()) {
            if (participant.playerId().equals(entry.player())) {
                return participant.state().membership() == MembershipState.VOLUNTARILY_LEFT;
            }
        }
        return true;   // the run does not list them at all
    }
}
