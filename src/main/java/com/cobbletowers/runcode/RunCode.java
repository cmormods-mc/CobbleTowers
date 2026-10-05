package com.cobbletowers.runcode;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A run code (P35, roadmap D4): the tower, mode, starting Ascension and seed of a run, as one line of text a player can
 * paste to a friend. Entering it starts the same run: the same opponents, bosses and draft cards, because every draw in a
 * run derives from its seed (TDS #29).
 *
 * <p>Format {@code CT1-<tower>-<mode>-<ascension>-<seed>-<check>}: the tower and mode are the id paths (the namespace is
 * included only when it is not {@code cobbletowers}, written {@code namespace.path}), the mode is {@code std} for Standard,
 * the seed is unsigned base 36, and the check is one base-36 character so a mistyped code is refused rather than quietly
 * becoming a different run.
 *
 * <p>Pure: no server, no registry. Whether the tower and mode exist is the caller's question.
 */
public final class RunCode {

    public static final String PREFIX = "CT1";
    private static final String STANDARD = "std";
    private static final String DEFAULT_NAMESPACE = "cobbletowers";

    private RunCode() {}

    /** What a code says. */
    public record Decoded(ResourceLocation tower, Optional<ResourceLocation> playlist, int ascension, long seed) {}

    public static String encode(ResourceLocation tower, Optional<ResourceLocation> playlist, int ascension, long seed) {
        String body = PREFIX + "-" + part(tower) + "-" + playlist.map(RunCode::part).orElse(STANDARD) + "-"
                + Math.max(0, ascension) + "-" + Long.toUnsignedString(seed, 36);
        return body + "-" + check(body);
    }

    /** The decoded run, or empty for anything that is not a well-formed code with a matching check character. */
    public static Optional<Decoded> decode(String raw) {
        if (raw == null) return Optional.empty();
        String code = raw.trim().toLowerCase(Locale.ROOT);
        String[] parts = code.split("-");
        if (parts.length != 6 || !parts[0].equals(PREFIX.toLowerCase(Locale.ROOT))) return Optional.empty();
        String body = String.join("-", parts[0], parts[1], parts[2], parts[3], parts[4]);
        if (parts[5].length() != 1 || parts[5].charAt(0) != check(body)) return Optional.empty();
        try {
            ResourceLocation tower = id(parts[1]);
            ResourceLocation mode = parts[2].equals(STANDARD) ? null : id(parts[2]);
            if (mode == null && !parts[2].equals(STANDARD)) return Optional.empty();
            Optional<ResourceLocation> playlist = Optional.ofNullable(mode);
            int ascension = Integer.parseInt(parts[3]);
            long seed = Long.parseUnsignedLong(parts[4], 36);
            if (ascension < 0 || tower == null) return Optional.empty();
            return Optional.of(new Decoded(tower, playlist, ascension, seed));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    private static String part(ResourceLocation id) {
        return id.getNamespace().equals(DEFAULT_NAMESPACE) ? id.getPath() : id.getNamespace() + "." + id.getPath();
    }

    private static ResourceLocation id(String part) {
        int dot = part.indexOf('.');
        return dot < 0 ? ResourceLocation.tryParse(DEFAULT_NAMESPACE + ":" + part)
                : ResourceLocation.tryParse(part.substring(0, dot) + ":" + part.substring(dot + 1));
    }

    /** One base-36 character: a position-weighted sum (case-blind), so a swapped or mistyped character is caught. */
    static char check(String body) {
        int sum = 7;
        for (int i = 0; i < body.length(); i++) {
            sum = (sum * 31 + Character.toLowerCase(body.charAt(i)) * (i + 1)) % 36;
        }
        return Character.forDigit(sum, 36);
    }
}
