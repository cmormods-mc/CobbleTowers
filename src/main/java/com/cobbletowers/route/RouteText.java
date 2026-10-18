package com.cobbletowers.route;

import com.cobbletowers.intermission.IntermissionEvents.Room;

/** Words for the route (P50). Pure. */
public final class RouteText {

    private RouteText() {}

    /** A room as a player reads it: "Mystic Pool" for the pool, the capitalised name otherwise. */
    public static String roomName(Room room) {
        return switch (room) {
            case POOL -> "Mystic Pool";
            case ECHO_DUEL -> "Echo Duel";
            default -> room.name().charAt(0) + room.name().substring(1).toLowerCase(java.util.Locale.ROOT);
        };
    }
}
