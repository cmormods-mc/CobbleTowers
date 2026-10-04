package com.cobbletowers.persistence;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * How a run is being played, beyond which tower (P32): the playlist whose house rules apply, and the trial it is an attempt at.
 * Fixed when the run is created and never changed, so every rule that depends on it reads the same answer for the whole run.
 *
 * <p>An absent block means an ordinary run, which is what every run written before P32 was.
 *
 * @param playlist   the playlist (Monotype, Hardcore...), empty for Standard
 * @param trial      the trial instance this run is an attempt at, such as {@code daily:2026-10-05}, empty for an ordinary run
 * @param floorLimit the floor a trial ends on (0 for no limit): clearing it completes the run
 * @param scored     whether this attempt is the player's one scored attempt at the trial (later ones are practice)
 * @param enemyLevelLock every enemy is exactly this level (0 for no lock): a trial fixes it so results compare
 */
public record RunOptions(Optional<ResourceLocation> playlist, Optional<String> trial, int floorLimit, boolean scored,
                         int enemyLevelLock) {

    public static final RunOptions NONE = new RunOptions(Optional.empty(), Optional.empty(), 0, false, 0);

    /** Without a level lock, which is every run that is not a trial. */
    public RunOptions(Optional<ResourceLocation> playlist, Optional<String> trial, int floorLimit, boolean scored) {
        this(playlist, trial, floorLimit, scored, 0);
    }

    public RunOptions {
        Objects.requireNonNull(playlist, "playlist");
        Objects.requireNonNull(trial, "trial");
        if (floorLimit < 0) throw new IllegalArgumentException("floorLimit must be >= 0, got " + floorLimit);
        if (enemyLevelLock < 0 || enemyLevelLock > 100) throw new IllegalArgumentException("enemyLevelLock must be 0..100, got " + enemyLevelLock);
    }

    /** An ordinary run under a playlist. */
    public static RunOptions of(Optional<ResourceLocation> playlist) {
        return new RunOptions(playlist, Optional.empty(), 0, false, 0);
    }

    public boolean isTrial() {
        return trial.isPresent();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        playlist.ifPresent(id -> tag.putString("playlist", id.toString()));
        trial.ifPresent(key -> tag.putString("trial", key));
        tag.putInt("floor_limit", floorLimit);
        tag.putBoolean("scored", scored);
        tag.putInt("enemy_level_lock", enemyLevelLock);
        return tag;
    }

    public static RunOptions fromTag(CompoundTag tag) {
        Optional<ResourceLocation> playlist = tag.contains("playlist")
                ? Optional.ofNullable(ResourceLocation.tryParse(tag.getString("playlist"))) : Optional.empty();
        Optional<String> trial = tag.contains("trial") ? Optional.of(tag.getString("trial")) : Optional.empty();
        return new RunOptions(playlist, trial, Math.max(0, tag.getInt("floor_limit")), tag.getBoolean("scored"),
                Math.max(0, Math.min(100, tag.getInt("enemy_level_lock"))));
    }
}
