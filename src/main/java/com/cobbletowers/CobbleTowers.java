package com.cobbletowers;

import com.cobbletowers.spike.SpikeCommand;
import com.cobbletowers.spike.SpikeEncounters;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Entrypoint: the dev-only battle spike command, and clearing its state when the server stops. */
public final class CobbleTowers implements ModInitializer {

    public static final String MOD_ID = "cobbletowers";

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");

        // Guarded: a failure to register a dev command must not take the rest of the server's
        // command tree down with it.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            try {
                SpikeCommand.register(dispatcher);
            } catch (RuntimeException ex) {
                TowerLog.error("Could not register the CobbleTowers commands", ex);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SpikeEncounters.onServerStopped());

        TowerLog.info("CobbleTowers {} loaded", version);
    }
}
