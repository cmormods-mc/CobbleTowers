package com.cobbletowers;

import com.cobbletowers.command.DefinitionsCommand;
import com.cobbletowers.command.CellsCommand;
import com.cobbletowers.command.RunsCommand;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.instance.CellTickets;
import com.cobbletowers.instance.CellWarmPool;
import com.cobbletowers.instance.InstanceAllocator;
import com.cobbletowers.runtime.RunRecovery;
import com.cobbletowers.runtime.TowerRuns;
import com.cobbletowers.spike.SpikeCommand;
import com.cobbletowers.spike.SpikeEncounters;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackType;

/** Entrypoint: tower definitions, stored runs and their recovery, the debug commands, and the spike. */
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
                RunsCommand.register(dispatcher);
                CellsCommand.register(dispatcher);
                SpikeCommand.register(dispatcher);
            } catch (RuntimeException ex) {
                TowerLog.error("Could not register the CobbleTowers commands", ex);
            }
        });
        // Loading and recovery are separate steps, and separately guarded: a failure to park an
        // interrupted run must not also cost the index of the runs that loaded fine.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            long now = System.currentTimeMillis();
            try {
                int loaded = TowerRuns.load(server, now);
                // The cell index is rebuilt from the runs themselves, so there is no second file
                // that could disagree with them about who holds what.
                int leased = InstanceAllocator.rebuild(server, TowerRuns.all());
                if (loaded > 0) {
                    TowerLog.info("Loaded {} tower run(s) from disk, {} holding an instance cell.", loaded, leased);
                }
            } catch (RuntimeException ex) {
                TowerLog.error("Could not load stored tower runs", ex);
                return;
            }
            try {
                RunRecovery.recoverInterruptedRuns(server, now);
            } catch (RuntimeException ex) {
                TowerLog.error("Could not recover interrupted tower runs", ex);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SpikeEncounters.onServerStopped());
        // Per-server state on a class that is not per-server: an integrated client keeps this JVM
        // across worlds, so anything left indexed here would be read back against the next one.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TowerRuns.onServerStopped());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> InstanceAllocator.onServerStopped());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CellTickets.onServerStopped());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CellWarmPool.onServerStopped());
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new TowerDefinitionRegistry());

        TowerLog.info("CobbleTowers {} loaded", version);
    }
}
