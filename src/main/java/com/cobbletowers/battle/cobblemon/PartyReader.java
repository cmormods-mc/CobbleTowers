package com.cobbletowers.battle.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletowers.runtime.PartyValidation.PartyMember;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** Reads a player's live Cobblemon party into the plain values the run rules work with. */
public final class PartyReader {

    private PartyReader() {}

    /** The party as the web Hall draws it: display name, sprite name, level, fainted, types. Party order. */
    public static List<com.cobbletowers.network.TowerHallStatePayload.Mon> cards(ServerPlayer player) {
        List<com.cobbletowers.network.TowerHallStatePayload.Mon> cards = new ArrayList<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            com.cobblemon.mod.common.pokemon.Species species = pokemon.getSpecies();
            List<String> types = new ArrayList<>();
            for (com.cobblemon.mod.common.api.types.ElementalType type : species.getTypes()) types.add(type.getName());
            cards.add(new com.cobbletowers.network.TowerHallStatePayload.Mon(pokemon.getDisplayName(false).getString(),
                    spriteName(species.getName()), pokemon.getLevel(), pokemon.isFainted(), types));
        }
        return cards;
    }

    /** The sprite file name for a species name: lower case, letters and digits only ("Mr. Mime" is mrmime). */
    public static String spriteName(String speciesName) {
        return speciesName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Party order, fainted members included: whether they matter is the ruleset's call. */
    public static List<PartyMember> members(ServerPlayer player) {
        List<PartyMember> members = new ArrayList<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            com.cobblemon.mod.common.pokemon.Species species = pokemon.getSpecies();
            List<String> types = new ArrayList<>();
            for (com.cobblemon.mod.common.api.types.ElementalType type : species.getTypes()) types.add(type.getName());
            // Labels are Cobblemon's own (legendary, mythical, ultra_beast, paradox...); fully evolved means nothing
            // further to evolve into.
            members.add(new PartyMember(pokemon.getUuid(), pokemon.getLevel(), pokemon.isFainted(), species.getName(),
                    types, species.getEvolutions().isEmpty(), java.util.Set.copyOf(species.getLabels())));
        }
        return members;
    }
}
