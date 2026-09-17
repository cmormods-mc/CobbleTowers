package com.cobbletowers.persistence;

import com.cobbletowers.api.tower.RunState;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/**
 * The last point a run was committed at: the idempotency key it was committed under, and the state
 * it was in.
 *
 * <p>Both or neither. A key alone says a commit happened but not what to come back to, which is the
 * one thing recovery needs; a state alone cannot be recognised as already applied when the same move
 * arrives twice. Keeping them in one record means they cannot be written apart.
 *
 * @param key   from {@code Transition.keyTemplate}, with the run and floor filled in
 * @param state the run's state once that move had been applied -- where a resume returns to
 */
public record RunCheckpoint(String key, RunState state) {

    public RunCheckpoint {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(state, "state");
        if (key.isBlank()) throw new IllegalArgumentException("a checkpoint key must not be blank");
        if (key.contains("{")) throw new IllegalArgumentException("checkpoint key left a placeholder unfilled: " + key);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", key);
        tag.putString("state", state.name().toLowerCase(Locale.ROOT));
        return tag;
    }

    public static RunCheckpoint fromTag(CompoundTag tag) {
        String key = tag.getString("key");
        String raw = tag.getString("state");
        try {
            return new RunCheckpoint(key, RunState.valueOf(raw.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("checkpoint has unknown state '" + raw + "'");
        }
    }
}
