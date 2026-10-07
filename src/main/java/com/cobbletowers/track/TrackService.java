package com.cobbletowers.track;

import com.cobbletowers.definition.AchievementRegistry;
import com.cobbletowers.definition.SeasonTrackDefinition;
import com.cobbletowers.definition.SeasonTrackRegistry;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.mastery.MasteryPerks;
import com.cobbletowers.mastery.MasteryTrack;
import com.cobbletowers.mastery.MasteryTracks;
import com.cobbletowers.network.TrackActionPayload;
import com.cobbletowers.network.TrackStatePayload;
import com.cobbletowers.network.TrackStatePayload.Lane;
import com.cobbletowers.network.TrackStatePayload.Node;
import com.cobbletowers.persistence.TowerSeasonProgressStore;
import com.cobbletowers.season.Cosmetics;
import com.cobbletowers.season.SeasonProgressService;
import com.cobbletowers.season.SeasonSchedule;
import com.cobbletowers.season.Seasons;
import com.cobbletowers.trial.TrialService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Progress tab's tracks (P37): builds what the client draws and answers its claims. Presentation and claiming only; what a level or a step
 * is worth is the track data, and how points and levels are earned is unchanged. Every request is revalidated here, never trusted.
 */
public final class TrackService {

    /** A track longer than this is cut off in the view (the payload holds 512 nodes). */
    static final int MAX_NODES = 400;

    private TrackService() {}

    /** Requests closer together than this from one player are dropped: a client cannot make the server rebuild a track every tick. */
    private static final long MIN_GAP_MILLIS = 150;
    /** Server thread only (the receiver hops to it). */
    private static final Map<java.util.UUID, Long> LAST_REQUEST = new java.util.HashMap<>();

    /** Cuts text to what a payload string may hold ({@code writeUtf} throws past its limit, which would break the packet). */
    static String cut(String text, int max) {
        return text == null ? "" : text.length() <= max ? text : text.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static void handle(MinecraftServer server, ServerPlayer player, TrackActionPayload request) {
        long now = System.currentTimeMillis();
        Long last = LAST_REQUEST.put(player.getUUID(), now);
        if (LAST_REQUEST.size() > 1024) LAST_REQUEST.clear();
        if (last != null && now - last < MIN_GAP_MILLIS) return;
        String message = "";
        ResourceLocation tower = ResourceLocation.tryParse(request.tower());
        // Only a tower this server has: an arbitrary id from a modified client must not grow the merged-track cache.
        if (tower != null && !TowerDefinitionRegistry.content().towers().containsKey(tower)) tower = null;
        switch (request.action()) {
            case "claim" -> {
                Optional<String> refused = request.lane().equals("season")
                        ? SeasonProgressService.claim(server, player.getUUID(), request.number())
                        : tower == null ? Optional.of("Unknown tower.") : MasteryClaims.claim(server, player.getUUID(), tower, request.number());
                message = refused.orElse("Claimed.");
            }
            case "claim_all" -> {
                int claimed = request.lane().equals("season") ? SeasonProgressService.claimAll(server, player.getUUID())
                        : tower == null ? 0 : MasteryClaims.claimAll(server, player.getUUID(), tower);
                message = claimed == 0 ? "Nothing to claim." : "Claimed " + claimed + (claimed == 1 ? " reward." : " rewards.");
            }
            default -> {
            }
        }
        send(server, player, request.tower(), message);
    }

    public static void send(MinecraftServer server, ServerPlayer player, String towerRaw, String message) {
        if (!ServerPlayNetworking.canSend(player, TrackStatePayload.TYPE)) return;
        ServerPlayNetworking.send(player, build(server, player, towerRaw, message));
    }

    public static TrackStatePayload build(MinecraftServer server, ServerPlayer player, String towerRaw, String message) {
        List<ResourceLocation> ids = TowerDefinitionRegistry.content().sortedTowerIds();
        List<TrackStatePayload.Tower> towers = new ArrayList<>();
        for (ResourceLocation id : ids) {
            towers.add(new TrackStatePayload.Tower(id, cut(TowerDefinitionRegistry.content().towers().get(id).displayName(), 128),
                    MasteryClaims.levelOf(server, player.getUUID(), id)));
        }
        ResourceLocation tower = ResourceLocation.tryParse(towerRaw == null ? "" : towerRaw);
        if (tower == null || !ids.contains(tower)) tower = ids.isEmpty() ? null : ids.get(0);
        Lane mastery = tower == null ? Lane.NONE : masteryLane(server, player, tower);
        return new TrackStatePayload(TrackConfig.current().autoClaim(), towers, tower == null ? "" : cut(tower.toString(), 128), mastery,
                seasonLane(server, player), cut(message, 256));
    }

    // ---- mastery ----------------------------------------------------------------------------------

    static Lane masteryLane(MinecraftServer server, ServerPlayer player, ResourceLocation tower) {
        MasteryTrack track = MasteryTracks.forTower(tower);
        TowerSeasonProgressStore store = TowerSeasonProgressStore.get(server);
        int level = MasteryClaims.levelOf(server, player.getUUID(), tower);
        int length = Math.min(MAX_NODES, Math.max(Math.max(track.highestLevel(), AchievementRegistry.all().size()), level));
        List<Node> nodes = new ArrayList<>();
        for (int number = 1; number <= length; number++) {
            MasteryTrack.Node node = track.node(number);
            String note = noteAt(track, number);
            if (node == null || !node.claimable()) {
                nodes.add(new Node(number, TrackStatePayload.INFO, "", "", note));
                continue;
            }
            int state = number > level ? TrackStatePayload.LOCKED
                    : store.claimed(player.getUUID(), MasteryClaims.key(tower, number)) ? TrackStatePayload.CLAIMED : TrackStatePayload.CLAIMABLE;
            nodes.add(new Node(number, state, iconOf(node.grants()), describe(node.label(), node.grants(), node.cosmetics()), note));
        }
        String rank = MasteryPerks.rankOf(tower, level);
        int next = MasteryPerks.nextRankAt(tower, level);
        String subtitle = next < 0 ? rank + " (top rank)" : rank + ", " + (next - level) + " to " + MasteryPerks.rankOf(tower, next);
        return new Lane(true, "Mastery", subtitle, level, 0, 0, 0L, nodes);
    }

    /** A rank starting here, and any perk this level adds. */
    static String noteAt(MasteryTrack track, int level) {
        List<String> parts = new ArrayList<>();
        if (track.ranks().containsKey(level)) parts.add(track.ranks().get(level) + " rank");
        MasteryPerks.Perks now = track.perksAt(level), before = track.perksAt(level - 1);
        if (now.vendorDiscountPercent() != before.vendorDiscountPercent()) parts.add("vendor prices -" + now.vendorDiscountPercent() + "%");
        if (now.cobbleDollarBonusPercent() != before.cobbleDollarBonusPercent()) parts.add("+" + now.cobbleDollarBonusPercent() + "% CobbleDollars");
        if (now.raidPointsBonusPercent() != before.raidPointsBonusPercent()) parts.add("+" + now.raidPointsBonusPercent() + "% Raid Points");
        return String.join(", ", parts);
    }

    // ---- season -----------------------------------------------------------------------------------

    static Lane seasonLane(MinecraftServer server, ServerPlayer player) {
        Optional<SeasonSchedule.Phase> phase = Seasons.phase();
        Optional<SeasonTrackDefinition> found = SeasonTrackRegistry.current();
        if (phase.isEmpty() || !(phase.get() instanceof SeasonSchedule.Active active) || found.isEmpty()) return Lane.NONE;
        SeasonTrackDefinition track = found.get();
        TowerSeasonProgressStore store = TowerSeasonProgressStore.get(server);
        int total = store.of(player.getUUID(), active.number()).total();
        int reached = track.stepsFor(total);
        Map<String, String> tokens = Map.of("season", String.valueOf(active.number()), "season_name", Seasons.definition(active.number()).name());
        List<Node> nodes = new ArrayList<>();
        for (SeasonTrackDefinition.Step step : track.steps()) {
            int state = step.number() > reached ? TrackStatePayload.LOCKED
                    : SeasonProgressService.claimed(store, player.getUUID(), active.number(), step.number())
                            ? TrackStatePayload.CLAIMED : TrackStatePayload.CLAIMABLE;
            List<SeasonTrackDefinition.Grant> expanded = new ArrayList<>();
            for (SeasonTrackDefinition.Grant grant : step.grants()) {
                expanded.add(new SeasonTrackDefinition.Grant(Cosmetics.expand(grant.item(), tokens), grant.amount(), "",
                        Cosmetics.expand(grant.label(), tokens)));
            }
            nodes.add(new Node(step.number(), state, iconOf(expanded), SeasonProgressService.describeStep(step, active.number()), ""));
            if (nodes.size() >= MAX_NODES) break;
        }
        boolean done = reached >= track.stepCount();
        int into = done ? 0 : total - reached * track.stepCost();
        long endsIn = TrialService.millisUntilReset() + Math.max(0L, active.daysLeft() - 1) * 86_400_000L;
        return new Lane(true, "Season " + active.number() + ": " + Seasons.definition(active.number()).name(),
                total + " points", reached, into, done ? 0 : track.stepCost(), endsIn, nodes);
    }

    // ---- words ------------------------------------------------------------------------------------

    private static String iconOf(List<SeasonTrackDefinition.Grant> grants) {
        return grants.isEmpty() ? "" : grants.get(0).item();
    }

    private static String describe(String label, List<SeasonTrackDefinition.Grant> grants, List<String> cosmetics) {
        if (!label.isEmpty()) return label;
        List<String> parts = new ArrayList<>();
        for (SeasonTrackDefinition.Grant grant : grants) {
            ResourceLocation item = ResourceLocation.tryParse(grant.item());
            parts.add(!grant.label().isEmpty() ? grant.label() : item == null ? grant.item() : SeasonProgressService.describe(item, grant.amount()));
        }
        for (String cosmetic : cosmetics) parts.add(cosmetic.replace('_', ' '));
        return String.join(", ", parts);
    }
}
