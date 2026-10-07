package com.cobbletowers.economy;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.RentalSetDefinition;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Gives a lent rental Pokemon an AscensionLib profile. A player's own Pokemon get theirs when they are captured or hatched, and a
 * battle's ascension effects come from the profile stored for each Pokemon's id; a rental is a brand-new Pokemon that never went
 * through either, so without this its side of the battle fought with no effects while the opponents' (declared by
 * {@link AscensionLibScouting}) did.
 *
 * <p>Reached by reflection like the other AscensionLib bridges, because the library is optional here: the contract is
 * {@code com.ascensionlib.AscensionGrants.grantWithRarity(Pokemon, String)}, the one it keeps for any mod that hands a player a
 * Pokemon. The rental set's own rarity decides the profile's rarity (its modifier slots are rolled, as for any grant), so an epic card
 * is an epic profile. Fire and forget: a failure is logged and never touches a run, and a rental that was not profiled simply fights
 * without effects, as before.
 */
public final class AscensionLibGrants {

    private static final String MOD_ID = "ascensionlib";
    private static final String CLASS = "com.ascensionlib.AscensionGrants";

    private static boolean resolved;
    private static Method grant;
    private static boolean saidUnknown;

    private AscensionLibGrants() {}

    /** The library's rarity id for a rental set's rarity: the same words, except that its top tier is spelled {@code mythical}. */
    public static String rarityId(RentalSetDefinition.Rarity rarity) {
        return rarity == RentalSetDefinition.Rarity.MYTHIC ? "mythical" : rarity.lower();
    }

    /** Profiles the rentals just placed in a party, each at its set's rarity. {@code rentals} and {@code sets} are in the same order. */
    public static void profileRentals(List<Pokemon> rentals, List<RentalSetDefinition> sets) {
        if (!resolve() || grant == null) return;
        for (int i = 0; i < rentals.size() && i < sets.size(); i++) {
            Pokemon rental = rentals.get(i);
            String rarity = rarityId(sets.get(i).rarity());
            try {
                String result = String.valueOf(grant.invoke(null, rental, rarity));
                // DISABLED is the library saying no world is running; nothing to say about it for every rental.
                if (!"GRANTED".equals(result) && !"DISABLED".equals(result) && !saidUnknown) {
                    saidUnknown = true;
                    TowerLog.warn("AscensionLib did not profile rental {} at {}: {} (said once)", rental.getUuid(), rarity, result);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                TowerLog.error("Profiling rental {} for AscensionLib failed", rental.getUuid(), ex);
            }
        }
    }

    private static boolean resolve() {
        if (!resolved) {
            resolved = true;
            if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return false;
            try {
                grant = Class.forName(CLASS).getMethod("grantWithRarity", Pokemon.class, String.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                grant = null;
                TowerLog.error("AscensionLib is installed but " + CLASS + " does not match what CobbleTowers expects, "
                        + "so rentals will fight without ascension profiles", ex);
            }
        }
        return grant != null;
    }
}
