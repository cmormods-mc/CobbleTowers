package com.cobbletowers.encounter;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
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
import com.cobbletowers.modifier.DraftService;
import com.cobbletowers.modifier.ModifierEffects;
import com.cobbletowers.instance.TowerDimension;
import com.cobbletowers.persistence.LedgerEntry;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.runtime.ParticipantService;
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

    /**
     * How many more opponents one player owes this floor, and where the next one is drawn from.
     *
     * <p>An ENCOUNTER modifier's {@code extra_opponents} is what puts anything but zero here: the
     * floor's prerequisite becomes several battles in a row rather than one.
     *
     * <p>The stride is carried rather than recomputed. The next opponent's ordinal has to stay clear
     * of every other player's, so it steps by the number of fighters -- and by the time a battle
     * ends, the list of fighters it was calculated from is gone. Carrying it is three bytes against
     * re-deriving a number that must not change mid-floor.
     */
    public record Wave(int nextOrdinal, int stride, int remaining) {
        public Wave taken() {
            return new Wave(nextOrdinal + stride, stride, remaining - 1);
        }
    }

    /** One floor's round for one run. */
    public record Round(UUID runId, int floorIndex, Phase phase, Map<UUID, Status> byPlayer,
                        Map<UUID, Wave> waves, long startedAt) {

        public Round {
            byPlayer = Map.copyOf(byPlayer);
            waves = Map.copyOf(waves);
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
        if (partyLevels.isEmpty()) {
            // Said out loud, because the alternative was found the hard way: every opponent draw
            // silently returned empty, the round ended up with nobody in it, and `begin` returned
            // an empty Optional with nothing logged. The operator got "the floor could not be
            // started" and the log had not one word about why.
            TowerLog.error("Run {} cannot start floor {}: none of its {} fighter(s) has a Pokemon to"
                    + " fight with, so no opponent can be levelled", runId, run.floorIndex(), fighters.size());
            return Optional.empty();
        }

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

        // What the party has drafted, resolved once for the whole floor rather than per opponent.
        ModifierEffects effects = DraftService.effects(run);

        Map<UUID, Status> byPlayer = new LinkedHashMap<>();
        Map<UUID, Wave> waves = new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < fighters.size(); ordinal++) {
            ServerPlayer player = fighters.get(ordinal);
            Optional<EncounterSnapshot> snapshot = EncounterDraw.draw(
                    pool, run.seed(), run.floorIndex(), ordinal, partyLevels, ruleset, effects.levelOffset());
            if (snapshot.isEmpty()) continue;
            waves.put(player.getUUID(),
                    new Wave(ordinal + fighters.size(), fighters.size(), effects.extraOpponents()));

            // Spread out, so four opponents do not stand inside one another.
            BlockPos where = floor.get().layout().get().presentation().in(origin).offset(ordinal * 4, 0, 0);
            Optional<UUID> battle = CobblemonBattleAdapter.start(
                    level, player, snapshot.get(), where, runId, run.floorIndex());
            byPlayer.put(player.getUUID(), battle.isPresent() ? Status.FIGHTING : Status.OUT);
        }
        if (byPlayer.isEmpty()) {
            TowerLog.error("Run {} drew no opponents at all for floor {}; pool {} produced nothing",
                    runId, run.floorIndex(), floor.get().encounterPoolId());
            return Optional.empty();
        }

        Round round = new Round(runId, run.floorIndex(), Phase.PREREQUISITE, byPlayer, waves,
                System.currentTimeMillis());
        ROUNDS.put(runId, round);
        TowerLog.info("Floor {} of run {} begun with {} opponent(s){}", run.floorIndex(), runId, byPlayer.size(),
                effects.extraOpponents() > 0 ? " plus " + effects.extraOpponents() + " more each" : "");
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

        long now = System.currentTimeMillis();
        if (playerWon) {
            earn(server, binding.runId(),
                    LedgerEntry.opponentDefeated(binding.floorIndex(), binding.species(), binding.playerId(), now));
        }

        // A winner who still owes opponents goes straight into the next one and stays FIGHTING, so
        // the floor does not settle underneath them. Recorded before the status is, because starting
        // it can fail and a player with nothing left to fight has simply cleared.
        Wave wave = round.waves().get(binding.playerId());
        Map<UUID, Wave> waves = new LinkedHashMap<>(round.waves());
        boolean stillFighting = false;
        if (playerWon && wave != null && wave.remaining() > 0) {
            stillFighting = sendNextOpponent(server, round, binding.playerId(), wave);
            if (stillFighting) waves.put(binding.playerId(), wave.taken());
        }

        Map<UUID, Status> byPlayer = new LinkedHashMap<>(round.byPlayer());
        if (!stillFighting) byPlayer.put(binding.playerId(), playerWon ? Status.CLEARED : Status.OUT);
        Round updated = new Round(round.runId(), round.floorIndex(), round.phase(), byPlayer, waves,
                round.startedAt());
        ROUNDS.put(binding.runId(), updated);

        if (!playerWon) knockOut(server, binding.runId(), binding.playerId(), now);

        settle(server, updated, now);
    }

    /**
     * What happens once nobody on the floor is still fighting.
     *
     * <p>Reached from a battle ending and from a player being dropped, which is why it is not
     * written into either: a floor whose last fighter walks away has to finish the same way as one
     * whose last fighter loses, or it hangs holding the run, the cell and its tickets.
     */
    private static void settle(MinecraftServer server, Round round, long now) {
        if (!round.settled()) return;

        if (round.everyoneOut()) {
            ROUNDS.remove(round.runId());
            TowerLog.info("Floor {} of run {} wiped the party", round.floorIndex(), round.runId());
            lose(server, round.runId(), round.floorIndex(), now);
            return;
        }

        // Anyone still standing has earned the right to the boss. The floor is not finished until
        // that is fought: the prerequisite is a prerequisite, not the floor.
        if (!startBoss(server, round, now)) {
            ROUNDS.remove(round.runId());
            TowerLog.error("Floor {} of run {} cleared its opponents but the boss could not be started",
                    round.floorIndex(), round.runId());
            RunTransitionService.apply(server, round.runId(), RunEvent.TECHNICAL_FAILURE, now);
        }
    }

    /**
     * Puts the next opponent of a multi-opponent floor in front of one player.
     *
     * <p>Re-resolves the floor's content rather than carrying it on the round. It is a handful of
     * map lookups, it happens once per won battle rather than per tick, and the alternative is a
     * round holding definitions that a datapack reload could make stale underneath it -- the thing
     * TDS §10 keeps out of persisted state, for the same reason.
     *
     * @return whether a battle actually started; false leaves the player cleared, which is the safe
     *         way to fail -- a floor that cannot put up the next opponent must not hang waiting
     */
    private static boolean sendNextOpponent(MinecraftServer server, Round round, UUID playerId, Wave wave) {
        Optional<PersistedRun> found = TowerRuns.get(round.runId());
        if (found.isEmpty()) return false;
        PersistedRun run = found.get();
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        ServerLevel level = TowerDimension.level(server);
        if (player == null || level == null || run.cell().isEmpty()) return false;

        TowerContent content = TowerDefinitionRegistry.content();
        Optional<FloorDefinition> floor = content.floorAt(run.towerId(), run.floorIndex());
        TowerDefinition tower = content.towers().get(run.towerId());
        if (floor.isEmpty() || tower == null || floor.get().layout().isEmpty()) return false;
        EncounterPoolDefinition pool = content.pools().get(floor.get().encounterPoolId());
        RulesetDefinition ruleset = content.rulesets().get(
                floor.get().rulesetOverride().orElse(tower.rulesetId()));
        if (pool == null || ruleset == null) return false;

        Optional<BlockPos> origin = CellPreparer.originFor(server, run.cell().getAsInt(), floor.get().layout().get());
        if (origin.isEmpty()) return false;

        ModifierEffects effects = DraftService.effects(run);
        // The level snapshot is taken from this player's own party, as the first draw was from the
        // whole party's. It cannot be lowered by the floor's progress: TowerLevelPolicy reads the
        // registered Pokemon, and a fainted one still counts (TDS #45).
        Optional<EncounterSnapshot> snapshot = EncounterDraw.draw(pool, run.seed(), run.floorIndex(),
                wave.nextOrdinal(), levelsOf(List.of(player)), ruleset, effects.levelOffset());
        if (snapshot.isEmpty()) return false;

        BlockPos where = floor.get().layout().get().presentation().in(origin.get())
                .offset(wave.nextOrdinal() * 4, 0, 0);
        Optional<UUID> battle = CobblemonBattleAdapter.start(
                level, player, snapshot.get(), where, round.runId(), run.floorIndex());
        if (battle.isEmpty()) return false;
        TowerLog.info("Run {} floor {}: {} faces another opponent ({} left after this)",
                round.runId(), run.floorIndex(), playerId, wave.remaining() - 1);
        return true;
    }

    /**
     * Takes one player out of the floor they are on, and lets the floor carry on without them.
     *
     * <p>A disconnect past its grace window, a stalled player the watchdog gave up on, somebody who
     * chose to leave. Their battle ends and their opponent goes with it -- an opponent left standing
     * is what P3's sweep quarantines a cell for -- and if they were the last one fighting, the floor
     * settles exactly as it would have if they had lost.
     *
     * @return true when a floor actually had them
     */
    public static boolean dropPlayer(MinecraftServer server, UUID runId, UUID playerId, String why, long now) {
        CobblemonBattleAdapter.endPlayer(server, runId, playerId);
        Round round = ROUNDS.get(runId);
        if (round == null || !round.byPlayer().containsKey(playerId)) return false;
        if (round.byPlayer().get(playerId) != Status.FIGHTING) return false;

        Map<UUID, Status> byPlayer = new LinkedHashMap<>(round.byPlayer());
        byPlayer.put(playerId, Status.OUT);
        Round updated = new Round(round.runId(), round.floorIndex(), round.phase(), byPlayer, round.waves(),
                round.startedAt());
        ROUNDS.put(runId, updated);
        TowerLog.info("Player {} dropped from floor {} of run {}: {}", playerId, round.floorIndex(), runId, why);

        settle(server, updated, now);
        return true;
    }

    /**
     * A player whose battle is lost: out of the fight, and watching the rest of the floor (TDS #25).
     *
     * <p>Two states rather than one, because they are two different facts. KNOCKED_OUT is true the
     * moment they lose, whether or not they are online to be put anywhere; SPECTATING_TEAM is true
     * only once they are actually standing somewhere watching. Someone who lost while disconnected
     * gets the first and not the second, and comes back to exactly that.
     */
    private static void knockOut(MinecraftServer server, UUID runId, UUID playerId, long now) {
        ParticipantService.update(server, runId, playerId, ParticipantState::knockedOut, now);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return;
        TowerRuns.get(runId).ifPresent(run -> {
            if (sendToSpectatorAnchor(server, run, player)) {
                ParticipantService.update(server, runId, playerId, ParticipantState::spectating, now);
            }
        });
    }

    /** Every floor being fought right now, for the watchdog to judge. */
    public static List<Round> activeRounds() {
        return List.copyOf(ROUNDS.values());
    }

    /**
     * Stands a player on their floor's spectator anchor.
     *
     * <p>P4 built that anchor and checked somebody could stand on it; this is what it was for. The
     * alternative is leaving a knocked-out player in the middle of an arena they are no longer in,
     * or -- worse, for somebody reconnecting -- wherever they logged out.
     *
     * @return false when the floor cannot say where that is, so the caller can leave them be
     */
    public static boolean sendToSpectatorAnchor(MinecraftServer server, PersistedRun run, ServerPlayer player) {
        Optional<FloorDefinition> floor = TowerDefinitionRegistry.content().floorAt(run.towerId(), run.floorIndex());
        ServerLevel level = TowerDimension.level(server);
        if (floor.isEmpty() || floor.get().layout().isEmpty() || level == null || run.cell().isEmpty()) return false;

        Optional<BlockPos> origin = CellPreparer.originFor(server, run.cell().getAsInt(), floor.get().layout().get());
        if (origin.isEmpty()) return false;

        FloorAnchor anchor = floor.get().layout().get().spectator();
        BlockPos where = anchor.in(origin.get());
        player.teleportTo(level, where.getX() + 0.5, where.getY(), where.getZ() + 0.5, anchor.yaw(), 0.0f);
        return true;
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
                run.seed(), run.floorIndex(), levelsOf(standing), ruleset,
                DraftService.effects(run).bossLevelOffset());
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

        ROUNDS.put(round.runId(),
                new Round(round.runId(), round.floorIndex(), Phase.BOSS, round.byPlayer(), round.waves(),
                        round.startedAt()));
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
                // Everyone watching is now owed the intermission, which is where they come back.
                ParticipantService.markRevivePending(server, binding.runId(), now);
                // And the party's Pokemon go back in their balls: the floor is over, and a lead left
                // standing is what the cell's cleanup sweep quarantines the cell for.
                recallParties(server, binding.runId());
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
        recallParties(server, runId);
    }

    /**
     * Puts the party's own Pokemon back in their balls before the cell is handed back.
     *
     * <p>Opponents were never the only thing left standing in a cell. A player's own lead is still
     * out when a floor ends, and the cleanup sweep does not care whose it is: a non-player entity in
     * the cell is a quarantine, so every completed run would have cost the tower a cell.
     *
     * <p>Recalled rather than discarded. The entity is only how a Pokemon is shown; throwing it away
     * would leave the Pokemon marked as sent out with nothing to send back.
     *
     * <p>Found by the live test only after its probe was fixed -- the old one used
     * {@code execute ... run say}, which returns nothing over RCON, so it had been reporting an
     * empty cell whatever was in it.
     */
    private static void recallParties(MinecraftServer server, UUID runId) {
        TowerRuns.get(runId).ifPresent(run -> {
            for (PersistedParticipant participant : run.participants()) {
                ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
                if (player == null) continue;
                for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                    if (pokemon.getEntity() != null) pokemon.recall();
                }
            }
        });
    }

    public static int onServerStopped(MinecraftServer server) {
        int held = ROUNDS.size();
        ROUNDS.clear();
        CobblemonBattleAdapter.onServerStopped(server);
        TowerBossAdapter.onServerStopped();
        return held;
    }
}
