package com.cobbletowers.halloween.vault;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The vault's deterministic state machine: CLOSED -> ANTICIPATING -> UNLOCKING -> OPENING -> OPEN -> CLOSING -> CLOSED.
 *
 * <p>Advanced only by {@link #tick()}, one call per server tick. {@link #start} works from CLOSED only, {@link #close} from OPEN only, and
 * {@link #reset} from every state. The outcome (treat or trick) is stored but never read by the pose: the pose timeline up to and including
 * OPENING is the same for both, and the events manifest holds no outcome-specific event before OPEN. Pure; no Minecraft types.
 */
public final class VaultMachine {

    public enum State { CLOSED, ANTICIPATING, UNLOCKING, OPENING, OPEN, CLOSING }

    public enum Outcome { TREAT, TRICK }

    /** What one tick produced: the state the tick belongs to, the tick within it, the pose to show and the events that fire now. */
    public record Frame(State state, int stateTick, VaultPose pose, List<VaultEvent> events) {}

    private record Spec(String sequence, State next, int ticks) {}

    private final VaultTimeline timeline;
    private final List<VaultEvent> events;
    private final Map<State, Spec> specs = new EnumMap<>(State.class);
    private State state = State.CLOSED;
    private int tick;
    private Outcome outcome;

    public VaultMachine(VaultTimeline timeline, JsonObject animationManifest, List<VaultEvent> events) {
        this.timeline = timeline;
        this.events = List.copyOf(events);
        JsonObject states = animationManifest.getAsJsonObject("state_machine").getAsJsonObject("states");
        for (State s : State.values()) {
            JsonObject o = states.getAsJsonObject(s.name());
            specs.put(s, new Spec(o.get("sequence").getAsString(), o.get("next").isJsonNull() ? null : State.valueOf(o.get("next").getAsString()),
                    o.get("ticks").getAsInt()));
        }
    }

    public State state() {
        return state;
    }

    public Outcome outcome() {
        return outcome;
    }

    /** Ticks since the current state was entered (the value the next {@link #tick()} will report). */
    public int stateTick() {
        return tick;
    }

    public boolean start(Outcome outcome) {
        if (state != State.CLOSED || outcome == null) return false;
        this.state = State.ANTICIPATING;
        this.tick = 0;
        this.outcome = outcome;
        return true;
    }

    public boolean close() {
        if (state != State.OPEN) return false;
        state = State.CLOSING;
        tick = 0;
        return true;
    }

    /** Any state to CLOSED at rest. */
    public void reset() {
        state = State.CLOSED;
        tick = 0;
        outcome = null;
    }

    /** Advances one server tick. */
    public Frame tick() {
        Spec spec = specs.get(state);
        VaultPose pose = timeline.sample(spec.sequence(), tick);
        List<VaultEvent> fired = new ArrayList<>(2);
        for (VaultEvent e : events) {
            if (e.state() == state && e.tick() == tick && e.appliesTo(outcome)) fired.add(e);
        }
        Frame frame = new Frame(state, tick, pose, List.copyOf(fired));
        tick++;
        if (spec.next() != null && tick >= spec.ticks()) {
            state = spec.next();
            tick = 0;
            if (state == State.CLOSED) outcome = null;
        }
        return frame;
    }
}
