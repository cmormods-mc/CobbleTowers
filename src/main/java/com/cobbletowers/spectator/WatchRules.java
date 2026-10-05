package com.cobbletowers.spectator;

import java.util.Optional;

/**
 * Who may watch whom (P36e), as plain values so every refusal can be tested without a server.
 *
 * <p>A watcher is a player outside the run looking in: they ride a participant's camera in spectator mode and are sent home when the run ends.
 * It is not the loss-spectating of {@link SpectatorPresentation}, which belongs to a participant of the same run.
 */
public final class WatchRules {

    /** What the watcher and the player they asked for look like to the server right now. */
    public record Situation(boolean targetOnline, boolean targetIsSelf, boolean targetInLiveRun, boolean targetInTower,
                            boolean watcherInOwnRun, boolean watcherAlreadyWatching) {}

    private WatchRules() {}

    /** The sentence explaining why not, or empty when the watch may begin. */
    public static Optional<String> refusal(Situation s) {
        if (s.targetIsSelf()) return Optional.of("You cannot watch yourself.");
        if (s.watcherInOwnRun()) return Optional.of("You are in a run of your own. Finish it or cash out first.");
        if (!s.targetOnline()) return Optional.of("That player is not online.");
        if (!s.targetInLiveRun()) return Optional.of("That player is not in a tower run right now.");
        if (!s.targetInTower()) return Optional.of("That player is between floors; try again in a moment.");
        return Optional.empty();
    }

    /**
     * Whether an existing watch must end now: the run is over, or the player they were following is gone or has left the tower.
     */
    public static boolean mustEnd(boolean runLive, boolean targetOnline, boolean targetInTower) {
        return !runLive || !targetOnline || !targetInTower;
    }
}
