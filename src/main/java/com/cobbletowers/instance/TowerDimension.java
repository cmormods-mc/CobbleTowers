package com.cobbletowers.instance;

import com.cobbletowers.TowerLog;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The one controlled dimension every cell lives in (TDS #6).
 *
 * <p>Declared as datapack data in this mod's own jar --
 * {@code data/cobbletowers/dimension_type/tower.json} and {@code data/cobbletowers/dimension/tower.json}
 * -- rather than registered in code, which is how Minecraft has taken dimensions since 1.19.
 *
 * <p><b>The id is effectively permanent.</b> Adding a datapack dimension to an existing world is
 * supported; removing one from a world that has used it is not clean, because the world keeps
 * references to a level that no longer exists. So this string is not something to rename later.
 */
public final class TowerDimension {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tower");
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ID);

    private TowerDimension() {}

    /**
     * The tower level, or null when it is missing.
     *
     * <p>Null is possible in exactly one situation worth handling: someone removed or broke the
     * datapack entry. Everything here treats that as "no instances can be allocated" rather than
     * throwing, so a server with a damaged datapack still starts and still says why.
     */
    public static ServerLevel level(MinecraftServer server) {
        ServerLevel level = server.getLevel(LEVEL);
        if (level == null) {
            TowerLog.error("The tower dimension {} is not loaded; no instance can be allocated."
                    + " Is the mod's datapack intact?", ID);
        }
        return level;
    }

    public static boolean isLoaded(MinecraftServer server) {
        return server.getLevel(LEVEL) != null;
    }
}
