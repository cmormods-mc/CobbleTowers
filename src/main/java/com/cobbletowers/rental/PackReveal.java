package com.cobbletowers.rental;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The timing and feel of a pack opening (P33), as numbers: how long things take, which card turns over when, how hard the table
 * shakes for a given rarity. Pure, so it is unit-tested without a client; the screen only asks and draws.
 *
 * <p>The rarity vocabulary and its colours follow CobblemonCards' (common, uncommon, rare, epic, legendary, mythic), so a card means
 * the same thing to a player who knows that mod.
 */
public final class PackReveal {

    /** The pack shaking before it tears open. */
    public static final long TEAR_MS = 900;
    /** The pause between one card turning over and the next. */
    public static final long CARD_GAP_MS = 450;
    /** How long one card takes to turn over. */
    public static final long FLIP_MS = 420;

    private static final List<String> ORDER = List.of("common", "uncommon", "rare", "epic", "legendary", "mythic");

    private PackReveal() {}

    /** 0 (common) to 5 (mythic); anything unknown counts as common, so a newer server never breaks an older client. */
    public static int rank(String rarity) {
        int index = ORDER.indexOf(rarity == null ? "" : rarity);
        return Math.max(index, 0);
    }

    /** The frame and glow colour of a rarity, opaque ARGB. */
    public static int color(String rarity) {
        return switch (rank(rarity)) {
            case 1 -> 0xFF4CAF50;
            case 2 -> 0xFF3F8CFF;
            case 3 -> 0xFFA855F7;
            case 4 -> 0xFFF5B301;
            case 5 -> 0xFFFF4DA6;
            default -> 0xFF9AA0A6;
        };
    }

    /** The colour of a mythic card cycles through the hues, as a holographic card would; every other rarity is steady. */
    public static int colorAt(String rarity, long ms) {
        if (rank(rarity) < 5) return color(rarity);
        float hue = (ms % 3000) / 3000f;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.55f, 1f) & 0xFFFFFF);
    }

    /** The order the cards turn over in: lowest rarity first, so the best card is last. Ties keep the pack order. */
    public static List<Integer> revealOrder(List<String> rarities) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < rarities.size(); i++) order.add(i);
        order.sort(Comparator.comparingInt(i -> rank(rarities.get(i))));
        return order;
    }

    /** When the card in {@code position} of {@link #revealOrder} starts to turn, counted from the moment the pack finished tearing. */
    public static long flipStart(int position) {
        return position * CARD_GAP_MS;
    }

    /** How far a card has turned over {@code ms} after it started: 0 is face down, 1 is face up, eased at both ends. */
    public static float flip(long ms) {
        if (ms <= 0) return 0f;
        if (ms >= FLIP_MS) return 1f;
        float t = (float) ms / FLIP_MS;
        return t * t * (3f - 2f * t);
    }

    /** When everything is face up, counted from the end of the tear. */
    public static long totalMs(int cards) {
        return cards <= 0 ? 0 : flipStart(cards - 1) + FLIP_MS;
    }

    /** The widest the table shakes when a card of this rarity turns over, in pixels: none below epic. */
    public static float shakeAmplitude(String rarity) {
        return switch (rank(rarity)) {
            case 3 -> 2f;
            case 4 -> 4f;
            case 5 -> 6f;
            default -> 0f;
        };
    }

    /** The shake {@code ms} after it began, fading to nothing over 600 ms. */
    public static float shake(String rarity, long ms) {
        float amplitude = shakeAmplitude(rarity);
        if (amplitude == 0f || ms < 0 || ms > 600) return 0f;
        float fade = 1f - ms / 600f;
        return (float) Math.sin(ms * 0.09) * amplitude * fade;
    }

    /** How many sparkles burst from a card of this rarity as it lands. */
    public static int particleCount(String rarity) {
        return switch (rank(rarity)) {
            case 2 -> 6;
            case 3 -> 14;
            case 4 -> 26;
            case 5 -> 40;
            default -> 0;
        };
    }

    /** The shaking of the sealed pack as it tears, in pixels, growing then stopping: a held breath. */
    public static float tearShake(long ms, int topRank) {
        if (ms < 0 || ms > TEAR_MS) return 0f;
        float grow = Math.min(1f, ms / (float) TEAR_MS);
        float amplitude = 1.5f + grow * (2f + topRank);
        return (float) Math.sin(ms * 0.11) * amplitude;
    }
}
