package com.cobbletowers.economy;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.RentalSetDefinition;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Gives a lent rental Pokemon an AscensionLib profile, since a new rental never went through capture or hatching.
 * Reached by reflection ({@code com.ascensionlib.AscensionGrants.grantWithRarity}); the set's rarity sets the
 * profile's, and then every upgrade credit its level has earned is spent on random slots (a rental is never crafted
 * on, so nobody else would). Fire and forget: a failure is logged and the rental fights without effects.
 */
public final class AscensionLibGrants {

    private static final String MOD_ID = "ascensionlib";
    private static final String CLASS = "com.ascensionlib.AscensionGrants";

    private static boolean resolved;
    private static Method grant;
    /** Absent in an AscensionLib older than the random upgrades; then rentals keep rank I affixes. */
    private static Method upgrade;
    private static boolean saidUnknown;

    private AscensionLibGrants() {}

    /**
     * The library's rarity id for a rental set's rarity: the same words, except that its top tier is spelled {@code
     * mythical}.
     */
    public static String rarityId(RentalSetDefinition.Rarity rarity) {
        return rarity == RentalSetDefinition.Rarity.MYTHIC ? "mythical" : rarity.lower();
    }

    /**
     * Profiles the rentals just placed in a party, each at its set's rarity. {@code rentals} and {@code sets} are in
     * the same order.
     */
    public static void profileRentals(List<Pokemon> rentals, List<RentalSetDefinition> sets) {
        if (!resolve() || grant == null) return;
        profile(rentals, sets);
        upgradeRentals(rentals);
    }

    /**
     * Spends every pending upgrade credit of each rental (a level milestone just reached, or the ones its starting level
     * earned) on randomly chosen affix slots, one rank each.
     */
    public static void upgradeRentals(List<Pokemon> rentals) {
        if (!resolve() || upgrade == null) return;
        for (Pokemon rental : rentals) {
            try {
                upgrade.invoke(null, rental);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                TowerLog.error("Upgrading rental " + rental.getUuid() + " for AscensionLib failed", ex);
            }
        }
    }

    private static void profile(List<Pokemon> rentals, List<RentalSetDefinition> sets) {
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
                try {
                    upgrade = Class.forName(CLASS).getMethod("upgradeRandomly", Pokemon.class);
                } catch (NoSuchMethodException old) {
                    upgrade = null;
                    TowerLog.warn("This AscensionLib has no upgradeRandomly; lent Pokemon keep rank I affixes (update AscensionLib)");
                }
            } catch (ReflectiveOperationException | LinkageError ex) {
                grant = null;
                TowerLog.error("AscensionLib is installed but " + CLASS + " does not match what CobbleTowers expects, "
                        + "so rentals will fight without ascension profiles", ex);
            }
        }
        return grant != null;
    }
}
