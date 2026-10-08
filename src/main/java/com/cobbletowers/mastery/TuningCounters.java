package com.cobbletowers.mastery;

import com.cobbletowers.persistence.TowerCounterStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.server.MinecraftServer;

/**
 * Where the mod bumps the balance tallies and where the report reads them back. Keys are {@code group.name}; the
 * report prints each group, and for the "offered/taken" groups the share taken. Never throws: a tally must not disturb
 * the thing it counts.
 */
public final class TuningCounters {

    private TuningCounters() {}

    public static void bump(MinecraftServer server, String key) {
        add(server, key, 1);
    }

    public static void add(MinecraftServer server, String key, long amount) {
        try {
            TowerCounterStore.get(server).add(key, amount);
        } catch (RuntimeException ex) {
            // Telemetry only.
        }
    }

    /** Report lines for every tally. Pure, so it is tested without a server. */
    public static List<String> lines(Map<String, Long> counts) {
        List<String> out = new ArrayList<>();
        if (counts.isEmpty()) {
            out.add("Counters: nothing recorded yet.");
            return out;
        }
        Map<String, Map<String, Long>> groups = new TreeMap<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            int dot = entry.getKey().indexOf('.');
            String group = dot < 0 ? entry.getKey() : entry.getKey().substring(0, dot);
            String name = dot < 0 ? "" : entry.getKey().substring(dot + 1);
            groups.computeIfAbsent(group, ignored -> new TreeMap<>()).put(name, entry.getValue());
        }
        out.add("Counters:");
        for (Map.Entry<String, Map<String, Long>> group : groups.entrySet()) {
            out.add("  " + group.getKey());
            for (Map.Entry<String, Long> entry : group.getValue().entrySet()) {
                out.add("    " + (entry.getKey().isEmpty() ? "(total)" : entry.getKey()) + ": " + entry.getValue());
            }
        }
        // Modifier, relic and event picks: how often each was taken when it was offered.
        List<String> rates = new ArrayList<>();
        for (String kind : List.of("draft", "relic", "event")) {
            Map<String, Long> offered = groups.getOrDefault(kind + "_offered", Map.of());
            Map<String, Long> taken = groups.getOrDefault(kind + "_taken", Map.of());
            for (Map.Entry<String, Long> entry : offered.entrySet()) {
                long won = taken.getOrDefault(entry.getKey(), 0L);
                rates.add("    " + kind + " " + entry.getKey() + ": taken " + won + " of " + entry.getValue() + " offers ("
                        + (entry.getValue() == 0 ? 0 : won * 100 / entry.getValue()) + "%)");
            }
        }
        long floors = counts.getOrDefault("floor_time.floors", 0L);
        if (floors > 0) {
            rates.add("    floor time: " + counts.getOrDefault("floor_time.millis_sum", 0L) / floors / 1000 + " s on average over " + floors + " cleared floor(s)");
        }
        long bonused = counts.getOrDefault("risk_bonus.payouts_with_bonus", 0L);
        if (bonused > 0) {
            rates.add("    risk bonus: " + counts.getOrDefault("risk_bonus.percent_sum", 0L) / bonused + "% on average over " + bonused + " of "
                    + counts.getOrDefault("risk_bonus.final_payouts", 0L) + " final payouts");
        }
        if (!rates.isEmpty()) {
            out.add("  rates");
            out.addAll(rates);
        }
        return out;
    }
}
