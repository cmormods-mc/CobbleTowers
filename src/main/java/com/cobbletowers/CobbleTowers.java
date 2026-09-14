package com.cobbletowers;

import com.cobbletowers.api.boss.TowerBossProvider;
import com.cobbletowers.integration.cobbleraids.CobbleRaidsBossAdapter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common server entrypoint for CobbleTowers. */
public final class CobbleTowers implements ModInitializer {
    public static final String MOD_ID = "cobbletowers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final CobbleRaidsBossAdapter BOSS_PROVIDER = new CobbleRaidsBossAdapter();

    private CobbleTowers() {}

    public static TowerBossProvider bosses() {
        return BOSS_PROVIDER;
    }

    @Override
    public void onInitialize() {
        // No tick callback is registered here. The adapter is event/call driven and owns only a
        // small active-handle map while boss encounters actually exist.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            int dropped = BOSS_PROVIDER.clear();
            if (dropped > 0) {
                LOGGER.warn("Server stopped with {} Tower boss encounter handle(s) still tracked.", dropped);
            }
        });
        LOGGER.info("CobbleTowers initialized with CobbleRaids public API integration.");
    }
}
