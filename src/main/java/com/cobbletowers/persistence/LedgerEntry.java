package com.cobbletowers.persistence;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * One thing a run earned, recorded without a value (the economy prices it, P9).
 * @param what the opponent's species, or the floor id for a cleared floor
 * @param byPlayer who earned it; a cleared floor is credited to the run
 */
public record LedgerEntry(Kind kind, int floorIndex, ResourceLocation what, UUID byPlayer, long at) {

    public enum Kind {
        OPPONENT_DEFEATED,
        /** The floor's CobbleRaids boss. Separate from an ordinary opponent so P9 can weigh it. */
        BOSS_DEFEATED,
        FLOOR_CLEARED,
        /**
         * A milestone floor was cleared (P21); {@code what} is the milestone's id. Priced from the table's milestone
         * section.
         */
        MILESTONE_CLEARED,
        /**
         * The run was lost, so everything above it is void. Marked rather than deleted, so P9 reads "grant nothing"
         * and an operator can still see what was earned.
         */
        POOL_FORFEITED
    }

    public LedgerEntry {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(what, "what");
        if (floorIndex < 1) throw new IllegalArgumentException("floorIndex must be >= 1, got " + floorIndex);
    }

    public static LedgerEntry bossDefeated(int floorIndex, ResourceLocation definition, long at) {
        return new LedgerEntry(Kind.BOSS_DEFEATED, floorIndex, definition, null, at);
    }

    /** Everything earned so far is void. Appended once, when the run is lost. */
    public static LedgerEntry forfeited(int floorIndex, ResourceLocation towerId, long at) {
        return new LedgerEntry(Kind.POOL_FORFEITED, floorIndex, towerId, null, at);
    }

    public static LedgerEntry opponentDefeated(int floorIndex, ResourceLocation species, UUID player, long at) {
        return new LedgerEntry(Kind.OPPONENT_DEFEATED, floorIndex, species, Objects.requireNonNull(player), at);
    }

    /** Clearing a milestone floor belongs to the run, so it carries no player. */
    public static LedgerEntry milestoneCleared(int floorIndex, ResourceLocation milestoneId, long at) {
        return new LedgerEntry(Kind.MILESTONE_CLEARED, floorIndex, milestoneId, null, at);
    }

    /** A cleared floor belongs to the run, so it carries no player. */
    public static LedgerEntry floorCleared(int floorIndex, ResourceLocation floorId, long at) {
        return new LedgerEntry(Kind.FLOOR_CLEARED, floorIndex, floorId, null, at);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", kind.name().toLowerCase(Locale.ROOT));
        tag.putInt("floor", floorIndex);
        tag.putString("what", what.toString());
        if (byPlayer != null) tag.putUUID("by", byPlayer);
        tag.putLong("at", at);
        return tag;
    }

    public static LedgerEntry fromTag(CompoundTag tag) {
        ResourceLocation what = ResourceLocation.tryParse(tag.getString("what"));
        if (what == null) throw new IllegalArgumentException("ledger entry names an invalid id: " + tag.getString("what"));
        String raw = tag.getString("kind");
        Kind kind;
        try {
            kind = Kind.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // Refused rather than defaulted: guessing would put an entry in the pool under the wrong
            // heading, and the pool is what a player is eventually paid from.
            throw new IllegalArgumentException("ledger entry has unknown kind '" + raw + "'");
        }
        return new LedgerEntry(kind, tag.getInt("floor"), what,
                tag.hasUUID("by") ? tag.getUUID("by") : null, tag.getLong("at"));
    }
}
