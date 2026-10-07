package com.cobbletowers.persistence;

import com.cobbletowers.api.tower.participant.CombatState;
import com.cobbletowers.api.tower.participant.ConnectionState;
import com.cobbletowers.api.tower.participant.MembershipState;
import com.cobbletowers.api.tower.participant.ParticipantState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * One participant as written to disk: an id, three state axes and the registered Pokemon. Identifiers only (TDS
 * section 10); the Pokemon uuids are captured at validation so rearranging a party between floors does not rewrite
 * the run.
 */
public record PersistedParticipant(UUID playerId, ParticipantState state, List<UUID> registeredPokemon) {

    public PersistedParticipant {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(state, "state");
        registeredPokemon = List.copyOf(registeredPokemon);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", playerId);
        tag.putString("connection", state.connection().name().toLowerCase(Locale.ROOT));
        tag.putString("combat", state.combat().name().toLowerCase(Locale.ROOT));
        tag.putString("membership", state.membership().name().toLowerCase(Locale.ROOT));
        ListTag pokemon = new ListTag();
        for (UUID id : registeredPokemon) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            pokemon.add(entry);
        }
        tag.put("registered", pokemon);
        return tag;
    }

    public static PersistedParticipant fromTag(CompoundTag tag) {
        List<UUID> registered = new ArrayList<>();
        ListTag stored = tag.getList("registered", Tag.TAG_COMPOUND);
        for (int i = 0; i < stored.size(); i++) {
            registered.add(stored.getCompound(i).getUUID("id"));
        }
        ParticipantState state = new ParticipantState(
                parse(ConnectionState.class, tag.getString("connection"), "connection"),
                parse(CombatState.class, tag.getString("combat"), "combat"),
                parse(MembershipState.class, tag.getString("membership"), "membership"));
        return new PersistedParticipant(tag.getUUID("player"), state, registered);
    }

    /**
     * An unknown axis value is refused rather than defaulted, which would put a player back in a state they were not
     * in.
     */
    private static <E extends Enum<E>> E parse(Class<E> type, String raw, String field) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("participant field '" + field + "' has unknown value '" + raw + "'");
        }
    }
}
