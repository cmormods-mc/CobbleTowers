package com.cobbletowers.encounter;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbleraids.api.encounter.EncounterResult;
import com.cobbletowers.battle.cobblemon.CobblemonBattleAdapter;
import com.cobbletowers.battle.cobbleraids.TowerBossAdapter;
import com.cobbletowers.definition.BossPoolDefinition;
import com.cobbletowers.definition.EncounterPoolDefinition;
import com.cobbletowers.definition.FloorAnchor;
import com.cobbletowers.definition.FloorDefinition;
import com.cobbletowers.definition.MilestoneDefinition;
import com.cobbletowers.definition.RulesetDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.instance.CellPreparer;
import com.cobbletowers.instance.TowerDimension;
import com.cobbletowers.persistence.LedgerEntry;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.runtime.RunTransitionService;
import com.cobbletowers.runtime.TowerRuns;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A floor's prerequisite round: who is still fighting, who has cleared, who is out (TDS #31).
 *
 * <p>This owns what is <b>true</b> about a floor. How a battle is actually run belongs to
 * {@link CobblemonBattleAdapter}, and the split is the point: the rules of a floor should read the
 * same whoever is executing the combat, which is what lets P6 put a CobbleRaids boss after this
 * round without rewriting either side.
 *
 * <p>One opponent per player, all at once. The last player clearing resolves the floor; everyone
 * being out loses the run. Both events have been in the transition table since P1 -- this is the
 * first code to raise them.
 */
public final class TowerEncounters {

    public enum Status { FIGHTING, CLEARED, OUT }

    /**
     * Which half of the floor is being fought.
     *
     * <p>The phase lives here, in runtime state, and deliberately not in P1's transition table. The
     * table already has an event for a resolved encounter and one for a wipe, and both still mean
     * exactly what they meant; a table that grew a state every time gameplay gained a step would stop
     * being a contract worth having.
     */
    public enum Phase { PREREQUISITE, BOSS }

    /** One floor's round for one run. */
    public record Round(UUID runId, int floorIndex, Phase phase, Map<UUID, Status> byPlayer) {

        public Round {
            byPlayer = Map.copyOf(byPlayer);
        }

        public boolean everyoneCleared() {
            return byPlayer.values().stream().allMatch(status -> status == Status.CLEARED);
        }

        public boolean everyoneOut() {
            return byPlayer.values().stream().allMatch(status -> status == Status.OUT);
        }

        public boolean settled() {
            return byPlayer.values().stream().noneMatch(status -> status == Status.FIGHTING);
        }
    }

    private static final Map<UUID, Round> ROUNDS = new LinkedHashMap<>();

    private TowerEncounters() {}

    /** Wires this to the adapter. Called once at startup. */
    public static void install() {
        CobblemonBattleAdapter.install(TowerEncounters::onResolved);
        TowerBossAdapter.install(new TowerBossAdapter.Listener() {
            @Override
            public void onBossEnded(MinecraftServer server, TowerBossAdapter.Binding binding,
                                    EncounterResult result) {
                TowerEncounters.onBossEnded(server, binding, result);
            }
        });
    }

    /**
     * Starts the floor's opponents, one per player who can fight.
     *
     * <p>Returns empty when the floor cannot be run at all -- unknown content, nobody able to fight,
     * no cell. The caller turns that into a technical fault rather than a loss, because none of those
     * are a party's doing.
     */
    public static Optional<Round> begin(MinecraftServer server, UUID runId) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) return Optional.empty();
        PersistedRun run = found.get();

        TowerContent content = TowerDefinitionRegistry.content();
        Optional<FloorDefinition> floor = content.floorAt(run.towerId(), run.floorIndex());
        TowerDefinition tower = content.towers().get(run.towerId());
        if (floor.isEmpty() || tower == null) {
            TowerLog.error("Run {} is on {} floor {}, which is not loaded", runId, run.towerId(), run.floorIndex());
            return Optional.empty();
        }
        EncounterPoolDefinition pool = content.pools().get(floor.get().encounterPoolId());
        RulesetDefinition ruleset = content.rulesets().get(
                floor.get().rulesetOverride().orElse(tower.rulesetId()));
        if (pool == null || ruleset == null) {
            TowerLog.error("Floor {} names content that is not loaded (pool {}, ruleset {})",
                    floor.get().id(), floor.get().encounterPoolId(), tower.rulesetId());
            return Optional.empty();
        }

        ServerLevel level = TowerDimension.level(server);
        OptionalInt cell = run.cell();
        if (level == null || cell.isEmpty() || floor.get().layout().isEmpty()) {
            TowerLog.error("Run {} has no built floor to fight on", runId);
            return Optional.empty();
        }
        // Asked of the preparer, not recomputed: the origin depends on the structure's size, and a
        // second calculation here put the entry anchor 25 blocks from the arena and dropped the
        // party into the void beside it.
        Optional<BlockPos> maybeOrigin =
                CellPreparer.originFor(server, cell.getAsInt(), floor.get().layout().get());
        if (maybeOrigin.isEmpty()) {
            TowerLog.error("Floor {} names structure {}, which is not loaded; cannot place anybody",
                    floor.get().id(), floor.get().layout().get().structure());
            return Optional.empty();
        }
        BlockPos origin = maybeOrigin.get();

        List<ServerPlayer> fighters = new ArrayList<>();
        for (PersistedParticipant participant : run.participants()) {
            if (!participant.state().canFight()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player != null) fighters.add(player);
        }
        if (fighters.isEmpty()) {
            TowerLog.error("Run {} has nobody able to fight floor {}", runId, run.floorIndex());
            return Optional.empty();
        }

        // Taken once, from everybody, before a single battle starts (TDS #45): a party that faints or
        // disconnects during the floor cannot make the rest of it easier.
        List<Integer> partyLevels = levelsOf(fighters);

        // Put the party in the arena before anything is fought in it. P4 built the entry anchor and
        // checked a player can stand on it; this is what it was for. Without it the players stay
        // wherever they were -- in the overworld -- while their Pokemon fight in another dimension,
        // which is not a floor so much as a rumour of one.
        FloorAnchor entry = floor.get().layout().get().entry();
        BlockPos arrival = entry.in(origin);
        for (int ordinal = 0; ordinal < fighters.size(); ordinal++) {
            ServerPlayer player = fighters.get(ordinal);
            player.teleportTo(level, arrival.getX() + 0.5 + ordinal * 2, arrival.getY(),
                    arrival.getZ() + 0.5, entry.yaw(), 0.0f);
        }

        Map<UUID, Status> byPlayer = new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < fighters.size(); ordinal++) {
            ServerPlayer player = fighters.get(ordinal);
            Optional<EncounterSnapshot> snapshot = EncounterDraw.draw(
                    pool, run.seed(), run.floorIndex(), ordinal, partyLevels, ruleset);
            if (snapshot.isEmpty()) continue;

            // Spread out, so four opponents do not stand inside one another.
            BlockPos where = floor.get().layout().get().presentation().in(origin).offset(ordinal * 4, 0, 0);
            Optional<UUID> battle = CobblemonBattleAdapter.start(
                    level, player, snapshot.get(), where, runId, run.floorIndex());
            byPlayer.put(player.getUUID(), battle.isPresent() ? Status.FIGHTING : Status.OUT);
        }
        if (byPlayer.isEmpty()) return Optional.empty();

        Round round = new Round(runId, run.floorIndex(), Phase.PREREQUISITE, byPlayer);
        ROUNDS.put(runId, round);
        TowerLog.info("Floor {} of run {} begun with {} opponent(s)", run.floorIndex(), runId, byPlayer.size());
        return Optional.of(round);
    }

    /** Every registered Pokemon of every fighter, fainted ones included. */
    private static List<Integer> levelsOf(List<ServerPlayer> fighters) {
        List<Integer> levels = new ArrayList<>();
        for (ServerPlayer player : fighters) {
            for (BattlePokemon member : Cobblemon.INSTANCE.getStorage().getParty(player).toBattleTeam(true, false)) {
                levels.add(member.getEffectedPokemon().getLevel());
            }
        }
        return levels;
    }

    /** The adapter calls this when one player's battle ends. */
    private static void onResolved(MinecraftServer server, CobblemonBattleAdapter.Binding binding, boolean playerWon) {
        Round round = ROUNDS.get(binding.runId());
        if (round == null) return;

        Map<UUID, Status> byPlayer = new LinkedHashMap<>(round.byPlayer());
        byPlayer.put(binding.playerId(), playerWon ? Status.CLEARED : Status.OUT);
        Round updated = new Round(round.runId(), round.floorIndex(), round.phase(), byPlayer);
        ROUNDS.put(binding.runId(), updated);

        long now = System.currentTimeMillis();
        if (playerWon) earn(server, binding.runId(),
                LedgerEntry.opponentDefeated(binding.floorIndex(), binding.species(), binding.playerId(), now));

        if (!updated.settled()) return;

        if (updated.everyoneOut()) {
            ROUNDS.remove(binding.runId());
            TowerLog.info("Floor {} of run {} wiped the party", updated.floorIndex(), updated.runId());
            lose(server, updated.runId(), updated.floorIndex(), now);
            return;
        }

        // Anyone still standing has earned the right to the boss. The floor is not finished until
        // that is fought: the prerequisite is a prerequisite, not the floor.
        if (!startBoss(server, updated, now)) {
            ROUNDS.remove(binding.runId());
            TowerLog.error("Floor {} of run {} cleared its opponents but the boss could not be started",
                    updated.floorIndex(), updated.runId());
            RunTransitionService.apply(server, updated.runId(), RunEvent.TECHNICAL_FAILURE, now);
        }
    }

    /**
     * Puts this floor's boss up against everyone still standing.
     *
     * <p>Everyone, not only those who cleared their own opponent: the boss is the shared fight, which
     * is the whole reason it runs through CobbleRaids rather than as another set of solo battles.
     */
    private static boolean startBoss(MinecraftServer server, Round round, long now) {
        Optional<PersistedRun> found = TowerRuns.get(round.runId());
        if (found.isEmpty()) return false;
        PersistedRun run = found.get();

        TowerContent content = TowerDefinitionRegistry.content();
        Optional<FloorDefinition> floor = content.floorAt(run.towerId(), run.floorIndex());
        TowerDefinition tower = content.towers().get(run.towerId());
        if (floor.isEmpty() || tower == null) return false;
        RulesetDefinition ruleset = content.rulesets().get(
                floor.get().rulesetOverride().orElse(tower.rulesetId()));
        ServerLevel level = TowerDimension.level(server);
        if (ruleset == null || level == null || run.cell().isEmpty() || floor.get().layout().isEmpty()) return false;

        List<ServerPlayer> standing = new ArrayList<>();
        for (Map.Entry<UUID, Status> entry : round.byPlayer().entrySet()) {
            if (entry.getValue() == Status.OUT) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) standing.add(player);
        }
        if (standing.isEmpty()) return false;

        // A milestone floor takes the boss the milestone names; every other floor draws one.
        Optional<BossPoolDefinition> pool = floor.get().bossPoolId()
                .map(id -> content.bossPools().get(id))
                .filter(Objects::nonNull);
        Optional<BossDraw.Boss> boss = BossDraw.draw(pool, handpickedBoss(content, tower, floor.get()),
                run.seed(), run.floorIndex(), levelsOf(standing), ruleset);
        if (boss.isEmpty()) {
            TowerLog.error("Floor {} names neither a boss pool nor a milestone boss", floor.get().id());
            return false;
        }

        Optional<BlockPos> origin = CellPreparer.originFor(server, run.cell().getAsInt(), floor.get().layout().get());
        if (origin.isEmpty()) return false;
        BlockPos where = floor.get().layout().get().presentation().in(origin.get());

        Optional<UUID> started = TowerBossAdapter.start(
                server, level, standing, boss.get(), where, round.runId(), round.floorIndex());
        if (started.isEmpty()) return false;

        ROUNDS.put(round.runId(), new Round(round.runId(), round.floorIndex(), Phase.BOSS, round.byPlayer()));
        return true;
    }

    /** The definition a milestone floor is meant to finish with, if this is one. */
    private static Optional<ResourceLocation> handpickedBoss(TowerContent content, TowerDefinition tower,
                                                            FloorDefinition floor) {
        if (floor.milestone().isEmpty()) return Optional.empty();
        for (ResourceLocation milestoneId : tower.milestoneIds()) {
            MilestoneDefinition milestone = content.milestones().get(milestoneId);
            if (milestone != null && milestone.floorIndex() == floor.index()) {
                return milestone.raidDefinitionId();
            }
        }
        return Optional.empty();
    }

    /** CobbleRaids has finished with the floor's boss, one way or another. */
    private static void onBossEnded(MinecraftServer server, TowerBossAdapter.Binding binding,
                                    EncounterResult result) {
        Round round = ROUNDS.remove(binding.runId());
        long now = System.currentTimeMillis();
        TowerLog.info("Floor {} boss of run {} ended {} after {} combat tick(s)",
                binding.floorIndex(), binding.runId(), result.outcome(), result.elapsedCombatTicks());

        switch (result.outcome()) {
            case VICTORY -> {
                earn(server, binding.runId(),
                        LedgerEntry.bossDefeated(binding.floorIndex(), binding.definition(), now));
                TowerRuns.get(binding.runId())
                        .flatMap(run -> TowerDefinitionRegistry.content().floorAt(run.towerId(), run.floorIndex()))
                        .ifPresent(floor -> earn(server, binding.runId(),
                                LedgerEntry.floorCleared(binding.floorIndex(), floor.id(), now)));
                // Said plainly, because completing a floor is the thing an operator reading a log is
                // looking for. It moved here when the boss became the end of a floor, and stopped
                // being logged at all for a run -- which the live test noticed before anyone else.
                TowerLog.info("Floor {} of run {} cleared", binding.floorIndex(), binding.runId());
                RunTransitionService.apply(server, binding.runId(), RunEvent.ENCOUNTER_RESOLVED_CLEARED, now);
            }
            case DEFEAT -> lose(server, binding.runId(), binding.floorIndex(), now);
            // Aborted is neither a win nor a loss -- an operator or a shutdown ended it -- so the run
            // parks rather than being scored. Losing a pool to a server restart would be indefensible.
            case ABORTED -> {
                if (round != null) {
                    RunTransitionService.apply(server, binding.runId(), RunEvent.TECHNICAL_FAILURE, now);
                }
            }
        }
    }

    /**
     * The run is lost, so the unclaimed pool goes with it.
     *
     * <p>Marked rather than deleted, so what was earned can still be read afterwards -- and so
     * cashing out at an intermission stays a real decision, which is the point of the rule.
     */
    private static void lose(MinecraftServer server, UUID runId, int floorIndex, long now) {
        TowerLog.info("Floor {} of run {} wiped the party; the unclaimed pool is forfeited",
                floorIndex, runId);
        TowerRuns.get(runId).ifPresent(run -> earn(server, runId,
                LedgerEntry.forfeited(floorIndex, run.towerId(), now)));
        RunTransitionService.apply(server, runId, RunEvent.ENCOUNTER_RESOLVED_WIPED, now);
    }

    /** Appends to the unclaimed pool. No worth is decided here; that is P9's. */
    private static void earn(MinecraftServer server, UUID runId, LedgerEntry entry) {
        TowerRuns.get(runId).ifPresent(run -> TowerRuns.save(server, run.withEarned(entry, entry.at()), false));
    }

    public static Optional<Round> of(UUID runId) {
        return Optional.ofNullable(ROUNDS.get(runId));
    }

    public static int active() {
        return ROUNDS.size();
    }

    /** Ends a run's floor without resolving it: abandoning, parking, shutting down. */
    public static void abandon(MinecraftServer server, UUID runId) {
        ROUNDS.remove(runId);
        CobblemonBattleAdapter.endRun(server, runId);
        // The boss too, or it stands in the cell until the sweep quarantines it.
        TowerBossAdapter.abort(runId);
    }

    public static int onServerStopped(MinecraftServer server) {
        int held = ROUNDS.size();
        ROUNDS.clear();
        CobblemonBattleAdapter.onServerStopped(server);
        TowerBossAdapter.onServerStopped();
        return held;
    }
}
