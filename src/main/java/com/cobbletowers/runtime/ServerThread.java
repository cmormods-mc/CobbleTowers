package com.cobbletowers.runtime;

import net.minecraft.server.MinecraftServer;

/**
 * Getting onto the server thread, the one thread tower state may be touched from.
 *
 * <p>Everything under {@code runtime}, {@code encounter}, {@code lobby}, {@code intermission} and the battle adapters
 * keeps its state in plain maps and in read-modify-write run records, with no locking, because it was written for one
 * thread. Most entry points are that thread already: ticks, commands, and (because their handlers hop) network
 * payloads. The exception is anything a library fires from the network layer. Fabric's
 * {@code ServerPlayConnectionEvents.DISCONNECT} runs on whichever Netty thread saw the socket close, so a party whose
 * connections fail together is handled by several threads at once -- observed live as four disconnects on four Netty
 * threads, three of them recorded and one lost to the others' writes.
 */
public final class ServerThread {

    private ServerThread() {}

    /**
     * Runs {@code task} on the server thread: straight away if the caller already is it, otherwise queued behind
     * whatever the server is already going to run.
     *
     * <p>That ordering matters for a disconnect. Fabric fires the event before vanilla queues the work that removes the
     * player from the player list, so a task queued from here runs while the player is still listed, exactly as the
     * handler would have seen them had it run inline.
     */
    public static void run(MinecraftServer server, Runnable task) {
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
    }
}
