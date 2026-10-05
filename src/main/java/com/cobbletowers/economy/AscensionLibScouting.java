package com.cobbletowers.economy;

import com.cobbletowers.TowerLog;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Tells AscensionLib about the enemies a floor puts up, so its players can spend a Scouter on them. Reached by
 * reflection like {@link AscensionLibRewards}: the library is optional, and the contract is two static methods on
 * {@code com.ascensionlib.AscensionEncounters} taking only {@code java.*} types.
 *
 * <p>Fire and forget. Scouting is a convenience, so a failure is logged and never touches a floor, and nothing is
 * queued or retried: an enemy that was not declared simply cannot be scouted.
 */
public final class AscensionLibScouting {

    private static final String MOD_ID = "ascensionlib";
    private static final String CLASS = "com.ascensionlib.AscensionEncounters";

    private static boolean resolved;
    private static Method declare;
    private static Method end;
    private static boolean saidUnknown;

    private AscensionLibScouting() {}

    /** Declares one enemy of an encounter. {@code boss} makes its reveal shared with every participant. */
    public static void declare(String encounterId, Collection<UUID> participants, int enemyIndex, String tier,
                               boolean boss, String speciesId, int level) {
        if (!resolve() || declare == null) return;
        try {
            Object status = declare.invoke(null, encounterId, participants, enemyIndex, tier, boss, null, speciesId, level);
            String text = String.valueOf(status);
            // DISABLED is the library saying no world is running; nothing to say about it every floor.
            if (!"DECLARED".equals(text) && !"DISABLED".equals(text) && !saidUnknown) {
                saidUnknown = true;
                TowerLog.warn("AscensionLib could not declare {} (level {}) for scouting: {} (said once)", speciesId,
                        level, text);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            TowerLog.error("Declaring enemy {} for scouting failed", speciesId, ex);
        }
    }

    /** The encounter is over: the library forgets its enemies and every reveal. Safe to repeat. */
    public static void end(String encounterId) {
        if (!resolve() || end == null) return;
        try {
            end.invoke(null, encounterId);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            TowerLog.error("Ending scouting encounter {} failed", encounterId, ex);
        }
    }

    private static boolean resolve() {
        if (!resolved) {
            resolved = true;
            if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return false;
            try {
                Class<?> encounters = Class.forName(CLASS);
                declare = encounters.getMethod("declareEnemy", String.class, Collection.class, int.class, String.class,
                        boolean.class, String.class, String.class, int.class);
                end = encounters.getMethod("end", String.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                declare = null;
                end = null;
                TowerLog.error("AscensionLib is installed but " + CLASS + " does not match what CobbleTowers expects, "
                        + "so Scouters cannot be used", ex);
            }
        }
        return declare != null;
    }
}
