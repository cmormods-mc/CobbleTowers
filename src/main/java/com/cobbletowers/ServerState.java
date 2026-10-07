package com.cobbletowers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Clears state held in static fields when a server stops: an integrated client keeps the JVM across worlds, so
 * anything left would be read back against the next one. The class that owns the state registers its clear from a
 * static initializer, so the clear sits beside the state.
 */
public final class ServerState {

    private static final List<Consumer<MinecraftServer>> CLEARS = new CopyOnWriteArrayList<>();

    private ServerState() {}

    public static void onStop(Consumer<MinecraftServer> clear) {
        CLEARS.add(clear);
    }

    public static void onStop(Runnable clear) {
        CLEARS.add(server -> clear.run());
    }

    /** Hooks the registered clears to server shutdown. Called once at startup. */
    public static void install() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (Consumer<MinecraftServer> clear : CLEARS) {
                try {
                    clear.accept(server);
                } catch (RuntimeException ex) {
                    TowerLog.error("Clearing per-server tower state failed", ex);
                }
            }
        });
    }
}
