package com.cobbletowers.storage;

import com.cobbletowers.ServerState;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokedexDataChangedEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletowers.TowerLog;
import com.cobbletowers.battle.cobblemon.PartyStorage;
import com.cobbletowers.battle.cobblemon.RentalStorage;
import com.cobbletowers.persistence.PartyJournalEntry;
import com.cobbletowers.persistence.TowerPartyJournalStore;
import com.cobbletowers.rental.RentalDraft;
import com.cobbletowers.storage.PartyArrangement.Original;
import com.cobbletowers.storage.PartyArrangement.Plan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lends a player a team for a rental run and makes sure it is taken back (P33). Journal, flush, then move: the
 * journal names the rental ids before anything moves. Every exit goes through {@code PartyJournalService.restoreNow},
 * which deletes rentals first; a sweep removes tagged leaks.
 */
public final class RentalPartyService {

    public enum Lock { LOCKED, NO_ROOM, IN_BATTLE, FAILED }

    /** How often the stray sweep runs: once a minute. */
    private static final int SWEEP_EVERY_TICKS = 1200;
    /** Players who joined and are due a sweep, so one runs after their storage has loaded. */
    private static final Map<UUID, Integer> JOINED = new HashMap<>();

    static {
        ServerState.onStop(JOINED::clear);
    }
    private static int ticks;

    private RentalPartyService() {}

    public static void install() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                tick(server);
            } catch (RuntimeException ex) {
                TowerLog.error("The rental sweep failed", ex);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> JOINED.put(handler.getPlayer().getUUID(), 60));
        // DISCONNECT fires on a Netty thread; JOINED is a plain map the server tick iterates, so hop onto the server
        // thread.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            com.cobbletowers.runtime.ServerThread.run(server, () -> JOINED.remove(id));
        });
        // A rental never earns experience: it would level, and could evolve, and it is deleted when the run ends
        // anyway.
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(RentalPartyService::noExperience);
        // Nor does one reach the Pokedex: Cobblemon marks whatever enters a party as owned, and a lent Pokemon is not
        // the
        // player's. Servers hang rewards and ranks on Pokedex progress, so this is as much a leak as a stray Pokemon
        // would be.
        CobblemonEvents.POKEDEX_DATA_CHANGED_PRE.subscribe(RentalPartyService::noPokedex);
    }

    private static void noPokedex(PokedexDataChangedEvent.Pre event) {
        try {
            Pokemon pokemon = event.getDataSource().getPokemon();
            if (pokemon != null && RentalStorage.isRental(pokemon)) event.cancel();
        } catch (RuntimeException ex) {
            TowerLog.error("The rental Pokedex guard failed", ex);
        }
    }

    private static void noExperience(ExperienceGainedEvent.Pre event) {
        try {
            Pokemon pokemon = event.getPokemon();
            if (RentalStorage.isRental(pokemon)) event.setExperience(0);
        } catch (RuntimeException ex) {
            TowerLog.error("The rental experience guard failed", ex);
        }
    }

    /** Whether a player's own party could be moved aside to make room, without moving anything. */
    public static boolean canMakeRoom(ServerPlayer player) {
        PartyStorage.Snapshot snapshot = PartyStorage.snapshot(player);
        return PartyArrangement.plan(snapshot.contents(), snapshot.pcSlots(), List.of()).ok();
    }

    /**
     * Journals, flushes, moves the player's own Pokemon to their boxes, then creates the rentals and fills the party.
     */
    public static Lock lock(MinecraftServer server, UUID runId, ServerPlayer player, RentalDraft.Team team) {
        if (PartyStorage.inBattle(player)) return Lock.IN_BATTLE;
        TowerPartyJournalStore store = TowerPartyJournalStore.get(server);
        // An earlier journal means an earlier run's Pokemon are not yet back; put them back first so the new journal
        // is true.
        if (store.entryFor(player.getUUID()).isPresent()) PartyJournalService.restoreNow(server, player);

        PartyStorage.Snapshot snapshot = PartyStorage.snapshot(player);
        Plan plan = PartyArrangement.plan(snapshot.contents(), snapshot.pcSlots(), List.of());
        if (!plan.ok()) return Lock.NO_ROOM;

        // The whole safety argument: the rental ids and the originals, written and flushed before a single Pokemon
        // moves.
        List<PartyJournalEntry.LentCard> cards = new ArrayList<>();
        for (int i = 0; i < team.ids().size(); i++) {
            cards.add(new PartyJournalEntry.LentCard(team.ids().get(i), team.sets().get(i).id().toString(), team.god().get(i)));
        }
        store.put(new PartyJournalEntry(player.getUUID(), runId, plan.originals(), team.ids(), cards));
        store.checkpoint(server);

        try {
            if (!plan.originals().isEmpty()) {
                Map<UUID, Slot> from = new HashMap<>();
                for (Original original : plan.originals()) from.put(original.pokemon(), original.slot());
                if (!PartyStorage.apply(player, plan.placements(), from)) {
                    PartyJournalService.restoreNow(server, player);
                    return Lock.FAILED;
                }
            }
            List<Pokemon> rentals = new ArrayList<>();
            for (int i = 0; i < team.sets().size(); i++) rentals.add(RentalStorage.create(team.sets().get(i), team.ids().get(i)));
            int placed = RentalStorage.fillParty(player, rentals);
            if (placed != rentals.size()) {
                PartyJournalService.restoreNow(server, player);
                return Lock.FAILED;
            }
            // Once they are in the party (the library only profiles an owned Pokemon): the rentals fight with
            // ascension profiles like
            // any other Pokemon, instead of with none while their opponents have them.
            com.cobbletowers.economy.AscensionLibGrants.profileRentals(rentals, team.sets());
        } catch (RuntimeException ex) {
            TowerLog.error("Lending {} their rental team failed; putting everything back", player.getUUID(), ex);
            PartyJournalService.restoreNow(server, player);
            return Lock.FAILED;
        }
        TowerLog.info("Lent {} a team of {} for run {} ({} of their own moved aside)", player.getUUID(), team.sets().size(),
                runId, plan.originals().size());
        return Lock.LOCKED;
    }

    // ---- the stray sweep
    // ---------------------------------------------------------------------------------------------

    private static void tick(MinecraftServer server) {
        ticks++;
        // Players who just joined get a sweep once their storage has had a moment to load.
        for (UUID id : new ArrayList<>(JOINED.keySet())) {
            int left = JOINED.get(id) - 1;
            if (left > 0) {
                JOINED.put(id, left);
                continue;
            }
            JOINED.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) sweep(server, player);
        }
        if (ticks % SWEEP_EVERY_TICKS != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sweep(server, player);
    }

    /**
     * Deletes every rental Pokemon the player holds that is in no live rental run (anything tagged but not in a
     * journal entry is a leak).
     * @return how many were deleted
     */
    public static int sweep(MinecraftServer server, ServerPlayer player) {
        if (PartyStorage.inBattle(player)) return 0;
        List<UUID> held = RentalStorage.tagged(player);
        if (held.isEmpty()) return 0;
        Set<UUID> live = new HashSet<>();
        for (PartyJournalEntry entry : TowerPartyJournalStore.get(server).all()) {
            if (entry.player().equals(player.getUUID()) && !PartyJournalService.runIsOverFor(entry)) live.addAll(entry.rentals());
        }
        int removed = RentalStorage.removeAll(player, id -> !live.contains(id));
        if (removed > 0) TowerLog.warn("Swept {} stray rental Pokemon from {}", removed, player.getUUID());
        return removed;
    }
}
