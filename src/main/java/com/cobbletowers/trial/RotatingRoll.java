package com.cobbletowers.trial;

import com.cobbletowers.definition.RotatingRecipeDefinition;
import com.cobbletowers.definition.RotatingRecipeDefinition.Mutator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * The parts of one rotating trial (P46), a pure function of the recipe and the period number. Each part draws from its
 * own seed key, so adding a part later does not reshuffle the others, and the path and house rule never repeat the
 * previous period's.
 * @param segmentRegions the region of each segment, first to last
 * @param namedIndex the hand-written entry a featured period uses, or -1
 */
public record RotatingRoll(
        List<ResourceLocation> segmentRegions,
        Optional<ResourceLocation> playlist,
        int level,
        List<ResourceLocation> mutators,
        Optional<ResourceLocation> startingRelic,
        Optional<String> roomBias,
        Optional<String> weeklyRule,
        int namedIndex) {

    private static final int REDRAWS = 16;
    /** How far back a redraw is chased; a longer chain than this is vanishingly rare. */
    private static final int GUARD_DEPTH = 8;

    public RotatingRoll {
        segmentRegions = List.copyOf(segmentRegions);
        mutators = List.copyOf(mutators);
    }

    public boolean featured() {
        return namedIndex >= 0;
    }

    /** The region of trial floor {@code floor} (1-based). */
    public ResourceLocation regionAt(RotatingRecipeDefinition recipe, int floor) {
        return segmentRegions.get(Math.min((floor - 1) / recipe.segmentSize(), segmentRegions.size() - 1));
    }

    /** The roll for period {@code number} (the epoch day for a daily, the week number for a weekly). */
    public static RotatingRoll of(RotatingRecipeDefinition recipe, long number) {
        List<ResourceLocation> path = guardedPath(recipe, number, GUARD_DEPTH);
        Optional<ResourceLocation> playlist = guardedPlaylist(recipe, number, GUARD_DEPTH);

        int level = recipe.levels().get(pick(recipe, number, "level", 0, recipe.levels().size()));
        Optional<ResourceLocation> relic = chance(recipe, number, "relic", recipe.relicPercent())
                ? Optional.of(recipe.relics().get(pick(recipe, number, "relic-pick", 0, recipe.relics().size()))) : Optional.empty();
        Optional<String> room = chance(recipe, number, "room", recipe.roomBiasPercent())
                ? Optional.of(recipe.roomBias().get(pick(recipe, number, "room-pick", 0, recipe.roomBias().size()))) : Optional.empty();
        Optional<String> rule = recipe.weeklyRules().isEmpty() ? Optional.empty()
                : Optional.of(recipe.weeklyRules().get(pick(recipe, number, "rule", 0, recipe.weeklyRules().size())));
        return new RotatingRoll(path, playlist, level, mutators(recipe, number), relic, room, rule, namedIndex(recipe, number));
    }

    // ---- the parts

    /** The path of period {@code number}, redrawn while it equals the (guarded) path before it. */
    private static List<ResourceLocation> guardedPath(RotatingRecipeDefinition recipe, long number, int depth) {
        List<ResourceLocation> path = path(recipe, number, 0);
        if (depth == 0) return path;
        List<ResourceLocation> previous = guardedPath(recipe, number - 1, depth - 1);
        for (int salt = 1; path.equals(previous) && salt <= REDRAWS; salt++) path = path(recipe, number, salt);
        return path;
    }

    private static Optional<ResourceLocation> guardedPlaylist(RotatingRecipeDefinition recipe, long number, int depth) {
        Optional<ResourceLocation> playlist = playlist(recipe, number, 0);
        if (depth == 0) return playlist;
        Optional<ResourceLocation> previous = guardedPlaylist(recipe, number - 1, depth - 1);
        for (int salt = 1; playlist.isPresent() && playlist.equals(previous) && salt <= REDRAWS; salt++) playlist = playlist(recipe, number, salt);
        return playlist;
    }

    private static List<ResourceLocation> path(RotatingRecipeDefinition recipe, long number, int salt) {
        List<ResourceLocation> regions = recipe.regions();
        List<ResourceLocation> path = new ArrayList<>();
        if (regions.size() == 1 || chance(recipe, number, "single" + salt, recipe.singleRegionPercent())) {
            ResourceLocation only = regions.get(pick(recipe, number, "single-pick", salt, regions.size()));
            for (int i = 0; i < recipe.segments(); i++) path.add(only);
            return path;
        }
        ResourceLocation last = null;
        for (int i = 0; i < recipe.segments(); i++) {
            // never the same region twice running, so a segment boundary is a change
            List<ResourceLocation> options = new ArrayList<>(regions);
            options.remove(last);
            last = options.get(pick(recipe, number, "segment" + i, salt, options.size()));
            path.add(last);
        }
        return path;
    }

    private static Optional<ResourceLocation> playlist(RotatingRecipeDefinition recipe, long number, int salt) {
        if (!chance(recipe, number, "playlist" + salt, recipe.playlistPercent())) return Optional.empty();
        return Optional.of(recipe.playlists().get(pick(recipe, number, "playlist-pick", salt, recipe.playlists().size())));
    }

    private static List<ResourceLocation> mutators(RotatingRecipeDefinition recipe, long number) {
        List<Integer> weights = recipe.mutatorCountWeights();
        int total = weights.stream().mapToInt(Integer::intValue).sum();
        int roll = pick(recipe, number, "mutator-count", 0, total);
        int count = 0;
        for (; count < weights.size(); count++) {
            roll -= weights.get(count);
            if (roll < 0) break;
        }
        List<ResourceLocation> chosen = new ArrayList<>();
        Set<String> groups = new HashSet<>();
        for (int attempt = 0; chosen.size() < count && attempt < recipe.mutators().size() * 4; attempt++) {
            Mutator mutator = recipe.mutators().get(pick(recipe, number, "mutator", attempt, recipe.mutators().size()));
            if (groups.add(mutator.group())) chosen.add(mutator.id());
        }
        return chosen;
    }

    /** The hand-written entry a featured period uses, or -1: one period in {@code featuredEvery}, at a seeded place. */
    private static int namedIndex(RotatingRecipeDefinition recipe, long number) {
        int every = recipe.featuredEvery();
        if (every <= 0) return -1;
        long cycle = Math.floorDiv(number, (long) every);
        int place = (int) Math.floorMod(TrialSeed.of(recipe.id() + "|feature|" + cycle), (long) every);
        if (Math.floorMod(number, (long) every) != place) return -1;
        return TrialSchedule.indexFor(recipe.id() + "|named", recipe.named().size(), cycle);
    }

    // ---- seeded draws

    private static int pick(RotatingRecipeDefinition recipe, long number, String part, int salt, int bound) {
        if (bound <= 1) return 0;
        return (int) Math.floorMod(TrialSeed.of(recipe.id() + "|" + number + "|" + part + "|" + salt), (long) bound);
    }

    private static boolean chance(RotatingRecipeDefinition recipe, long number, String part, int percent) {
        return percent > 0 && pick(recipe, number, part + "-chance", 0, 100) < percent;
    }
}
