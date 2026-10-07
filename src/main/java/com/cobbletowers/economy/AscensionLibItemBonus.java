package com.cobbletowers.economy;

import com.cobbletowers.TowerLog;
import java.lang.reflect.Method;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The 777 Unique's loot bonus: asks AscensionLib how many of an item a reward should be (+20% while a Pokemon holding
 * 777 is in the party), by reflection ({@code com.ascensionlib.AscensionRewards.scaleItemQuantity}). On any failure
 * the original quantity is used. Plain item rewards only.
 */
public final class AscensionLibItemBonus {
    private static boolean resolved;
    private static Method scale;

    private AscensionLibItemBonus() {}

    /** @param key identifies this reward, so the library's rounding of a fraction is the same on every retry */
    public static int scale(UUID player, int count, String key) {
        Method method = resolve();
        if (method == null || count < 1) return count;
        try {
            Object result = method.invoke(null, player, count, key);
            return result instanceof Integer scaled && scaled >= count ? scaled : count;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            TowerLog.error("AscensionLib could not scale an item reward; using the original quantity", ex);
            return count;
        }
    }

    private static Method resolve() {
        if (resolved) return scale;
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded("ascensionlib")) return null;
        try {
            scale = Class.forName("com.ascensionlib.AscensionRewards").getMethod("scaleItemQuantity", UUID.class, int.class, String.class);
        } catch (ReflectiveOperationException | LinkageError ex) {
            // An older library without the 777 bonus is not an error: item rewards are simply unscaled.
        }
        return scale;
    }
}
