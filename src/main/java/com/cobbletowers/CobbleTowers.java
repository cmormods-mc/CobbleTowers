package com.cobbletowers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Entrypoint. Registers nothing yet beyond announcing itself; the battle spike adds its command. */
public final class CobbleTowers implements ModInitializer {

    public static final String MOD_ID = "cobbletowers";

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        TowerLog.info("CobbleTowers {} loaded", version);
    }
}
