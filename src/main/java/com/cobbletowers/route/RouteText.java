package com.cobbletowers.route;

import com.cobbletowers.intermission.IntermissionEvents.Room;
import com.cobbletowers.persistence.RunModifierState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

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

    /** Stops shown in a one-line route before "+N more". */
    public static final int LINE_MAX = 10;

    /** A stop as a player reads it: "Battle" for a plain fight, the room's name otherwise; "Stop" when the kind was not kept. */
    public static String stopName(String kind) {
        if (kind == null || kind.isEmpty()) return "Stop";
        return RouteGraph.Kind.parse(kind).room().map(RouteText::roomName).orElse("Battle");
    }

    /**
     * The stops a run took, in order, each with the building it was fought in when a detour sent it elsewhere ("Battle (Rootvale)").
     * @param placeName a tower id as a short name
     */
    public static List<String> stops(RunModifierState state, Function<ResourceLocation, String> placeName) {
        List<String> out = new ArrayList<>();
        List<Integer> steps = state.routeSteps();
        for (int i = 0; i + 1 < steps.size(); i += 2) {
            String stop = stopName(state.routeKindAt(i / 2));
            int floor = steps.get(i);
            var arena = state.arenaAt(floor + 1);
            out.add(arena.isPresent() ? stop + " (" + placeName.apply(arena.get()) + ")" : stop);
        }
        return List.copyOf(out);
    }

    /** "Battle > Forge > Rest", cut to {@link #LINE_MAX} stops with "+N more"; empty when there was no route. */
    public static String line(List<String> stops) {
        if (stops.isEmpty()) return "";
        List<String> shown = stops.size() > LINE_MAX ? stops.subList(0, LINE_MAX) : stops;
        return String.join(" > ", shown) + (stops.size() > LINE_MAX ? " (+" + (stops.size() - LINE_MAX) + " more)" : "");
    }
}
