package com.cobbletowers.instance;

import java.util.LinkedHashSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Server-thread queue: a load callback only records work; a later tick performs it. */
final class DeferredRemovalQueue<T> {
    private final LinkedHashSet<T> pending = new LinkedHashSet<>();

    void add(T entity) {
        pending.add(entity);
    }

    void drain(int budget, Predicate<T> eligible, Consumer<T> remove) {
        // Work added by a removal callback waits for the next tick too.
        int count = Math.min(budget, pending.size());
        for (int i = 0; i < count && !pending.isEmpty(); i++) {
            var iterator = pending.iterator();
            T entity = iterator.next();
            iterator.remove();
            // No live queue iterator is used after a callback can mutate the queue.
            if (eligible.test(entity)) remove.accept(entity);
        }
    }

    void clear() {
        pending.clear();
    }
}
