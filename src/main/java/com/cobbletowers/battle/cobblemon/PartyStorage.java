package com.cobbletowers.battle.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.ConstantsKt;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCPosition;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletowers.TowerLog;
import com.cobbletowers.runtime.PartyValidation.PartyMember;
import com.cobbletowers.storage.PartyArrangement;
import com.cobbletowers.storage.Slot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reads and rearranges a player's party and boxes (P18). The one place Cobblemon's storage is touched for
 * registration; everything it decides comes from {@link PartyArrangement}, which is pure and tested.
 *
 * <p>Moves are applied in two phases -- take every affected Pokemon out of its store, then set each at its
 * target -- so a target is never overwritten and a Pokemon is never in two places. If a set fails the
 * Pokemon that did not land are put straight back where they were, and as a last resort into any free
 * slot: a Pokemon is never left outside every store.
 */
public final class PartyStorage {

    private PartyStorage() {}

    /** One Pokemon as the chooser and the rules see it. */
    public record Entry(UUID id, String name, int level, boolean fainted, Slot slot) {}

    /**
     * Everything a player owns that registration can see.
     *
     * @param contents who is where, party and boxes together
     * @param pcSlots  every box slot that exists, in order
     * @param entries  the same Pokemon with their display details, in party-then-box order
     */
    public record Snapshot(Map<Slot, UUID> contents, List<Slot> pcSlots, Map<UUID, Entry> entries) {

        public Optional<PartyMember> member(UUID id) {
            Entry entry = entries.get(id);
            return entry == null ? Optional.empty() : Optional.of(new PartyMember(entry.id(), entry.level(), entry.fainted()));
        }
    }

    public static Snapshot snapshot(ServerPlayer player) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);

        Map<Slot, UUID> contents = new HashMap<>();
        Map<UUID, Entry> entries = new LinkedHashMap<>();
        for (int slot = 0; slot < PartyArrangement.PARTY_SIZE; slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon == null) continue;
            Slot where = Slot.party(slot);
            contents.put(where, pokemon.getUuid());
            entries.put(pokemon.getUuid(), entryOf(pokemon, where));
        }

        List<Slot> pcSlots = new ArrayList<>();
        List<PCBox> boxes = pc.getBoxes();
        for (int box = 0; box < boxes.size(); box++) {
            for (int slot = 0; slot < ConstantsKt.POKEMON_PER_BOX; slot++) {
                pcSlots.add(Slot.pc(box, slot));
            }
            for (Map.Entry<Integer, Pokemon> found : boxes.get(box).getNonEmptySlots().entrySet()) {
                Slot where = Slot.pc(box, found.getKey());
                contents.put(where, found.getValue().getUuid());
                entries.put(found.getValue().getUuid(), entryOf(found.getValue(), where));
            }
        }
        return new Snapshot(contents, pcSlots, entries);
    }

    private static Entry entryOf(Pokemon pokemon, Slot slot) {
        return new Entry(pokemon.getUuid(), pokemon.getDisplayName(false).getString(), pokemon.getLevel(),
                pokemon.isFainted(), slot);
    }

    /** True while the player is in any Cobblemon battle. Nothing is moved under a fight. */
    public static boolean inBattle(ServerPlayer player) {
        return BattleRegistry.getBattleByParticipatingPlayer(player) != null;
    }

    /**
     * Applies placements decided by {@link PartyArrangement}. {@code originals} is where each of those
     * Pokemon is now, so a failure can put it back.
     *
     * @return true when every Pokemon landed; false when a rollback was needed (and was carried out)
     */
    public static boolean apply(ServerPlayer player, Map<UUID, Slot> placements, Map<UUID, Slot> originals) {
        if (placements.isEmpty()) return true;
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);

        // Phase one: out. Looked up by id, so a stale slot cannot take out the wrong Pokemon.
        Map<UUID, Pokemon> held = new LinkedHashMap<>();
        for (UUID id : placements.keySet()) {
            Pokemon pokemon = party.get(id);
            PokemonStore<?> from = party;
            if (pokemon == null) {
                pokemon = pc.get(id);
                from = pc;
            }
            if (pokemon == null) {
                TowerLog.warn("Pokemon {} was expected in {}'s storage and was not found; leaving it", id, player.getUUID());
                continue;
            }
            if (from.remove(pokemon)) held.put(id, pokemon);
        }

        // Phase two: in. Whatever does not land is put back, never dropped.
        boolean clean = true;
        List<UUID> unplaced = new ArrayList<>(held.keySet());
        for (Map.Entry<UUID, Pokemon> entry : held.entrySet()) {
            try {
                set(party, pc, placements.get(entry.getKey()), entry.getValue());
                unplaced.remove(entry.getKey());
            } catch (RuntimeException ex) {
                TowerLog.error("Could not place Pokemon {} for {}; it will be put back", entry.getKey(), player.getUUID(), ex);
                clean = false;
            }
        }
        for (UUID id : unplaced) {
            Pokemon pokemon = held.get(id);
            Slot home = originals.get(id);
            boolean back = false;
            try {
                if (home != null) {
                    set(party, pc, home, pokemon);
                    back = true;
                }
            } catch (RuntimeException ex) {
                TowerLog.error("Could not put Pokemon {} back at {}", id, home, ex);
            }
            if (!back && !party.add(pokemon) && !pc.add(pokemon)) {
                // Nothing accepts it. Say so loudly: this is the one outcome that must never be quiet.
                TowerLog.error("POKEMON {} OF PLAYER {} IS HELD BY NO STORE AND COULD NOT BE PLACED", id, player.getUUID());
            }
        }
        return clean;
    }

    private static void set(PlayerPartyStore party, PCStore pc, Slot slot, Pokemon pokemon) {
        if (slot.isParty()) {
            party.set(new PartyPosition(slot.index()), pokemon);
        } else {
            pc.set(new PCPosition(slot.index(), slot.sub()), pokemon);
        }
    }
}
