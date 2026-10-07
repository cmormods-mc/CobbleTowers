package com.cobbletowers.instance;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Admission control for the tower's heavy work (docs/design/cell-allocation-async.md, option A): pasting a floor into a cell and clearing one
 * each cost 0.3 to 0.9 s of one server tick, and a burst of them backs chunk saves up until the watchdog fires. At most {@link #MAX_PER_WINDOW}
 * heavy operations are admitted in any {@link #WINDOW_MILLIS}; whoever is refused can wait, and does:
 * <ul>
 *   <li>a lobby whose countdown ended starts a second later (and says why);</li>
 *   <li>a finished run's cell is released by the once-a-second exit sweep instead of at once;</li>
 *   <li>the warm pool builds one cell at a time, off the tick that started a run.</li>
 * </ul>
 * Work that cannot wait (a milestone arena rebuilt between floors) is {@link #note noted}, so everything else backs off around it.
 * Server thread only; time is passed in so the rule is a unit test.
 */
public final class HeavyWork {

    public static final long WINDOW_MILLIS = 1_200L;
    public static final int MAX_PER_WINDOW = 1;

    private static final Deque<Long> RECENT = new ArrayDeque<>();
    private static long refused;

    private HeavyWork() {}

    /** Takes a slot if one is free. False means: try again later. */
    public static boolean tryAcquire(long now) {
        purge(now);
        if (RECENT.size() >= MAX_PER_WINDOW) {
            refused++;
            return false;
        }
        RECENT.addLast(now);
        return true;
    }

    /** Whether a slot is free right now, without taking it. */
    public static boolean free(long now) {
        purge(now);
        return RECENT.size() < MAX_PER_WINDOW;
    }

    /** Records heavy work that happened without asking (it could not wait), so the next request backs off. */
    public static void note(long now) {
        purge(now);
        RECENT.addLast(now);
    }

    /** How many requests have been refused since the server started: a diagnostic, never a rule. */
    public static long refusedCount() {
        return refused;
    }

    private static void purge(long now) {
        // A clock that went backwards must not leave a slot taken for ever.
        if (!RECENT.isEmpty() && RECENT.peekFirst() > now) RECENT.clear();
        while (!RECENT.isEmpty() && now - RECENT.peekFirst() >= WINDOW_MILLIS) RECENT.removeFirst();
    }

    /** Server stop, and a test seam. */
    public static void reset() {
        RECENT.clear();
        refused = 0;
    }
}
