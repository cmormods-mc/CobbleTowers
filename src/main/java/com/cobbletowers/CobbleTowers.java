package com.cobbletowers;

import com.cobbletowers.command.DefinitionsCommand;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.spike.SpikeCommand;
import com.cobbletowers.spike.SpikeEncounters;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackType;

/** Entrypoint: tower definitions, the read-only debug commands, and the dev-only battle spike. */
public final class CobbleTowers implements ModInitializer {

    public static final String MOD_ID = "cobbletowers";

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");

        // Guarded: a failure to register a command must not take the rest of the server's command
        // tree down with it.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            try {
                DefinitionsCommand.register(dispatcher);
                SpikeCommand.register(dispatcher);
            } catch (RuntimeException ex) {
                TowerLog.error("Could not register the CobbleTowers commands", ex);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SpikeEncounters.onServerStopped());
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new TowerDefinitionRegistry());

        TowerLog.info("CobbleTowers {} loaded", version);
    }
}
