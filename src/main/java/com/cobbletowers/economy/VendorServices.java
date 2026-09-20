package com.cobbletowers.economy;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletowers.definition.VendorEffect;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a vendor purchase actually does to a target's live party (TDS #20 C).
 *
 * <p>The same live-party read {@code CobblemonBattleAdapter}'s {@code recallParties} and
 * {@code TowerEncounters.levelsOf} already use -- not a new way of reaching a player's Pokemon.
 */
public final class VendorServices {

    private VendorServices() {}

    public static void apply(VendorEffect effect, ServerPlayer target) {
        switch (effect) {
            case FULL_HEAL -> {
                for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(target)) {
                    pokemon.setCurrentHealth(pokemon.getMaxHealth());
                }
            }
            case CURE_STATUS -> {
                for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(target)) {
                    pokemon.setStatus(null);
                }
            }
        }
    }
}
