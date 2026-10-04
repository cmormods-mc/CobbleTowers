package com.cobbletowers.events;

import com.cobbletowers.TowerLog;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The in-process stream of {@link TowerEvent}s (P32c). Emitting is cheap and synchronous, on the server thread that caused the
 * event; a subscriber that throws is logged and skipped, so a bug in a listener can never disturb a run.
 */
public final class TowerEvents {

    private static final List<Consumer<TowerEvent>> SUBSCRIBERS = new CopyOnWriteArrayList<>();

    private TowerEvents() {}

    public static void subscribe(Consumer<TowerEvent> subscriber) {
        SUBSCRIBERS.add(subscriber);
    }

    public static void emit(TowerEvent event) {
        for (Consumer<TowerEvent> subscriber : SUBSCRIBERS) {
            try {
                subscriber.accept(event);
            } catch (RuntimeException ex) {
                TowerLog.error("A tower event subscriber failed on {}", event.getClass().getSimpleName(), ex);
            }
        }
    }

    /** For tests: forget every subscriber. */
    public static void clear() {
        SUBSCRIBERS.clear();
    }
}
