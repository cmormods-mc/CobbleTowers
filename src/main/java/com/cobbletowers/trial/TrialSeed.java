package com.cobbletowers.trial;

import java.nio.charset.StandardCharsets;

/**
 * A stable 64-bit seed from a string (P32): FNV-1a over UTF-8 finished with SplitMix64, not {@code String.hashCode},
 * so a trial id gives the same seed on every server and Java version.
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
