package com.cobbletowers;

import com.cobbletowers.api.boss.TowerBossProvider;
import com.cobbletowers.integration.cobbleraids.CobbleRaidsBossAdapter;
import com.cobbletowers.runtime.TowerServerRuntime;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common server entrypoint for CobbleTowers. */
public final class CobbleTowers implements ModInitializer {
    public static final String MOD_ID = "cobbletowers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final CobbleRaidsBossAdapter BOSS_PROVIDER = new CobbleRaidsBossAdapter();
    private static final TowerServerRuntime SERVER_RUNTIME = new TowerServerRuntime();

    private CobbleTowers() {}

    public static TowerBossProvider bosses() {
        return BOSS_PROVIDER;
    }

    public static TowerServerRuntime runtime() {
        return SERVER_RUNTIME;
    }

    @Override
    public void onInitialize() {
        // Persistence is loaded only after the Minecraft server has completed startup and the
        // Overworld's global DimensionDataStorage is available. No world mutation occurs here.
        ServerLifecycleEvents.SERVER_STARTED.register(SERVER_RUNTIME::start);

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SERVER_RUNTIME.stop();

            int dropped = BOSS_PROVIDER.clear();
            if (dropped > 0) {
                LOGGER.warn("Server stopped with {} Tower boss encounter handle(s) still tracked.", dropped);
            }
        });

        LOGGER.info("CobbleTowers initialized with CobbleRaids public API integration and persistent Tower runtime.");
    }
}
