package com.cobbletowers.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bounds how fast one player's screen actions are taken: a token bucket, loose enough that no hand can reach it and
 * tight enough that a client looping on the channel cannot make the server rebuild screens all tick. Server thread only.
 */
final class ActionRate {

    enum Verdict { ALLOW, DROP, DROP_AND_SAY }

    /** Actions in a burst, then the sustained rate per second. */
    static final double BURST = 30;
    static final double PER_SECOND = 20;
    private static final long SAY_EVERY_MILLIS = 30_000;

    private static final class Bucket {
        double tokens = BURST;
        long at;
        long saidAt = Long.MIN_VALUE / 2;
    }

    private final Map<UUID, Bucket> buckets = new HashMap<>();

    Verdict check(UUID player, long now) {
        Bucket bucket = buckets.computeIfAbsent(player, id -> {
            Bucket fresh = new Bucket();
            fresh.at = now;
            return fresh;
        });
        bucket.tokens = Math.min(BURST, bucket.tokens + Math.max(0, now - bucket.at) * PER_SECOND / 1000.0);
        if (now > bucket.at) bucket.at = now;
        if (bucket.tokens >= 1) {
            bucket.tokens -= 1;
            return Verdict.ALLOW;
        }
        if (now - bucket.saidAt < SAY_EVERY_MILLIS) return Verdict.DROP;
        bucket.saidAt = now;
        return Verdict.DROP_AND_SAY;
    }

    void forget(UUID player) {
        buckets.remove(player);
    }

    void clear() {
        buckets.clear();
    }

    int size() {
        return buckets.size();
    }
}
