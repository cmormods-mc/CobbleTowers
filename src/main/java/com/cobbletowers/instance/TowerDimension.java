package com.cobbletowers.instance;

import com.cobbletowers.TowerLog;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The one dimension every cell lives in (TDS #6), declared as datapack data in this jar ({@code
 * data/cobbletowers/dimension_type/tower.json}, {@code dimension/tower.json}). The id is effectively permanent:
 * removing a used dimension from a world is not clean.
 */
public final class TowerDimension {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tower");
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ID);

    private TowerDimension() {}

    /**
     * The tower level, or null if the datapack entry is missing or broken; callers treat that as "no instances can be
     * allocated".
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
