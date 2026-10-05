package com.cobbletowers.season;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.SeasonDefinition;
import com.cobbletowers.definition.SeasonTrackDefinition;
import com.cobbletowers.definition.SeasonTrackRegistry;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.economy.CobbleDollars;
import com.cobbletowers.economy.RaidPointsCurrency;
import com.cobbletowers.persistence.PendingTowerReward;
import com.cobbletowers.persistence.TowerPendingRewardStore;
import com.cobbletowers.persistence.TowerSeasonProgressStore;
import com.cobbletowers.reward.RewardDelivery;
import com.cobbletowers.season.SeasonPoints.Progress;
import com.cobbletowers.season.SeasonPoints.Result;
import com.cobbletowers.season.SeasonPoints.Source;
import com.cobbletowers.trial.TrialClock;
import com.cobbletowers.trial.TrialService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Season points and the free track at run time (P36b): awards points for the play that already exists, and grants each track step
 * the moment its points are reached, through the pending-reward store (delivered at once to an online player and at login to an
 * offline one). The rules are {@link SeasonPoints} and {@link SeasonTrackDefinition}; this is the part that knows a server.
 *
 * <p>Idempotent by construction: a step is granted only when the player's stored step count is below the step reached, and the count
 * is saved with the grant, so a replay or a crash cannot pay a step twice.
 */
public final class SeasonProgressService {

    private SeasonProgressService() {}

    /** A regional tower is one with a regional theme; Neutral and the Test tower never earn season points for a clear. */
    public static boolean regional(ResourceLocation towerId) {
        TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(towerId);
        return tower != null && tower.regionalTheme().isPresent();
    }

    // ---- earning ----------------------------------------------------------------------------------

    /** A regional cycle clear by these players (spotlight if it was the season's featured region). Ignored for any other tower. */
    public static void regionalCleared(MinecraftServer server, ResourceLocation tower, List<UUID> players) {
        if (!regional(tower)) return;
        boolean spotlight = Seasons.activeNumber().map(Seasons::definition).flatMap(SeasonDefinition::spotlight)
                .map(tower::equals).orElse(false);
        for (UUID player : players) award(server, player, Source.REGIONAL_CLEAR, 0, spotlight);
    }

    /** Awards one source to a player; returns the points added (0 when no season is running, or a cap or an earlier count applies). */
    public static int award(MinecraftServer server, UUID player, Source source, int param, boolean spotlight) {
        Optional<Integer> season = Seasons.activeNumber();
        if (season.isEmpty()) return 0;
        TowerSeasonProgressStore store = TowerSeasonProgressStore.get(server);
        Progress before = store.of(player, season.get());
        Result result = SeasonPoints.award(before, source, param, spotlight,
                TrialClock.dayKey(TrialService.today()), TrialClock.weekKey(TrialService.today()));
        if (result.next() == before) return 0;
        settle(server, player, season.get(), result.next());
        if (result.granted() > 0) {
            TowerLog.info("{} earned {} season point(s) from {} (season {}: {} total)", player, result.granted(), source,
                    season.get(), result.next().total());
        }
        return result.granted();
    }

    /** An operator's grant: adds points outside every cap (a live test has no other way to earn a track's worth). */
    public static int addPoints(MinecraftServer server, UUID player, int points) {
        Optional<Integer> season = Seasons.activeNumber();
        if (season.isEmpty() || points < 1) return 0;
        TowerSeasonProgressStore store = TowerSeasonProgressStore.get(server);
        Progress before = store.of(player, season.get());
        Progress next = new Progress(before.total() + points, before.steps(), before.day(), before.dayTotal(), before.dayCounts(),
                before.once());
        settle(server, player, season.get(), next);
        return points;
    }

    /** Saves the new tally and grants every track step it has reached that has not been granted. */
    private static void settle(MinecraftServer server, UUID player, int season, Progress next) {
        TowerSeasonProgressStore store = TowerSeasonProgressStore.get(server);
        Optional<SeasonTrackDefinition> track = SeasonTrackRegistry.current();
        Progress saved = next;
        if (track.isPresent()) {
            int reached = track.get().stepsFor(next.total());
            if (reached > next.steps()) {
                // The step count is saved with the tally before anything is handed over, so a crash in between cannot grant twice.
                saved = next.withSteps(reached);
                store.put(player, season, saved);
                store.checkpoint(server);
                for (int step = next.steps() + 1; step <= reached; step++) grantStep(server, player, season, track.get(), step);
                return;
            }
        }
        store.put(player, season, saved);
        store.checkpoint(server);
    }

    private static void grantStep(MinecraftServer server, UUID player, int season, SeasonTrackDefinition track, int number) {
        SeasonTrackDefinition.Step step = track.steps().get(number - 1);
        TowerPendingRewardStore pending = TowerPendingRewardStore.get(server);
        long now = System.currentTimeMillis();
        UUID grantId = UUID.nameUUIDFromBytes(("season:" + season + ":" + number + ":" + player).getBytes(StandardCharsets.UTF_8));
        List<String> given = new ArrayList<>();
        for (SeasonTrackDefinition.Grant grant : step.grants()) {
            ResourceLocation item = ResourceLocation.tryParse(grant.item().replace("{season}", String.valueOf(season)));
            if (item == null || !grantable(item)) {
                TowerLog.warn("Season {} track step {}: {} is not an item on this server, so it is skipped.", season, number, grant.item());
                continue;
            }
            pending.add(player, new PendingTowerReward(grantId, 1, item, grant.amount(), now));
            given.add(describe(item, grant.amount()));
        }
        Set<String> cosmetics = new HashSet<>();
        for (String name : step.cosmetics()) cosmetics.add("s" + season + ":" + name);
        TowerSeasonProgressStore.get(server).addCosmetics(player, cosmetics);
        pending.checkpoint(server);
        TowerSeasonProgressStore.get(server).checkpoint(server);
        TowerLog.info("{} reached season {} track step {}: {}{}", player, season, number, given,
                cosmetics.isEmpty() ? "" : " and cosmetics " + cosmetics);

        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online == null) return;
        String reward = given.isEmpty() && cosmetics.isEmpty() ? "" : ": " + describeStep(step, season);
        online.sendSystemMessage(Component.literal("Season " + season + " track, step " + number + "/" + track.stepCount() + " reached"
                + reward));
        if (!given.isEmpty()) RewardDelivery.deliver(server, online);
    }

    /** A currency the delivery credits, or an item this server has. */
    private static boolean grantable(ResourceLocation id) {
        return id.equals(CobbleDollars.ITEM_ID) || id.equals(RaidPointsCurrency.ITEM_ID) || BuiltInRegistries.ITEM.containsKey(id);
    }

    // ---- words ------------------------------------------------------------------------------------

    static String describe(ResourceLocation item, int amount) {
        if (item.equals(CobbleDollars.ITEM_ID)) return amount + " CobbleDollars";
        if (item.equals(RaidPointsCurrency.ITEM_ID)) return amount + " Raid Points";
        String name = item.getPath().replace('_', ' ');
        return (amount > 1 ? amount + " " : "") + name;
    }

    /** What a step gives, in a phrase: its items and its cosmetics. */
    public static String describeStep(SeasonTrackDefinition.Step step, int season) {
        List<String> parts = new ArrayList<>();
        for (SeasonTrackDefinition.Grant grant : step.grants()) {
            ResourceLocation item = ResourceLocation.tryParse(grant.item().replace("{season}", String.valueOf(season)));
            parts.add(item == null ? grant.item() : describe(item, grant.amount()));
        }
        for (String cosmetic : step.cosmetics()) parts.add(cosmetic.replace('_', ' '));
        return String.join(", ", parts);
    }

    /** What {@code /tower season track} says. */
    public static List<String> trackLines(MinecraftServer server, UUID player) {
        Optional<Integer> season = Seasons.activeNumber();
        if (season.isEmpty()) return List.of(Seasons.enabled() ? "There is no season track right now: no season is running."
                : "Seasons are switched off on this server.");
        Optional<SeasonTrackDefinition> track = SeasonTrackRegistry.current();
        if (track.isEmpty()) return List.of("This server has no season track.");
        TowerSeasonProgressStore store = TowerSeasonProgressStore.get(server);
        Progress progress = store.of(player, season.get());
        int reached = track.get().stepsFor(progress.total());
        List<String> lines = new ArrayList<>();
        lines.add("Season " + season.get() + ": " + Seasons.definition(season.get()).name() + " track - step " + reached + "/"
                + track.get().stepCount() + ", " + progress.total() + "/" + track.get().totalPoints() + " points"
                + (progress.dayTotal() > 0 && TrialClock.dayKey(TrialService.today()).equals(progress.day())
                        ? " (" + progress.dayTotal() + "/" + SeasonPoints.DAILY_CAP + " earned today)" : ""));
        if (reached >= track.get().stepCount()) {
            lines.add("The whole track is done.");
        } else {
            for (int next = reached + 1; next <= Math.min(reached + 3, track.get().stepCount()); next++) {
                int away = next * track.get().stepCost() - progress.total();
                lines.add("  step " + next + " in " + away + " point(s): " + describeStep(track.get().steps().get(next - 1), season.get()));
            }
        }
        Set<String> earned = store.cosmeticsOf(player);
        if (!earned.isEmpty()) lines.add("Earned: " + earned.stream().map(name -> name.replace('_', ' ')).collect(Collectors.joining(", ")));
        lines.add("Points come from regional cycle clears (more in the spotlight region), trials, contracts, streak milestones, Echo "
                + "Duels and your club's weekly reward.");
        return lines;
    }

    /** One line for the login summary: where the track stands, or empty when there is nothing to say. */
    public static Optional<String> loginLine(MinecraftServer server, UUID player) {
        Optional<Integer> season = Seasons.activeNumber();
        Optional<SeasonTrackDefinition> track = SeasonTrackRegistry.current();
        if (season.isEmpty() || track.isEmpty()) return Optional.empty();
        Progress progress = TowerSeasonProgressStore.get(server).of(player, season.get());
        int reached = track.get().stepsFor(progress.total());
        if (reached >= track.get().stepCount()) return Optional.of("Season track: complete.");
        int away = (reached + 1) * track.get().stepCost() - progress.total();
        return Optional.of("Season " + season.get() + " track: step " + reached + "/" + track.get().stepCount() + ", " + away
                + " point(s) to " + describeStep(track.get().steps().get(reached), season.get()) + ".");
    }
}
