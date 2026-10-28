package com.cobbletowers.halloween.vault;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** The floating reveal candy's motion (the {@code reveal} block of animation.json). Pure; mirrors vault_anim.reveal_state. */
public final class VaultReveal {

    /** Root-local translation in blocks, yaw in degrees (about the candy's own middle) and uniform scale at one instant. */
    public record State(double[] blocks, double yawDeg, double scale) {}

    private final int count, riseTicks, holdTicks, collectTicks, bobPeriod;
    private final double bobUnits;
    private final double[][] start;
    private final double[] rise, spin;
    private final int[] stagger;
    private final List<VaultTimeline.Key> scaleKeys = new ArrayList<>();

    public VaultReveal(JsonObject manifest) {
        JsonObject r = manifest.getAsJsonObject("reveal");
        count = r.get("count").getAsInt();
        riseTicks = r.get("rise_ticks").getAsInt();
        holdTicks = r.get("hold_ticks").getAsInt();
        collectTicks = r.get("collect_ticks").getAsInt();
        bobUnits = r.get("bob_units").getAsDouble();
        bobPeriod = r.get("bob_period_ticks").getAsInt();
        start = new double[count][];
        rise = new double[count];
        spin = new double[count];
        stagger = new int[count];
        JsonArray starts = r.getAsJsonArray("start_units");
        for (int i = 0; i < count; i++) {
            JsonArray s = starts.get(i).getAsJsonArray();
            start[i] = new double[] {s.get(0).getAsDouble(), s.get(1).getAsDouble(), s.get(2).getAsDouble()};
            rise[i] = r.getAsJsonArray("rise_units").get(i).getAsDouble();
            spin[i] = r.getAsJsonArray("spin_deg_per_tick").get(i).getAsDouble();
            stagger[i] = r.getAsJsonArray("stagger_ticks").get(i).getAsInt();
        }
        for (var k : r.getAsJsonArray("scale_keys")) {
            JsonArray a = k.getAsJsonArray();
            scaleKeys.add(new VaultTimeline.Key(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsString()));
        }
    }

    public int count() {
        return count;
    }

    public int collectTicks() {
        return collectTicks;
    }

    public int holdTicks() {
        return holdTicks;
    }

    /** State of candy {@code index}, {@code ticksSinceSpawn} ticks after the reveal started; null before its stagger delay. */
    public State state(int index, int ticksSinceSpawn) {
        int t = ticksSinceSpawn - stagger[index];
        if (t < 0) return null;
        double k = VaultTimeline.ease("out_cubic", Math.min(1.0, (double) t / riseTicks));
        double y = start[index][1] + rise[index] * k;
        if (t > riseTicks) y += bobUnits * Math.sin(2 * Math.PI * (t - riseTicks) / bobPeriod);
        double[] blocks = {(start[index][0] - 8) / 16, y / 16, (start[index][2] - 8) / 16};
        return new State(blocks, (spin[index] * t) % 360.0, VaultTimeline.sampleKeys(scaleKeys, t));
    }
}
