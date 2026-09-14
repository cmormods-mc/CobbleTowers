package com.cobbletowers;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common server entrypoint for CobbleTowers.
 *
 * <p>Phase 0 intentionally registers no tick callbacks or CobbleRaids implementation hooks. The
 * first gameplay registration will be added only after the CobbleRaids public integration contract
 * is available, so this module never needs to depend on raid/lifecycle/spawn implementation packages.
 */
public final class CobbleTowers implements ModInitializer {
    public static final String MOD_ID = "cobbletowers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("CobbleTowers initialized (architecture bootstrap).");
    }
}
