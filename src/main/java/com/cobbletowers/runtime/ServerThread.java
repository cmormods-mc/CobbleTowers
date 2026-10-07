package com.cobbletowers.runtime;

import net.minecraft.server.MinecraftServer;

/**
 * Getting onto the server thread, the only thread tower state may be touched from (plain maps, no locking). The
 * exception is library callbacks such as Fabric's {@code DISCONNECT}, which runs on a Netty thread.
 */
public final class ServerThread {

    private ServerThread() {}

    /**
     * Runs {@code task} on the server thread: straight away if already there, otherwise queued. Fabric fires the
     * disconnect event before vanilla removes the player, so a queued task still sees them listed.
     */
    public static void run(MinecraftServer server, Runnable task) {
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
    }
}
