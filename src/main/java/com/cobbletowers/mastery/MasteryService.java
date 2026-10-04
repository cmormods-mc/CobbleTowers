package com.cobbletowers.mastery;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobbletowers.TowerLog;
import com.cobbletowers.api.modifier.RiskTier;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.ascension.AscensionPolicy;
import com.cobbletowers.definition.AchievementDefinition;
import com.cobbletowers.definition.AchievementRegistry;
import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Key;
import com.cobbletowers.mastery.LeaderboardRules.Member;
import com.cobbletowers.modifier.DraftService;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.TowerLeaderboardStore;
import com.cobbletowers.persistence.TowerMasteryStore;
import com.cobbletowers.persistence.TowerRunStatsStore;
import com.cobbletowers.runtime.TowerRuns;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Mastery and leaderboards at run time (P31): times each cycle, counts faints, and when a cycle is cleared judges it, unlocks
 * achievements, announces them and ranks the result. The rules it applies are all pure ({@link MasteryEvaluator},
 * {@link DifficultyScore}, {@link LeaderboardRules}); this is the part that knows a server.
 *
 * <p>Everything here is called from {@code RunTransitionService.apply} after a move is saved, and from
 * {@code AscensionService} when a team enters an Ascension. None of it can fail a transition: each entry point is guarded, and
 * a mastery problem is logged and dropped rather than allowed to disturb a run.
 */
public final class MasteryService {

    private MasteryService() {}

    public static void install() {
        CobblemonEvents.BATTLE_FAINTED.subscribe(MasteryService::onFaintedSafely);
    }

    // ---- hooks ----------------------------------------------------------------------------------------------------

    /** The transition the run just made; sorts out what it means for timing, clears and endings. */
    public static void onTransition(MinecraftServer server, UUID runId, RunState from, RunState to, long now) {
        try {
            Optional<PersistedRun> found = TowerRuns.get(runId);
            if (found.isEmpty()) return;
            PersistedRun run = found.get();
            if (to == RunState.ENCOUNTER_ACTIVE) {
                floorStarted(server, run, now);
            } else if (to == RunState.FLOOR_RESOLVING) {
                floorEnded(server, run, now);
            }
            if (from == RunState.FLOOR_RESOLVING && isCycleClear(run, to)) cycleCleared(server, run, now);
            if (to.isTerminal()) runEnded(server, run);
        } catch (RuntimeException ex) {
            TowerLog.error("Mastery bookkeeping for run {} failed", runId, ex);
        }
    }

    /** A team has entered Ascension {@code ascension}: depth counts for mastery, and the depth board is updated. */
    public static void onAscensionEntered(MinecraftServer server, PersistedRun run, int ascension) {
        try {
            TowerMasteryStore mastery = TowerMasteryStore.get(server);
            for (PersistedParticipant participant : run.participants()) {
                mastery.raiseDepth(participant.playerId(), run.towerId(), ascension);
                unlockByDepth(server, run, participant.playerId(), mastery);
            }
            mastery.checkpoint(server);
            submitDepth(server, run, ascension);
        } catch (RuntimeException ex) {
            TowerLog.error("Mastery bookkeeping for an Ascension of run {} failed", run.runId(), ex);
        }
    }

    private static boolean isCycleClear(PersistedRun run, RunState to) {
        TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(run.towerId());
        if (tower == null) return false;
        if (to == RunState.COMPLETED) return true;
        return to == RunState.INTERMISSION && tower.ascension()
                && AscensionPolicy.isCycleEnd(run.floorIndex(), tower.floorCount());
    }

    private static void floorStarted(MinecraftServer server, PersistedRun run, long now) {
        TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(run.towerId());
        if (tower == null) return;
        TowerRunStatsStore store = TowerRunStatsStore.get(server);
        TowerRunStatsStore.Stats stats = store.of(run.runId(), run.participants().size());
        if (tower.contentFloor(run.floorIndex()) == 1) {
            stats.activeMillis = 0;
            stats.faints = 0;
        }
        stats.floorStartedAt = now;
        store.touch();
        // A run that started at a deeper Ascension has reached it, whether or not it crossed into it.
        int ascension = tower.ascensionOf(run.floorIndex());
        if (ascension > 0) {
            TowerMasteryStore mastery = TowerMasteryStore.get(server);
            for (PersistedParticipant participant : run.participants()) {
                mastery.raiseDepth(participant.playerId(), run.towerId(), ascension);
            }
        }
    }

    private static void floorEnded(MinecraftServer server, PersistedRun run, long now) {
        TowerRunStatsStore store = TowerRunStatsStore.get(server);
        TowerRunStatsStore.Stats stats = store.peek(run.runId());
        if (stats == null || stats.floorStartedAt <= 0) return;
        stats.activeMillis += Math.max(0, now - stats.floorStartedAt);
        stats.floorStartedAt = 0;
        store.touch();
    }

    private static void onFaintedSafely(BattleFaintedEvent event) {
        try {
            if (!(event.getKilled().getActor() instanceof PlayerBattleActor actor)) return;
            UUID playerId = actor.getUuid();
            Optional<PersistedRun> run = TowerRuns.forPlayer(playerId).filter(r -> !r.isRetired());
            if (run.isEmpty()) return;
            ServerPlayer entity = actor.getEntity();
            if (entity == null) return;
            TowerRunStatsStore store = TowerRunStatsStore.get(entity.server);
            TowerRunStatsStore.Stats stats = store.peek(run.get().runId());
            if (stats == null) return;
            stats.faints++;
            store.touch();
        } catch (RuntimeException ex) {
            TowerLog.error("A tower run failed to note a faint for mastery", ex);
        }
    }

    // ---- a cycle clear --------------------------------------------------------------------------------------------

    /** Builds the result of the cycle the run just cleared, from what the run and its stats hold. */
    static CycleResult resultOf(MinecraftServer server, PersistedRun run, long now) {
        TowerContent content = TowerDefinitionRegistry.content();
        TowerDefinition tower = content.towers().get(run.towerId());
        TowerRunStatsStore.Stats stats = TowerRunStatsStore.get(server).of(run.runId(), run.participants().size());
        List<RiskTier> risks = new ArrayList<>();
        for (ModifierDefinition held : DraftService.held(content, run.modifiers())) risks.add(held.risk());
        for (ResourceLocation locked : run.modifiers().lockedIn()) content.modifier(locked).ifPresent(m -> risks.add(m.risk()));
        int ascension = tower == null ? 0 : tower.ascensionOf(run.floorIndex());
        List<UUID> players = new ArrayList<>();
        for (PersistedParticipant participant : run.participants()) players.add(participant.playerId());
        return new CycleResult(run.runId(), run.towerId(), ascension, players, stats.startSize == 1, stats.activeMillis,
                stats.faints == 0, DifficultyScore.severeIn(risks), DifficultyScore.of(risks, ascension, stats.startSize),
                run.rulesetRevision(), run.towerRevision(), run.towerDigest(), now);
    }

    private static void cycleCleared(MinecraftServer server, PersistedRun run, long now) {
        CycleResult clear = resultOf(server, run, now);
        TowerMasteryStore mastery = TowerMasteryStore.get(server);
        TowerLeaderboardStore boards = TowerLeaderboardStore.get(server);
        List<AchievementDefinition> definitions = AchievementRegistry.all();

        for (UUID player : clear.players()) {
            TowerMasteryStore.Progress before = mastery.progressOf(player, run.towerId());
            int cycles = mastery.addCycle(player, run.towerId());
            mastery.raiseDepth(player, run.towerId(), clear.ascension());
            TowerMasteryStore.Progress now2 = mastery.progressOf(player, run.towerId());
            List<AchievementDefinition> fresh = MasteryEvaluator.unlocked(definitions, now2.unlocked().keySet(), clear,
                    new MasteryEvaluator.Lifetime(cycles, now2.ascensionReached()));
            for (AchievementDefinition achievement : fresh) mastery.unlock(player, run.towerId(), achievement.id(), now);
            announce(server, run, player, before.level(), fresh);

            // The Clears board is per individual: one entry per player, rising with each clear.
            boards.offer(new Key(Board.CLEARS, run.towerId(), LeaderboardRules.Mode.ANY),
                    new Entry(List.of(member(server, player)), cycles, null, clear.ascension(), clear.score(),
                            clear.rulesetRevision(), clear.towerRevision(), clear.towerDigest(), now));
        }
        mastery.checkpoint(server);

        List<Member> team = new ArrayList<>();
        for (UUID player : clear.players()) team.add(member(server, player));
        LeaderboardRules.Mode mode = LeaderboardRules.modeOf(clear.startedSolo());
        boards.offer(new Key(Board.DIFFICULTY, run.towerId(), mode), entryOf(team, clear.score(), clear));
        if (clear.ascension() == 0 && clear.activeMillis() > 0) {
            boards.offer(new Key(Board.SPEED, run.towerId(), mode), entryOf(team, clear.activeMillis(), clear));
        }
        boards.checkpoint(server);
        TowerLog.info("Run {} cleared a cycle of {} at Ascension {}: {} ms, flawless={}, score {}, {} severe",
                run.runId(), run.towerId(), clear.ascension(), clear.activeMillis(), clear.flawless(), clear.score(),
                clear.severeModifiers());
    }

    private static Entry entryOf(List<Member> team, long value, CycleResult clear) {
        return new Entry(team, value, clear.runId(), clear.ascension(), clear.score(), clear.rulesetRevision(),
                clear.towerRevision(), clear.towerDigest(), clear.at());
    }

    // ---- depth ----------------------------------------------------------------------------------------------------

    private static void unlockByDepth(MinecraftServer server, PersistedRun run, UUID player, TowerMasteryStore mastery) {
        TowerMasteryStore.Progress progress = mastery.progressOf(player, run.towerId());
        List<AchievementDefinition> fresh = MasteryEvaluator.unlockedByDepth(AchievementRegistry.all(),
                progress.unlocked().keySet(), new MasteryEvaluator.Lifetime(progress.cyclesCleared(), progress.ascensionReached()));
        long now = System.currentTimeMillis();
        for (AchievementDefinition achievement : fresh) mastery.unlock(player, run.towerId(), achievement.id(), now);
        announce(server, run, player, progress.level(), fresh);
    }

    private static void submitDepth(MinecraftServer server, PersistedRun run, int ascension) {
        if (ascension < 1) return;
        TowerRunStatsStore.Stats stats = TowerRunStatsStore.get(server).of(run.runId(), run.participants().size());
        List<Member> team = new ArrayList<>();
        for (PersistedParticipant participant : run.participants()) team.add(member(server, participant.playerId()));
        TowerLeaderboardStore boards = TowerLeaderboardStore.get(server);
        boards.offer(new Key(Board.ASCENSION, run.towerId(), LeaderboardRules.modeOf(stats.startSize == 1)),
                new Entry(team, ascension, run.runId(), ascension, 0, run.rulesetRevision(), run.towerRevision(),
                        run.towerDigest(), System.currentTimeMillis()));
        boards.checkpoint(server);
    }

    private static void runEnded(MinecraftServer server, PersistedRun run) {
        TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(run.towerId());
        if (tower != null) submitDepth(server, run, tower.ascensionOf(run.floorIndex()));
        TowerRunStatsStore.get(server).remove(run.runId());
    }

    // ---- telling people -------------------------------------------------------------------------------------------

    private static void announce(MinecraftServer server, PersistedRun run, UUID playerId, int levelBefore,
                                 List<AchievementDefinition> fresh) {
        if (fresh.isEmpty()) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return;
        String tower = TowerDefinitionRegistry.content().towers().containsKey(run.towerId())
                ? TowerDefinitionRegistry.content().towers().get(run.towerId()).displayName() : run.towerId().toString();
        for (AchievementDefinition achievement : fresh) {
            player.sendSystemMessage(Component.literal("Achievement unlocked: ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(achievement.displayName()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(Component.literal("  " + achievement.description()).withStyle(ChatFormatting.GRAY)));
        }
        int levelAfter = levelBefore + fresh.size();
        player.sendSystemMessage(Component.literal(tower + " mastery: level " + levelAfter + " (" + MasteryPerks.rankOf(levelAfter) + ")")
                .withStyle(ChatFormatting.AQUA));
        if (!MasteryPerks.rankOf(levelAfter).equals(MasteryPerks.rankOf(levelBefore))) {
            player.sendSystemMessage(Component.literal("New rank: " + MasteryPerks.rankOf(levelAfter) + "!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    // ---- perks ----------------------------------------------------------------------------------------------------

    /** What a player's mastery in a tower earns them there. */
    public static MasteryPerks.Perks perksOf(MinecraftServer server, UUID player, ResourceLocation tower) {
        return MasteryPerks.at(TowerMasteryStore.get(server).progressOf(player, tower).level());
    }

    /** The perks that apply to a player right now: those of the tower their live run is in, none outside a run. */
    public static MasteryPerks.Perks perksInRun(MinecraftServer server, UUID player) {
        return TowerRuns.forPlayer(player).filter(run -> !run.isRetired())
                .map(run -> perksOf(server, player, run.towerId())).orElse(MasteryPerks.Perks.NONE);
    }

    /** The perks of the tower a given run is in (for a reward being delivered after the run). */
    public static MasteryPerks.Perks perksForRun(MinecraftServer server, UUID player, UUID runId) {
        return TowerRuns.get(runId).map(run -> perksOf(server, player, run.towerId())).orElse(MasteryPerks.Perks.NONE);
    }

    // ---- names ----------------------------------------------------------------------------------------------------

    static Member member(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return new Member(id, online.getGameProfile().getName());
        String cached = server.getProfileCache() == null ? null
                : server.getProfileCache().get(id).map(profile -> profile.getName()).orElse(null);
        return new Member(id, cached != null ? cached : id.toString().substring(0, 8));
    }
}
