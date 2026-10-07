package com.cobbletowers.encounter;

import com.cobbletowers.ServerState;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbleraids.api.encounter.EncounterResult;
import com.cobbletowers.battle.cobblemon.CobblemonBattleAdapter;
import com.cobbletowers.battle.cobblemon.PartyReader;
import com.cobbletowers.battle.cobbleraids.TowerBossAdapter;
import com.cobbletowers.definition.BossPoolDefinition;
import com.cobbletowers.definition.FloorAnchor;
import com.cobbletowers.definition.FloorDefinition;
import com.cobbletowers.definition.MilestoneDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.diagnostics.TowerMetrics;
import com.cobbletowers.economy.AscensionLibScouting;
import com.cobbletowers.instance.CellPreparer;
import com.cobbletowers.modifier.DraftService;
import com.cobbletowers.modifier.ModifierEffects;
import com.cobbletowers.instance.TowerDimension;
import com.cobbletowers.persistence.LedgerEntry;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.runtime.ParticipantService;
import com.cobbletowers.runtime.RunExitService;
import com.cobbletowers.runtime.PartyValidation;
import com.cobbletowers.runtime.RunTransitionService;
import com.cobbletowers.runtime.TowerRuns;
import com.cobbletowers.spectator.SpectatorPresentation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A floor's prerequisite round: one opponent per player, all at once. The last player to clear resolves the floor;
 * everyone out loses the run.
 */
public final class TowerEncounters {

    public enum Status { FIGHTING, CLEARED, OUT }

    /** Which half of the floor is being fought. Kept here, not in the run transition table. */
    public enum Phase { PREREQUISITE, BOSS }

    /**
     * Opponents one player still owes this floor. The stride is carried because the fighter list is gone by the time
     * a battle ends.
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

    static {
        ServerState.onStop(TowerEncounters::onServerStopped);
    }

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
     * Starts one opponent per player who can fight. Empty means the floor cannot run at all (a technical fault, not a
     * loss).
     */
    public static Optional<Round> begin(MinecraftServer server, UUID runId) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) return Optional.empty();
        Optional<FloorSetup> resolved = FloorSetup.resolve(server, found.get(), true);
        if (resolved.isEmpty()) return Optional.empty();
        FloorSetup setup = resolved.get();
        PersistedRun run = setup.run();

        List<ServerPlayer> fighters = fightersOf(server, run);
        if (fighters.isEmpty()) {
            TowerLog.error("Run {} has nobody able to fight floor {}", runId, run.floorIndex());
            return Optional.empty();
        }
        // Taken once, from everybody, before a single battle starts (TDS #45): a party that faints or
        // disconnects during the floor cannot make the rest of it easier.
        List<Integer> partyLevels = levelsOf(run, fighters);
        if (partyLevels.isEmpty()) {
            // Logged because an empty draw otherwise returned empty with no explanation.
            TowerLog.error("Run {} cannot start floor {}: none of its {} fighter(s) has a Pokemon to"
                    + " fight with, so no opponent can be levelled", runId, run.floorIndex(), fighters.size());
            return Optional.empty();
        }

        arrive(server, setup, fighters);
        ModifierEffects effects = DraftService.effects(run);
        Optional<Round> round = startOpponents(server, setup, fighters, partyLevels, effects);
        if (round.isEmpty()) {
            TowerLog.error("Run {} drew no opponents at all for floor {}; pool {} produced nothing",
                    runId, run.floorIndex(), setup.floor().encounterPoolId());
            return Optional.empty();
        }
        ROUNDS.put(runId, round.get());
        TowerLog.info("Floor {} of run {} begun with {} opponent(s){}", run.floorIndex(), runId,
                round.get().byPlayer().size(),
                effects.extraOpponents() > 0 ? " plus " + effects.extraOpponents() + " more each" : "");
        return round;
    }

    /** The run's participants who can fight and are online. */
    private static List<ServerPlayer> fightersOf(MinecraftServer server, PersistedRun run) {
        List<ServerPlayer> fighters = new ArrayList<>();
        for (PersistedParticipant participant : run.participants()) {
            if (!participant.state().canFight()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player != null) fighters.add(player);
        }
        return fighters;
    }

    /** Puts the party in the arena before anything is fought. */
    private static void arrive(MinecraftServer server, FloorSetup setup, List<ServerPlayer> fighters) {
        FloorAnchor entry = setup.layout().entry();
        BlockPos arrival = entry.in(setup.origin());
        for (int ordinal = 0; ordinal < fighters.size(); ordinal++) {
            ServerPlayer player = fighters.get(ordinal);
            // Where they stand now is where they go home to (P20); skipped if they are already inside.
            RunExitService.remember(server, player);
            player.teleportTo(setup.level(), arrival.getX() + 0.5 + ordinal * 2, arrival.getY(),
                    arrival.getZ() + 0.5, entry.yaw(), 0.0f);
        }
    }

    /** Draws and starts each fighter's first opponent. Empty when none could be started. */
    private static Optional<Round> startOpponents(MinecraftServer server, FloorSetup setup,
                                                  List<ServerPlayer> fighters, List<Integer> partyLevels,
                                                  ModifierEffects effects) {
        PersistedRun run = setup.run();
        Map<UUID, Status> byPlayer = new LinkedHashMap<>();
        Map<UUID, Wave> waves = new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < fighters.size(); ordinal++) {
            ServerPlayer player = fighters.get(ordinal);
            long started = System.nanoTime();
            Optional<EncounterSnapshot> snapshot = EncounterDraw.draw(setup.pool(), run.seed(), run.floorIndex(),
                    ordinal, partyLevels, setup.ruleset(), effects.levelOffset(), setup.theme());
            if (snapshot.isEmpty()) continue;
            waves.put(player.getUUID(),
                    new Wave(ordinal + fighters.size(), fighters.size(), effects.extraOpponents()));

            BlockPos where = setup.presentation(ordinal);
            // Declared and armed before the battle starts: the enemy's ascension effects ride its >start, so the
            // enemy a Scouter reveals has to exist by then.
            String scoutId = FloorScouting.declareOpponent(run.runId(), player.getUUID(), run.floorIndex(),
                    snapshot.get());
            Optional<UUID> battle = AscensionLibScouting.armed(List.of(player.getUUID()), scoutId,
                    () -> CobblemonBattleAdapter.start(setup.level(), player, snapshot.get(), where, run.runId(),
                            run.floorIndex()));
            if (battle.isEmpty()) FloorScouting.endOpponent(run.runId(), player.getUUID());
            byPlayer.put(player.getUUID(), battle.isPresent() ? Status.FIGHTING : Status.OUT);
            if (battle.isPresent()) {
                // First opponent per player is timed here; later ones in sendNextOpponent.
                TowerMetrics.recordEncounterConstruction(server, run.runId(),
                        (System.nanoTime() - started) / 1_000_000);
            }
        }
        if (byPlayer.isEmpty()) return Optional.empty();
        return Optional.of(new Round(run.runId(), run.floorIndex(), Phase.PREREQUISITE, byPlayer, waves,
                System.currentTimeMillis()));
    }

    /**
     * Levels of every fighter's registered Pokemon, fainted ones included (TDS #45); falls back to the live party if
     * none registered.
     */
    private static List<Integer> levelsOf(PersistedRun run, List<ServerPlayer> fighters) {
        List<Integer> levels = new ArrayList<>();
        for (ServerPlayer player : fighters) {
            List<UUID> registered = run.participants().stream()
                    .filter(participant -> participant.playerId().equals(player.getUUID()))
                    .findFirst().map(PersistedParticipant::registeredPokemon).orElse(List.of());
            levels.addAll(PartyValidation.levels(PartyReader.members(player), registered));
        }
        return levels;
    }

    /** The adapter calls this when one player's battle ends. */
    private static void onResolved(MinecraftServer server, CobblemonBattleAdapter.Binding binding, boolean playerWon) {
        // An Echo Duel (P35) is a bonus exhibition at an intermission: it is not the floor's, and no round is waiting
        // on it.
        if (binding.exhibition()) {
            com.cobbletowers.echo.EchoDuels.onResolved(server, binding, playerWon);
            return;
        }
        FloorScouting.endOpponent(binding.runId(), binding.playerId());
        Round round = ROUNDS.get(binding.runId());
        if (round == null) return;

        long now = System.currentTimeMillis();
        if (playerWon) {
            earn(server, binding.runId(),
                    LedgerEntry.opponentDefeated(binding.floorIndex(), binding.species(), binding.playerId(), now));
        }

        // A winner who still owes opponents starts the next one and stays FIGHTING. Recorded first because starting
        // can fail.
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

        // This player's own floor participation just finished, win or loss -- anyone spectating them
        // is watching a fight that no longer exists.
        if (!stillFighting) {
            TowerRuns.get(binding.runId())
                    .ifPresent(run -> SpectatorPresentation.refreshFollowersOf(server, run, binding.playerId()));
        }

        if (!playerWon) knockOut(server, binding.runId(), binding.playerId(), now);

        settle(server, updated, now);
    }

    /** Runs once nobody is still fighting, whether the last one lost, won or was dropped. */
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
     * Starts an Echo Duel: one Pokemon of an Echo against a cloned, healed party.
     * @return whether the battle started
     */
    public static boolean startEchoDuel(MinecraftServer server, PersistedRun run, ServerPlayer player, int ordinal,
                                        com.cobbletowers.echo.EchoPolicy.Pick pick) {
        Optional<FloorSetup> resolved = FloorSetup.resolve(server, run, false);
        if (resolved.isEmpty()) return false;
        FloorSetup setup = resolved.get();
        Optional<EncounterSnapshot> drawn = EncounterDraw.draw(setup.pool(), run.seed(), run.floorIndex(), ordinal,
                levelsOf(run, List.of(player)), setup.ruleset(), DraftService.effects(run).levelOffset(),
                setup.theme());
        if (drawn.isEmpty()) return false;
        EncounterSnapshot opponent = drawn.get().withEcho(pick.properties(), pick.echo().name());
        BlockPos where = setup.presentation(ordinal);
        // An exhibition is explicitly native: no enemy effects, not even a wild rating.
        return AscensionLibScouting.armed(List.of(player.getUUID()), null,
                () -> CobblemonBattleAdapter.startExhibition(setup.level(), player, opponent, where, run.runId(),
                        run.floorIndex())).isPresent();
    }

    /**
     * Starts the next opponent of a multi-opponent floor. Content is re-resolved each time so a datapack reload
     * cannot leave it stale.
     * @return false leaves the player cleared
     */
    private static boolean sendNextOpponent(MinecraftServer server, Round round, UUID playerId, Wave wave) {
        Optional<PersistedRun> found = TowerRuns.get(round.runId());
        if (found.isEmpty()) return false;
        PersistedRun run = found.get();
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        Optional<FloorSetup> resolved = FloorSetup.resolve(server, run, false);
        if (player == null || resolved.isEmpty()) return false;
        FloorSetup setup = resolved.get();

        long started = System.nanoTime();
        ModifierEffects effects = DraftService.effects(run);
        // Levels come from this player's own party; fainted Pokemon still count (TDS #45).
        Optional<EncounterSnapshot> snapshot = EncounterDraw.draw(setup.pool(), run.seed(), run.floorIndex(),
                wave.nextOrdinal(), levelsOf(run, List.of(player)), setup.ruleset(), effects.levelOffset(),
                setup.theme());
        if (snapshot.isEmpty()) return false;

        BlockPos where = setup.presentation(wave.nextOrdinal());
        String scoutId = FloorScouting.declareOpponent(round.runId(), playerId, run.floorIndex(), snapshot.get());
        Optional<UUID> battle = AscensionLibScouting.armed(List.of(playerId), scoutId,
                () -> CobblemonBattleAdapter.start(setup.level(), player, snapshot.get(), where, round.runId(),
                        run.floorIndex()));
        if (battle.isEmpty()) {
            FloorScouting.endOpponent(round.runId(), playerId);
            return false;
        }
        TowerMetrics.recordEncounterConstruction(server, round.runId(), (System.nanoTime() - started) / 1_000_000);
        setup.theme().ifPresent(theme -> FloorScouting.announceJersey(player, theme, snapshot.get()));
        FloorScouting.sendReveal(player, setup, effects, snapshot.get());
        TowerLog.info("Run {} floor {}: {} faces another opponent ({} left after this)",
                round.runId(), run.floorIndex(), playerId, wave.remaining() - 1);
        return true;
    }

    /**
     * Takes one player out of the floor and lets it carry on; settles the floor if they were the last fighter.
     * @return true when a floor had them
     */
    public static boolean dropPlayer(MinecraftServer server, UUID runId, UUID playerId, String why, long now) {
        CobblemonBattleAdapter.endPlayer(server, runId, playerId);
        FloorScouting.endOpponent(runId, playerId);
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
     * Marks a player knocked out. KNOCKED_OUT is true at once; SPECTATING_TEAM only once they stand somewhere
     * watching.
     */
    private static void knockOut(MinecraftServer server, UUID runId, UUID playerId, long now) {
        ParticipantService.update(server, runId, playerId, ParticipantState::knockedOut, now);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return;
        TowerRuns.get(runId).ifPresent(run -> {
            if (sendToSpectatorAnchor(server, run, player)) {
                ParticipantService.update(server, runId, playerId, ParticipantState::spectating, now)
                        .ifPresent(updated -> SpectatorPresentation.startSpectating(server, updated, player));
            }
        });
    }

    /** Every floor being fought right now, for the watchdog to judge. */
    public static List<Round> activeRounds() {
        return List.copyOf(ROUNDS.values());
    }

    /** Stands a player on the floor's spectator anchor. @return false when the floor has none */
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

    /** Puts the boss up against everyone still standing, not just those who cleared. */
    private static boolean startBoss(MinecraftServer server, Round round, long now) {
        Optional<PersistedRun> found = TowerRuns.get(round.runId());
        if (found.isEmpty()) return false;
        Optional<FloorSetup> resolved = FloorSetup.resolve(server, found.get(), false);
        if (resolved.isEmpty()) return false;
        FloorSetup setup = resolved.get();
        PersistedRun run = setup.run();

        List<ServerPlayer> standing = new ArrayList<>();
        for (Map.Entry<UUID, Status> entry : round.byPlayer().entrySet()) {
            if (entry.getValue() == Status.OUT) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) standing.add(player);
        }
        if (standing.isEmpty()) return false;

        // A milestone floor takes the boss the milestone names; every other floor draws one.
        Optional<BossPoolDefinition> pool = setup.floor().bossPoolId()
                .map(id -> setup.content().bossPools().get(id))
                .filter(Objects::nonNull);
        Optional<BossDraw.Boss> boss = BossDraw.draw(pool, handpickedBoss(setup.content(), setup.tower(), setup.floor()),
                run.seed(), run.floorIndex(), levelsOf(run, standing), setup.ruleset(),
                DraftService.effects(run).bossLevelOffset());
        if (boss.isEmpty()) {
            TowerLog.error("Floor {} names neither a boss pool nor a milestone boss", setup.floor().id());
            return false;
        }

        // The boss fight sends out CLONES of each party (CobbleRaids), so a lead left standing from the
        // opponent fight would be there twice. Put the real ones away first.
        for (ServerPlayer player : standing) recallParty(player);

        Optional<UUID> started = TowerBossAdapter.start(
                server, setup.level(), standing, boss.get(), setup.presentation(0), round.runId(), round.floorIndex(),
                DraftService.effects(run),
                // Declared inside the adapter, once the encounter id exists and before the boss battle starts.
                encounterId -> FloorScouting.declareBoss(server, setup, boss.get(), standing, encounterId));
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
        return content.milestoneAt(tower.id(), floor.index()).flatMap(MilestoneDefinition::raidDefinitionId);
    }

    /** Whether {@code floorIndex} is the last floor of the run's tower. */
    private static boolean isFinalFloor(UUID runId, int floorIndex) {
        // A trial (P32) ends on its own floor limit, whatever the tower's length.
        Optional<PersistedRun> trial = TowerRuns.get(runId).filter(run -> run.options().floorLimit() > 0);
        if (trial.isPresent()) return floorIndex >= trial.get().options().floorLimit();
        return TowerRuns.get(runId)
                .map(run -> TowerDefinitionRegistry.content().towers().get(run.towerId()))
                // A tower that ascends (P30) has no last floor: a cycle's end is an intermission where the team
                // chooses to cash out or go on, not the end of the run.
                .map(tower -> !tower.ascension() && floorIndex >= tower.floorCount())
                .orElse(false);
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
                // A milestone floor also pays its milestone reward (P21), banked with everything else.
                TowerRuns.get(binding.runId())
                        .flatMap(run -> TowerDefinitionRegistry.content().milestoneAt(run.towerId(), binding.floorIndex()))
                        .ifPresent(milestone -> earn(server, binding.runId(),
                                LedgerEntry.milestoneCleared(binding.floorIndex(), milestone.id(), now)));
                // Logged so an operator can see floors completing.
                TowerLog.info("Floor {} of run {} cleared", binding.floorIndex(), binding.runId());
                FloorPayout.pay(server, binding, result, now);
                // Everyone watching is now owed the intermission, which is where they come back.
                ParticipantService.markRevivePending(server, binding.runId(), now);
                // And the party's Pokemon go back in their balls: the floor is over, and a lead left
                // standing is what the cell's cleanup sweep quarantines the cell for.
                recallParties(server, binding.runId());
                RunTransitionService.Outcome resolved =
                        RunTransitionService.apply(server, binding.runId(), RunEvent.ENCOUNTER_RESOLVED_CLEARED, now);
                // Only picks the transition; RewardBankService decides whether the clear pays out.
                if (resolved instanceof RunTransitionService.Move) {
                    RunEvent next = isFinalFloor(binding.runId(), binding.floorIndex())
                            ? RunEvent.FINAL_FLOOR_CLEARED : RunEvent.REWARDS_BANKED;
                    RunTransitionService.apply(server, binding.runId(), next, now);
                } else {
                    TowerLog.error("Run {} could not resolve floor {} into FLOOR_RESOLVING; rewards were not banked",
                            binding.runId(), binding.floorIndex());
                }
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

    /** The run is lost. The unclaimed pool is marked, not deleted, so it can still be read. */
    private static void lose(MinecraftServer server, UUID runId, int floorIndex, long now) {
        TowerLog.info("Floor {} of run {} wiped the party; the unclaimed pool is forfeited",
                floorIndex, runId);
        TowerRuns.get(runId).ifPresent(run -> earn(server, runId,
                LedgerEntry.forfeited(floorIndex, run.towerId(), now)));
        RunTransitionService.apply(server, runId, RunEvent.ENCOUNTER_RESOLVED_WIPED, now);
    }


    /** Appends to the unclaimed pool. No worth is decided here; that is P9's. */
    /** Adds an entry to a run's unclaimed pool. Public for the operator {@code runs earn} command, a test seam. */
    public static void earn(MinecraftServer server, UUID runId, LedgerEntry entry) {
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
        FloorScouting.endRun(runId);
        CobblemonBattleAdapter.endRun(server, runId);
        // The boss too, or it stands in the cell until the sweep quarantines it.
        TowerBossAdapter.abort(runId);
        // Aborting the boss does not end the player's battle with it; end it here so they can fight again.
        endParticipantBattles(server, runId);
        recallParties(server, runId);
    }

    /** Ends whatever Cobblemon battle each of a run's online participants is still in (see {@code abandon}). */
    private static void endParticipantBattles(MinecraftServer server, UUID runId) {
        TowerRuns.get(runId).ifPresent(run -> {
            for (PersistedParticipant participant : run.participants()) {
                ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
                if (player != null) CobblemonBattleAdapter.endBattleOf(player);
            }
        });
    }

    /** Recalls the party's Pokemon before the cell is handed back; a leftover entity would quarantine the cell. */
    private static void recallParties(MinecraftServer server, UUID runId) {
        TowerRuns.get(runId).ifPresent(run -> {
            for (PersistedParticipant participant : run.participants()) {
                ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
                if (player != null) recallParty(player);
            }
        });
    }

    private static void recallParty(ServerPlayer player) {
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon.getEntity() != null) pokemon.recall();
        }
    }

    public static int onServerStopped(MinecraftServer server) {
        int held = ROUNDS.size();
        ROUNDS.clear();
        FloorScouting.clear();
        CobblemonBattleAdapter.onServerStopped(server);
        TowerBossAdapter.onServerStopped();
        return held;
    }
}
