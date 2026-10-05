package com.cobbletowers.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/**
 * A boss victory's AscensionLib payout that has not been confirmed yet, kept on disk until it is.
 *
 * <p>The library pays once per (encounter, player, reward kind), so repeating a settlement is always safe; what it
 * cannot do is remember a settlement it never received (library disabled, no running world, a crash between the boss
 * ending and the call). This record is that memory. It holds the arguments of the call, never an amount: the library
 * recomputes the same amounts from the same encounter and player, which is also what lets a retry cover only the
 * players still owed.
 *
 * @param players      who is still owed; shrinks as the library confirms each one
 * @param createdAt    epoch millis, for expiry
 * @param attempts     tries so far, for the log
 */
public record PendingLibSettlement(UUID encounterId, Kind kind, String outcome, int fromFloor, int bossFloor,
                                   boolean keenEyeFloor, List<UUID> players, long createdAt, int attempts) {

    public enum Kind { MILESTONE, SCOUTER_DROPS }

    /** How long an unpaid settlement is kept trying before it is given up on and logged. */
    public static final long EXPIRY_MILLIS = 7L * 24 * 60 * 60 * 1000;

    public PendingLibSettlement {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(outcome, "outcome");
        players = List.copyOf(players);
    }

    /** The statuses that mean "not paid yet, try again": the library was unavailable, or the wallet refused. */
    private static boolean retryable(String status) {
        return "DISABLED".equals(status) || "REFUSED".equals(status);
    }

    /**
     * What is left to settle after one call.
     *
     * @param statuses the library's answer, per player, or null when the call itself failed (nothing is confirmed).
     *                 A player absent from the answer is finished: the Scouter roll lists only those who rolled a
     *                 drop. {@code GRANTED}, {@code ALREADY_GRANTED}, {@code NOT_PAID} and {@code CONFLICT} are
     *                 final; {@code DISABLED} and {@code REFUSED} are tried again.
     * @return the settlement with only the still-owed players, or empty when nobody is
     */
    public Optional<PendingLibSettlement> afterAttempt(Map<?, ?> statuses) {
        List<UUID> remaining = new ArrayList<>();
        for (UUID player : players) {
            if (statuses == null || retryable(String.valueOf(statuses.get(player)))) remaining.add(player);
        }
        if (remaining.isEmpty()) return Optional.empty();
        return Optional.of(new PendingLibSettlement(encounterId, kind, outcome, fromFloor, bossFloor, keenEyeFloor,
                remaining, createdAt, attempts + 1));
    }

    public boolean expired(long now) {
        return now - createdAt > EXPIRY_MILLIS;
    }

    /** Identifies the settlement: one encounter settles each kind once. */
    public String key() {
        return encounterId + "|" + kind;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("encounter", encounterId);
        tag.putString("kind", kind.name());
        tag.putString("outcome", outcome);
        tag.putInt("from", fromFloor);
        tag.putInt("boss", bossFloor);
        tag.putBoolean("keen_eye", keenEyeFloor);
        ListTag list = new ListTag();
        for (UUID player : players) list.add(NbtUtils.createUUID(player));
        tag.put("players", list);
        tag.putLong("created_at", createdAt);
        tag.putInt("attempts", attempts);
        return tag;
    }

    public static PendingLibSettlement fromTag(CompoundTag tag) {
        List<UUID> players = new ArrayList<>();
        for (Tag entry : tag.getList("players", Tag.TAG_INT_ARRAY)) players.add(NbtUtils.loadUUID(entry));
        return new PendingLibSettlement(tag.getUUID("encounter"), Kind.valueOf(tag.getString("kind")),
                tag.getString("outcome"), tag.getInt("from"), tag.getInt("boss"), tag.getBoolean("keen_eye"), players,
                tag.getLong("created_at"), tag.getInt("attempts"));
    }
}
