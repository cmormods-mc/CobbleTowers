package com.cobbletowers.trial;

import java.nio.charset.StandardCharsets;

/**
 * A stable 64-bit seed from a string (P32). Not {@code String.hashCode} (32 bits, and a different JVM could in principle
 * differ): FNV-1a over the UTF-8 bytes, finished with a SplitMix64 mix, so the same trial id gives the same seed on every server
 * and every Java version, which is the whole point of a shared trial.
 */
public final class TrialSeed {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private TrialSeed() {}

    public static long of(String key) {
        long hash = FNV_OFFSET;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xffL);
            hash *= FNV_PRIME;
        }
        hash += 0x9e3779b97f4a7c15L;
        hash = (hash ^ (hash >>> 30)) * 0xbf58476d1ce4e5b9L;
        hash = (hash ^ (hash >>> 27)) * 0x94d049bb133111ebL;
        return hash ^ (hash >>> 31);
    }
}
