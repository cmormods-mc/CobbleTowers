package com.cobbletowers.mastery;

import com.cobbletowers.definition.MasteryTrackDefinition;
import com.cobbletowers.definition.SeasonTrackDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One tower's mastery track after merging every file that applies (P37): rank names, perk rates by level and level
 * rewards. Files merge in order (datapack by id, then the owner's config): grants and cosmetics concatenate; perks,
 * label and rank name are last-wins. Pure.
 */
public final class MasteryTrack {

    /**
     * What claiming a level gives, and what it sets. Empty grants and cosmetics are a level that only changes a perk
     * or a rank.
     */
    public record Node(int level, String label, List<SeasonTrackDefinition.Grant> grants, List<String> cosmetics) {
        public Node {
            grants = List.copyOf(grants);
            cosmetics = List.copyOf(cosmetics);
        }

        public boolean claimable() {
            return !grants.isEmpty() || !cosmetics.isEmpty();
        }
    }

    private final TreeMap<Integer, String> ranks;
    private final TreeMap<Integer, Integer> vendor, dollars, raid;
    private final TreeMap<Integer, Node> nodes;

    private MasteryTrack(TreeMap<Integer, String> ranks, TreeMap<Integer, Integer> vendor, TreeMap<Integer, Integer> dollars,
                         TreeMap<Integer, Integer> raid, TreeMap<Integer, Node> nodes) {
        this.ranks = ranks;
        this.vendor = vendor;
        this.dollars = dollars;
        this.raid = raid;
        this.nodes = nodes;
    }

    public static final MasteryTrack EMPTY = merge(List.of());

    public static MasteryTrack merge(List<MasteryTrackDefinition> files) {
        TreeMap<Integer, String> ranks = new TreeMap<>();
        TreeMap<Integer, Integer> vendor = new TreeMap<>(), dollars = new TreeMap<>(), raid = new TreeMap<>();
        Map<Integer, String> labels = new TreeMap<>();
        Map<Integer, List<SeasonTrackDefinition.Grant>> grants = new TreeMap<>();
        Map<Integer, List<String>> cosmetics = new TreeMap<>();
        for (MasteryTrackDefinition file : files) {
            for (MasteryTrackDefinition.Rank rank : file.ranks()) ranks.put(rank.level(), rank.name());
            for (MasteryTrackDefinition.Level level : file.levels()) {
                level.vendorDiscountPercent().ifPresent(v -> vendor.put(level.level(), v));
                level.cobbleDollarBonusPercent().ifPresent(v -> dollars.put(level.level(), v));
                level.raidPointsBonusPercent().ifPresent(v -> raid.put(level.level(), v));
                if (!level.label().isEmpty()) labels.put(level.level(), level.label());
                grants.computeIfAbsent(level.level(), k -> new ArrayList<>()).addAll(level.grants());
                for (String cosmetic : level.cosmetics()) {
                    List<String> held = cosmetics.computeIfAbsent(level.level(), k -> new ArrayList<>());
                    if (!held.contains(cosmetic)) held.add(cosmetic);
                }
            }
        }
        TreeMap<Integer, Node> nodes = new TreeMap<>();
        for (int level : union(grants.keySet(), cosmetics.keySet())) {
            nodes.put(level, new Node(level, labels.getOrDefault(level, ""), grants.getOrDefault(level, List.of()),
                    cosmetics.getOrDefault(level, List.of())));
        }
        return new MasteryTrack(ranks, vendor, dollars, raid, nodes);
    }

    private static java.util.Set<Integer> union(java.util.Set<Integer> a, java.util.Set<Integer> b) {
        java.util.TreeSet<Integer> all = new java.util.TreeSet<>(a);
        all.addAll(b);
        return all;
    }

    /** The rank name at a level: the highest rank at or below it, or {@code Unranked}. */
    public String rankOf(int level) {
        Map.Entry<Integer, String> entry = ranks.floorEntry(Math.max(0, level));
        return entry == null ? "Unranked" : entry.getValue();
    }

    /** The level at which the next rank begins, or -1 at the top. */
    public int nextRankAt(int level) {
        Integer next = ranks.higherKey(Math.max(0, level));
        return next == null ? -1 : next;
    }

    /** The perks a level has earned: each perk's value is the one set at the highest level at or below it. */
    public MasteryPerks.Perks perksAt(int level) {
        return new MasteryPerks.Perks(valueAt(vendor, level), valueAt(dollars, level), valueAt(raid, level));
    }

    private static int valueAt(TreeMap<Integer, Integer> perk, int level) {
        Map.Entry<Integer, Integer> entry = perk.floorEntry(Math.max(0, level));
        return entry == null ? 0 : entry.getValue();
    }

    /** The rank thresholds in order (level, name), for drawing the track. */
    public Map<Integer, String> ranks() {
        return java.util.Collections.unmodifiableMap(ranks);
    }

    /** The levels that carry something to claim, in order. */
    public List<Node> nodes() {
        return List.copyOf(nodes.values());
    }

    public Node node(int level) {
        return nodes.get(level);
    }

    /** The highest level the track itself mentions (a rank, a perk or a reward). 0 for an empty track. */
    public int highestLevel() {
        int top = nodes.isEmpty() ? 0 : nodes.lastKey();
        for (TreeMap<Integer, Integer> perk : List.of(vendor, dollars, raid)) if (!perk.isEmpty()) top = Math.max(top, perk.lastKey());
        return ranks.isEmpty() ? top : Math.max(top, ranks.lastKey());
    }
}
