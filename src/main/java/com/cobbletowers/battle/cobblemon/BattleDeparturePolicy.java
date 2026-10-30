package com.cobbletowers.battle.cobblemon;

import java.util.function.BooleanSupplier;

/** An owned shared fight must handle one participant's departure before standalone termination. */
final class BattleDeparturePolicy {
    private BattleDeparturePolicy() {}

    static void depart(BooleanSupplier withdrawFromOwner, Runnable endStandalone) {
        // An owner failure must propagate: falling back would terminate everybody's shared battle.
        if (!withdrawFromOwner.getAsBoolean()) endStandalone.run();
    }
}
