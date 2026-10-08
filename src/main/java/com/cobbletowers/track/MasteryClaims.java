package com.cobbletowers.track;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.SeasonTrackDefinition;
import com.cobbletowers.mastery.MasteryTrack;
import com.cobbletowers.mastery.MasteryTracks;
import com.cobbletowers.persistence.PendingTowerReward;
import com.cobbletowers.persistence.TowerMasteryStore;
import com.cobbletowers.persistence.TowerPendingRewardStore;
import com.cobbletowers.persistence.TowerSeasonProgressStore;
import com.cobbletowers.reward.RewardDelivery;
import com.cobbletowers.season.Cosmetics;
import com.cobbletowers.season.SeasonProgressService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Claiming a mastery level's reward (P37). The prize is claimed any time after the level is reached and never lapses.
 * Replay-safe: grants are queued first under a deterministic id that {@code addIfAbsent} deduplicates, then the claim
 * is recorded.
 */
public final class MasteryClaims {

    private MasteryClaims() {}

    public static String key(ResourceLocation tower, int level) {
        return "m:" + tower + ":" + level;
    }

    public static int levelOf(MinecraftServer server, UUID player, ResourceLocation tower) {
        return TowerMasteryStore.get(server).progressOf(player, tower).level();
    }

    /** Why a claim is refused, or empty when {@code level} can be claimed now. */
    public static Optional<String> refusal(MinecraftServer server, UUID player, ResourceLocation tower, int level) {
        MasteryTrack.Node node = MasteryTracks.forTower(tower).node(level);
        if (node == null || !node.claimable()) return Optional.of("Level " + level + " has no reward.");
        if (levelOf(server, player, tower) < level) return Optional.of("Level " + level + " is not reached yet.");
        if (TowerSeasonProgressStore.get(server).claimed(player, key(tower, level))) return Optional.of("Level " + level + " is already claimed.");
        return Optional.empty();
    }

    /** @return the refusal, or empty when claimed */
    public static Optional<String> claim(MinecraftServer server, UUID player, ResourceLocation tower, int level) {
        Optional<String> refused = refusal(server, player, tower, level);
        if (refused.isPresent()) return refused;
        List<String> given = claimOne(server, player, tower, level);
        flush(server);
        announce(server, player, level, given);
        return Optional.empty();
    }

    /** Pending grants first, then the claim record: a crash between replays the grants harmlessly. */
    private static void flush(MinecraftServer server) {
        TowerPendingRewardStore.get(server).checkpoint(server);
        TowerSeasonProgressStore.get(server).checkpoint(server);
    }

    private static void announce(MinecraftServer server, UUID player, int level, List<String> given) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online == null) return;
        online.sendSystemMessage(Component.literal("Mastery level " + level + " claimed" + (given.isEmpty() ? "" : ": " + String.join(", ", given))));
        if (!given.isEmpty()) RewardDelivery.deliver(server, online);
    }

    /** Queues and records one claim in memory without saving (the caller flushes). Already checked claimable. */
    private static List<String> claimOne(MinecraftServer server, UUID player, ResourceLocation tower, int level) {
        MasteryTrack.Node node = MasteryTracks.forTower(tower).node(level);
        TowerPendingRewardStore pending = TowerPendingRewardStore.get(server);
        long now = System.currentTimeMillis();
        UUID grantId = UUID.nameUUIDFromBytes(("mastery:" + tower + ":" + level + ":" + player).getBytes(StandardCharsets.UTF_8));
        List<String> given = new ArrayList<>();
        Map<String, String> tokens = Map.of("tower", tower.getPath());
        int index = 0;
        for (SeasonTrackDefinition.Grant grant : node.grants()) {
            ResourceLocation item = ResourceLocation.tryParse(Cosmetics.expand(grant.item(), tokens));
            if (item == null || !SeasonProgressService.grantable(item)) {
                TowerLog.warn("Mastery track {} level {}: {} is not an item on this server, so it is skipped.", tower, level, grant.item());
                continue;
            }
            String label = Cosmetics.expand(grant.label(), tokens);
            // One id per grant (the queue deduplicates on id, item and components, so merged grants of one item must
            // not share an id).
            UUID grantOf = index == 0 ? grantId : UUID.nameUUIDFromBytes(("mastery:" + tower + ":" + level + ":" + player + ":" + index)
                    .getBytes(StandardCharsets.UTF_8));
            index++;
            pending.addIfAbsent(player, new PendingTowerReward(grantOf, 1, item, grant.amount(), now,
                    Cosmetics.expand(grant.components(), tokens), label));
            given.add(label.isEmpty() ? SeasonProgressService.describe(item, grant.amount()) : label);
        }
        TowerSeasonProgressStore store = TowerSeasonProgressStore.get(server);
        // Cosmetics from a mastery level are recorded under "m:"; showing them is a later phase.
        for (String cosmetic : node.cosmetics()) store.addCosmetics(player, java.util.Set.of("m:" + cosmetic));
        store.markClaimed(player, key(tower, level));
        TowerLog.info("{} claimed mastery level {} of {}: {}", player, level, tower, given);
        return given;
    }

    /** Claims every reached, unclaimed level of a tower in order; returns how many. */
    public static int claimAll(MinecraftServer server, UUID player, ResourceLocation tower) {
        int claimed = 0;
        List<String> all = new ArrayList<>();
        for (MasteryTrack.Node node : MasteryTracks.forTower(tower).nodes()) {
            if (refusal(server, player, tower, node.level()).isPresent()) continue;
            all.addAll(claimOne(server, player, tower, node.level()));
            claimed++;
        }
        if (claimed == 0) return 0;
        // One save for the batch, one message.
        flush(server);
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.sendSystemMessage(Component.literal("Claimed " + claimed + " mastery level(s)" + (all.isEmpty() ? "" : ": " + String.join(", ", all))));
            if (!all.isEmpty()) RewardDelivery.deliver(server, online);
        }
        return claimed;
    }
}
