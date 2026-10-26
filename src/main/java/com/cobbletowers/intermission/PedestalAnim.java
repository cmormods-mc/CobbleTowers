package com.cobbletowers.intermission;

import com.cobbletowers.TowerLog;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/**
 * Steps of the pedestal animations (P55), each due at a server tick: a card growing out of the floor, a shockwave, a
 * flicker. One queue for all runs, ordered by due tick. A step holds ids and numbers, never entities or players, so it
 * is safe to run late or after the run is gone (it finds nothing and does nothing). Server thread only.
 */
final class PedestalAnim {

    private record Step(long due, long order, UUID runId, Consumer<MinecraftServer> action) {}

    private static final PriorityQueue<Step> QUEUE = new PriorityQueue<>(
            (a, b) -> a.due() != b.due() ? Long.compare(a.due(), b.due()) : Long.compare(a.order(), b.order()));
    private static long counter;

    private PedestalAnim() {}

    static void at(long due, UUID runId, Consumer<MinecraftServer> action) {
        QUEUE.add(new Step(due, counter++, runId, action));
    }

    static boolean idle() {
        return QUEUE.isEmpty();
    }

    static int pending() {
        return QUEUE.size();
    }

    /** Runs every step due by {@code now}, in order. One step's fault does not stop the others. */
    static void run(MinecraftServer server, long now) {
        List<Step> due = new ArrayList<>();
        while (!QUEUE.isEmpty() && QUEUE.peek().due() <= now) due.add(QUEUE.poll());
        for (Step step : due) {
            try {
                step.action().accept(server);
            } catch (RuntimeException ex) {
                TowerLog.error("A pedestal animation step failed", ex);
            }
        }
    }

    /** Drops a run's waiting steps, for when its pedestals come down. */
    static void cancel(UUID runId) {
        for (Iterator<Step> it = QUEUE.iterator(); it.hasNext();) {
            if (it.next().runId().equals(runId)) it.remove();
        }
    }

    static void clear() {
        QUEUE.clear();
    }
}
