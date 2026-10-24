package com.cobbletowers.region;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.mastery.CycleResult;
import com.cobbletowers.mastery.MasteryService;
import com.cobbletowers.mastery.MasteryView;
import com.cobbletowers.network.RanksStatePayload;
import com.cobbletowers.persistence.TowerRegionStatsStore;
import com.cobbletowers.persistence.TowerRegionStatsStore.Champion;
import com.cobbletowers.persistence.TowerRegionStatsStore.Tally;
import com.cobbletowers.region.RegionRules.Stat;
import com.cobbletowers.season.Seasons;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

/**
 * The region rivalry: who belongs to which region (their CobbleTeams team), what each region has cleared, and the duel
 * the Ranks tab draws. Players with no region are simply not counted.
 */
public final class RegionService {

    private static final int CHAMPIONS = 3;

    private RegionService() {}

    /** The player's region: their CobbleTeams team if online, else the one last seen. Remembers what it finds. */
    public static Optional<String> regionOf(MinecraftServer server, UUID id) {
        TowerRegionStatsStore store = TowerRegionStatsStore.get(server);
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            Team team = online.getTeam();
            Optional<String> region = team == null ? Optional.empty() : RegionRules.regionOfTeam(team.getName());
            region.ifPresent(r -> store.remember(id, r));
            if (region.isPresent()) return region;
        }
        return store.homeOf(id);
    }

    /** The scopes a result is written to: all-time, plus the running season. */
    private static List<String> writeScopes() {
        List<String> scopes = new ArrayList<>(List.of(TowerRegionStatsStore.ALL));
        Seasons.activeId().ifPresent(scopes::add);
        return scopes;
    }

    /** The scope the tab shows: the running season, else all-time. */
    private static String viewScope() {
        return Seasons.activeId().orElse(TowerRegionStatsStore.ALL);
    }

    private static String viewLabel() {
        return Seasons.activeNumber().map(n -> "Season " + n).orElse("All time");
    }

    /** A cycle was cleared: every player with a region adds a clear to it, and an away one if the tower is a rival's. */
    public static void onCycleCleared(MinecraftServer server, CycleResult clear) {
        try {
            TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(clear.tower());
            Optional<String> theme = tower == null ? Optional.empty() : tower.regionalTheme().map(ResourceLocation::getPath);
            Optional<String> towerRegion = RegionRules.regionOfTower(clear.tower().getPath(), theme);
            TowerRegionStatsStore store = TowerRegionStatsStore.get(server);
            for (UUID player : clear.players()) {
                Optional<String> region = regionOf(server, player);
                if (region.isEmpty()) continue;
                boolean away = RegionRules.isAway(region, towerRegion);
                String name = MasteryService.member(server, player).name();
                for (String scope : writeScopes()) {
                    store.recordClear(scope, region.get(), player, name, away, clear.ascension(), clear.activeMillis());
                }
            }
        } catch (RuntimeException ex) {
            TowerLog.error("A cycle clear failed to reach the region standings", ex);
        }
    }

    /** These players finished a scored trial: their regions each get a trial clear. */
    public static void onTrialCleared(MinecraftServer server, List<UUID> players) {
        try {
            TowerRegionStatsStore store = TowerRegionStatsStore.get(server);
            for (UUID player : players) {
                Optional<String> region = regionOf(server, player);
                if (region.isEmpty()) continue;
                for (String scope : writeScopes()) store.recordTrial(scope, region.get());
            }
        } catch (RuntimeException ex) {
            TowerLog.error("A trial clear failed to reach the region standings", ex);
        }
    }

    /** The duel for a request; an empty or unknown side is filled with the viewer's region and its closest rival. */
    public static RanksStatePayload view(MinecraftServer server, ServerPlayer viewer, String leftRaw, String rightRaw) {
        TowerRegionStatsStore store = TowerRegionStatsStore.get(server);
        String scope = viewScope();
        Optional<String> yours = regionOf(server, viewer.getUUID());

        String left = RegionRules.REGIONS.contains(leftRaw) ? leftRaw : yours.orElse(RegionRules.REGIONS.get(0));
        String right = RegionRules.REGIONS.contains(rightRaw) && !rightRaw.equals(left) ? rightRaw : rival(store, scope, left);

        List<RanksStatePayload.Region> regions = new ArrayList<>();
        for (String id : RegionRules.REGIONS) {
            PlayerTeam team = server.getScoreboard().getPlayerTeam(id);
            regions.add(new RanksStatePayload.Region(id, RegionRules.displayName(id), team == null ? 0 : team.getPlayers().size()));
        }
        Tally a = store.tally(scope, left);
        Tally b = store.tally(scope, right);
        int aPlayers = members(regions, left), bPlayers = members(regions, right);

        List<RanksStatePayload.Row> rows = new ArrayList<>();
        for (Stat stat : Stat.values()) {
            long av = valueOf(stat, a), bv = valueOf(stat, b);
            rows.add(new RanksStatePayload.Row(stat.name().toLowerCase(java.util.Locale.ROOT), stat.label(), text(stat, av), text(stat, bv),
                    bar(stat, av, bv), bar(stat, bv, av), RegionRules.leader(stat, av, bv, aPlayers, bPlayers)));
        }
        String message = yours.isPresent() ? "" : "CobbleTeams has not placed you in a region yet, so your clears are not counted.";
        return new RanksStatePayload(viewLabel(), yours.orElse(""), left, right, regions, rows,
                champions(store, scope, left), champions(store, scope, right), message);
    }

    /** The other region with the most clears, so the default duel is the closest fight. */
    private static String rival(TowerRegionStatsStore store, String scope, String left) {
        String best = null;
        long bestPoints = -1;
        for (String id : RegionRules.REGIONS) {
            if (id.equals(left)) continue;
            Tally t = store.tally(scope, id);
            long points = t.clears() + t.away();
            if (points > bestPoints) {
                best = id;
                bestPoints = points;
            }
        }
        return best;
    }

    private static int members(List<RanksStatePayload.Region> regions, String id) {
        return regions.stream().filter(r -> r.id().equals(id)).mapToInt(RanksStatePayload.Region::members).findFirst().orElse(0);
    }

    private static long valueOf(Stat stat, Tally t) {
        return switch (stat) {
            case CLEARS -> t.clears();
            case AWAY -> t.away();
            case TRIALS -> t.trials();
            case ASCENSION -> t.bestAscension();
            case FASTEST -> t.fastestMs();
        };
    }

    private static String text(Stat stat, long value) {
        if (stat == Stat.FASTEST) return value == 0 ? "none yet" : MasteryView.duration(value);
        return Long.toString(value);
    }

    /** A 0-100 width for the bar of {@code mine} against {@code other}: the larger fills it; for time, the smaller does. */
    private static int bar(Stat stat, long mine, long other) {
        if (stat.kind() == Stat.Kind.LOW) {
            if (mine == 0) return 0;
            return other == 0 ? 100 : (int) Math.round(100.0 * Math.min(mine, other) / mine);
        }
        long top = Math.max(mine, other);
        return top == 0 ? 0 : (int) Math.round(100.0 * mine / top);
    }

    private static List<RanksStatePayload.Champion> champions(TowerRegionStatsStore store, String scope, String region) {
        List<RanksStatePayload.Champion> out = new ArrayList<>();
        for (Champion c : store.champions(scope, region, CHAMPIONS)) {
            out.add(new RanksStatePayload.Champion(c.name(), (int) Math.min(Integer.MAX_VALUE, c.points())));
        }
        return out;
    }
}
