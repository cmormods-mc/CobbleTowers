package com.cobbletowers.armor.flare;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What a back attachment's flare emits and when (the particles that stand in for animation, since Armor Model API draws static
 * models). Pure data and arithmetic with no game types, so a plain-JVM test can pin it; {@link ArmorFlareService} turns each
 * {@link Emit} into a real particle.
 *
 * <p>Positions are body-local blocks measured from the wearer's feet: {@code right} (their right hand), {@code up}, {@code back}
 * (behind them). A pass runs every {@value ArmorFlareService#PASS_TICKS} ticks and {@code pass} counts them; each effect has a
 * period in passes and a per-player {@code phase} so a crowd does not pulse in unison.
 */
public final class FlarePlan {

    public static final Set<String> SETS = Set.of("tideforge", "rootvale", "duskvale");

    /** One particle: its id (or a {@code block:} / {@code dust_transition} form the service understands), where, how it moves. */
    public record Emit(String particle, double right, double up, double back, double velRight, double velUp, double velBack,
                       int count, double spread) {}

    private FlarePlan() {}

    /** The set a chestplate item id belongs to ({@code cobbletowers:tideforge_chestplate} gives {@code tideforge}), or null. */
    public static String setOf(String namespace, String path) {
        if (!"cobbletowers".equals(namespace) || !path.endsWith("_chestplate")) return null;
        String set = path.substring(0, path.length() - "_chestplate".length());
        return SETS.contains(set) ? set : null;
    }

    /** A stable per-player offset, so effects are staggered between wearers without storing anything per player. */
    public static int phaseOf(java.util.UUID id) {
        return Math.floorMod(id.hashCode(), 24);
    }

    private static boolean every(long pass, int phase, int period) {
        return Math.floorMod(pass + phase, period) == 0;
    }

    public static List<Emit> at(String set, long pass, int phase, boolean underwater, boolean sprinting) {
        List<Emit> out = new ArrayList<>(4);
        switch (set) {
            case "tideforge" -> tideforge(out, pass, phase, underwater, sprinting);
            case "rootvale" -> rootvale(out, pass, phase, sprinting);
            case "duskvale" -> duskvale(out, pass, phase, sprinting);
            default -> {
            }
        }
        return out;
    }

    // Tideforge: the valves vent (bubbles under water, soft puffs on land), the gauge flickers, sprinting trails bubbles.
    private static void tideforge(List<Emit> out, long pass, int phase, boolean underwater, boolean sprinting) {
        int side = (int) (Math.floorMod(pass / 3, 2)) * 2 - 1;
        if (every(pass, phase, 3)) {
            out.add(new Emit(underwater ? "bubble" : "cloud", 0.18 * side, 1.62, 0.36, 0, underwater ? 0.07 : 0.035, 0.01, 0, 1));
        }
        if (every(pass, phase, 7)) {
            out.add(new Emit("bubble_pop", 0.18 * -side, 1.66, 0.34, 0, 0, 0, 1, 0.06));
        }
        if (every(pass, phase, 19)) {
            out.add(new Emit("electric_spark", 0, 1.12, 0.52, 0, 0, 0.02, 2, 0.05));   // the gauge flickers
        }
        if (sprinting && every(pass, phase, 2)) {
            out.add(new Emit(underwater ? "bubble" : "cloud", 0.0, 0.9, 0.5, 0, 0.02, 0.1, 0, 1));
        }
    }

    // Rootvale: leaves shake loose from the shield and fall, the boss seed sparkles.
    private static void rootvale(List<Emit> out, long pass, int phase, boolean sprinting) {
        if (every(pass, phase, 4)) {
            double[] xs = {-0.30, 0.22, -0.12, 0.32, 0.0, -0.26};
            double[] ys = {1.50, 1.30, 1.05, 0.90, 0.72, 1.20};
            int slot = (int) Math.floorMod(pass / 4 + phase, 6);
            out.add(new Emit("block:azalea_leaves", xs[slot], ys[slot], 0.45, 0, 0, 0.02, 0, 1));
        }
        if (every(pass, phase, 9)) {
            out.add(new Emit("happy_villager", 0, 1.08, 0.5, 0, 0, 0, 1, 0.12));
        }
        if (sprinting && every(pass, phase, 2)) {
            out.add(new Emit("block:azalea_leaves", 0.0, 1.1, 0.5, 0, 0, 0.12, 0, 1));
        }
    }

    // Duskvale: stars drift off the moon, lilac to pink dust trails from the tendril tips, a soul wisp rises now and then.
    private static void duskvale(List<Emit> out, long pass, int phase, boolean sprinting) {
        if (every(pass, phase, 5)) {
            double[] ang = {0.3, 1.0, 1.7, 2.4, 3.1, 3.8};
            int slot = (int) Math.floorMod(pass / 5 + phase, 6);
            double a = ang[slot];
            out.add(new Emit("end_rod", 0.50 * Math.cos(a), 1.80 + 0.50 * Math.sin(a), 0.40, 0, 0.012, 0, 0, 1));
        }
        if (every(pass, phase, 3)) {
            double[] xs = {-0.62, -0.34, 0.0, 0.34, 0.62};
            double[] ys = {0.55, 0.28, 0.12, 0.28, 0.55};
            int slot = (int) Math.floorMod(pass / 3 + phase, 5);
            out.add(new Emit("dust_transition", xs[slot], ys[slot], 0.40, 0, -0.01, 0.01, 0, 1));
        }
        if (every(pass, phase, 17)) {
            out.add(new Emit("soul", 0.0, 0.9, 0.42, 0, 0.03, 0.01, 0, 1));
        }
        if (sprinting && every(pass, phase, 2)) {
            out.add(new Emit("dust_transition", 0.0, 0.7, 0.5, 0, 0, 0.1, 0, 1));
        }
    }
}
