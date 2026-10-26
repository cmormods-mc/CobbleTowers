package com.cobbletowers.battle.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleStartError;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.ErroredBattleStart;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.cobbletowers.TowerLog;
import com.cobbletowers.encounter.EncounterSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kotlin.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The only place that names a Cobblemon battle: one battle per player. Parties are fought as-is (no clone, no heal;
 * TDS #16) and opponents are uncatchable (TDS #78).
 */
public final class CobblemonBattleAdapter {

    /** What a running battle belongs to. Ids only -- an entity reference here would pin its level. */
    public record Binding(UUID runId, UUID playerId, int floorIndex, ResourceLocation species, UUID opponentEntity,
                          long startedAt, boolean exhibition) {
        /** A floor's battle: the usual case. */
        public Binding(UUID runId, UUID playerId, int floorIndex, ResourceLocation species, UUID opponentEntity,
                       long startedAt) {
            this(runId, playerId, floorIndex, species, opponentEntity, startedAt, false);
        }
    }

    private static final BattleIndex INDEX = new BattleIndex();
    private static final Map<UUID, Binding> BY_BATTLE = INDEX.byBattle;
    private static final Map<UUID, List<UUID>> BATTLES_BY_RUN = INDEX.byRun;
    private static final Map<UUID, Long> LAST_ACTIVITY = INDEX.lastActivity;
    private static final Map<UUID, Long> PROBE_AT = INDEX.probeAt;
    private static final long PROBE_DELAY_MILLIS = 20_000;
    /** Turn counters of open battles: a turn that moves counts as activity, not only a faint. */
    private static final TurnTracker TURNS = INDEX.turns;
    private static final int TURN_POLL_TICKS = 20;
    private static int pollTicks;
    /** The running server, so a result never depends on a participant still being online to find it. */
    private static volatile MinecraftServer runningServer;
    private static boolean installed;

    /** Marks an entity this adapter spawned as a tower opponent, so a stray one can be told from anything else. */
    static final String OPPONENT_TAG = "cobbletowers_opponent";
    private static final int ORPHAN_SWEEP_TICKS = 100;
    /** An opponent must have stood this long (ticks) before it can be called stray: the battle starts the tick it spawns. */
    private static final int ORPHAN_GRACE_TICKS = 200;
    private static int sweepTicks;
    /** Opponents to discard once they can be found, with the ticks left to try. Server thread only. */
    private static final Map<UUID, Integer> PENDING_DISCARDS = new HashMap<>();
    private static final int PENDING_DISCARD_TICKS = 100;

    private CobblemonBattleAdapter() {}

    /** What happens when a floor's battle ends. Implemented by the encounter, called by this. */
    public interface Listener {
        void onResolved(MinecraftServer server, Binding binding, boolean playerWon);
    }

    private static Listener listener = (server, binding, won) -> {};

    /**
     * Subscribes to Cobblemon's global battle events once. Routing by battle id keeps other battles from ending a
     * floor.
     */
    public static void install(Listener encounterListener) {
        listener = encounterListener;
        if (installed) return;
        installed = true;
        CobblemonEvents.BATTLE_VICTORY.subscribe(CobblemonBattleAdapter::onVictorySafely);
        CobblemonEvents.BATTLE_FAINTED.subscribe(CobblemonBattleAdapter::onFaintedSafely);
        CobblemonEvents.BATTLE_FLED.subscribe(CobblemonBattleAdapter::onFledSafely);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED
                .register(server -> runningServer = server);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            // An exception out of a tick callback ends the server: every step is contained.
            try {
                retryPendingDiscards(server);
                probeQuietBattles(System.currentTimeMillis());
                if (++pollTicks % TURN_POLL_TICKS == 0) pollTurns(System.currentTimeMillis());
            } catch (RuntimeException ex) {
                TowerLog.errorOnce("battle-tick", "The battle adapter tick failed", ex);
            }
            if (++sweepTicks % ORPHAN_SWEEP_TICKS != 0) return;
            try {
                sweepOrphanOpponents(server);
            } catch (RuntimeException ex) {
                TowerLog.errorOnce("orphan-sweep", "The stray opponent sweep failed", ex);
            }
        });
        TowerLog.info("Cobblemon battle adapter installed");
    }

    /** Spawns an opponent and starts one player's battle. @return the battle id, empty if it could not start */
    public static Optional<UUID> start(ServerLevel level, ServerPlayer player, EncounterSnapshot snapshot,
                                       BlockPos where, UUID runId, int floorIndex) {
        return start(level, player, snapshot, where, runId, floorIndex, false);
    }

    /** An exhibition (Echo Duel): a cloned, healed party, no floor effects, marked so the encounter ignores it. */
    public static Optional<UUID> startExhibition(ServerLevel level, ServerPlayer player, EncounterSnapshot snapshot,
                                                 BlockPos where, UUID runId, int floorIndex) {
        return start(level, player, snapshot, where, runId, floorIndex, true);
    }

    private static Optional<UUID> start(ServerLevel level, ServerPlayer player, EncounterSnapshot snapshot,
                                        BlockPos where, UUID runId, int floorIndex, boolean exhibition) {
        PokemonEntity opponent = spawn(level, snapshot, where);
        if (opponent == null) return Optional.empty();

        // Armed only around the start itself: the effects ride this battle's >start and no other's.
        if (!exhibition) {
            com.cobbletowers.showdown.TowerBattleFx.armFloorBattle(player.getUUID(),
                    com.cobbletowers.armor.ArmorBonusEffects.battleEffects(player, runId));
        }
        BattleStartResult result;
        try {
            result = startPve(player, opponent, exhibition);
        } finally {
            if (!exhibition) com.cobbletowers.showdown.TowerBattleFx.disarm(player.getUUID());
        }


        if (!(result instanceof SuccessfulBattleStart success)) {
            TowerLog.error("Cobblemon refused a tower battle for {} on floor {}: {}",
                    player.getGameProfile().getName(), floorIndex, describe(result, player));
            opponent.discard();
            return Optional.empty();
        }

        UUID battleId = success.getBattle().getBattleId();
        Binding binding = new Binding(runId, player.getUUID(), floorIndex, snapshot.species(), opponent.getUUID(),
                System.currentTimeMillis(), exhibition);
        register(battleId, binding);
        // An Echo's Pokemon is named by what is actually built, not by the pool species the slot was drawn from.
        String opponentName = snapshot.echoProperties().map(com.cobbletowers.echo.EchoPolicy::speciesOf)
                .orElse(snapshot.species().toString());
        TowerLog.info("Floor {} battle {} started{}: {} vs {} at level {}", floorIndex, battleId,
                exhibition ? " (Echo duel)" : "", player.getGameProfile().getName(), opponentName, snapshot.level());
        return Optional.of(battleId);
    }

    private static void register(UUID battleId, Binding binding) {
        INDEX.register(battleId, binding, PROBE_DELAY_MILLIS);
    }

    /** Drops a battle from every index. @return its binding, null if it was not (or no longer) ours */
    private static Binding forget(UUID battleId) {
        return INDEX.forget(battleId);
    }

    /** A turn that moved is the player acting; the watchdog reads it as life. Once a second is plenty. */
    private static void pollTurns(long now) {
        if (BY_BATTLE.isEmpty()) return;
        try {
            for (UUID battleId : BY_BATTLE.keySet()) {
                PokemonBattle battle = BattleRegistry.getBattle(battleId);
                if (battle == null || battle.getEnded()) continue;
                if (TURNS.advanced(battleId, battle.getTurn())) LAST_ACTIVITY.put(battleId, now);
            }
        } catch (RuntimeException ex) {
            TowerLog.errorOnce("battle-turns", "Could not poll battle turns", ex);
        }
    }

    /**
     * Says once, 20 s after a floor battle started, where it stands if it is still on its first turn: a battle nobody
     * can move in (a stall) otherwise leaves no trace, and the floor watchdog only acts after minutes.
     */
    private static void probeQuietBattles(long now) {
        if (PROBE_AT.isEmpty()) return;
        var due = PROBE_AT.entrySet().iterator();
        while (due.hasNext()) {
            var entry = due.next();
            if (now < entry.getValue()) continue;
            due.remove();
            try {
                PokemonBattle battle = BattleRegistry.getBattle(entry.getKey());
                Binding binding = BY_BATTLE.get(entry.getKey());
                if (battle == null || binding == null || battle.getEnded() || battle.getTurn() > 1) continue;
                StringBuilder actors = new StringBuilder();
                for (var actor : battle.getActors()) {
                    actors.append(' ').append(actor.getShowdownId()).append("{mustChoose=").append(actor.getMustChoose())
                            .append(",request=").append(actor.getRequest() != null)
                            .append(",responses=").append(actor.getResponses().size()).append('}');
                }
                TowerLog.warn("Floor {} battle {} of run {} is still on turn {} {} s after it started (started={}):{}",
                        binding.floorIndex(), entry.getKey(), binding.runId(), battle.getTurn(), PROBE_DELAY_MILLIS / 1000,
                        battle.getStarted(), actors);
                nudgeIdleOpponents(battle, binding);
            } catch (RuntimeException ex) {
                TowerLog.errorOnce("battle-probe", "The quiet battle probe failed", ex);
            }
        }
    }

    /**
     * Cobblemon can drop the opponent's request when the player answers first, so the AI's choice never reaches
     * Showdown and the turn waits forever (seen live: only {@code >p1} written). Showdown picks a legal move for an
     * AI side told {@code default}; done once, only when every actor is idle on turn 1.
     */
    private static void nudgeIdleOpponents(PokemonBattle battle, Binding binding) {
        for (var actor : battle.getActors()) {
            if (actor.getMustChoose() || actor.getRequest() != null || !actor.getResponses().isEmpty()) return;
        }
        for (var actor : battle.getActors()) {
            if (!(actor instanceof com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor)) continue;
            TowerLog.warn("Floor {} battle of run {}: telling the idle opponent {} to choose", binding.floorIndex(),
                    binding.runId(), actor.getShowdownId());
            battle.writeShowdownAction(">" + actor.getShowdownId() + " default");
        }
    }

    private static BattleStartResult startPve(ServerPlayer player, PokemonEntity opponent, boolean exhibition) {
        return BattleBuilder.INSTANCE.pve(
                player,
                opponent,
                // Leading Pokemon: null lets Cobblemon pick the party's first able one, which is the
                // same choice a wild encounter makes.
                null,
                BattleFormat.Companion.getGEN_9_SINGLES(),
                // Real party, not healed: a floor is fought with what is left (TDS #16). An exhibition uses a healed
                // clone.
                exhibition,
                exhibition,
                Float.MAX_VALUE,
                // The real party, not null: the Kotlin method rejects null.
                Cobblemon.INSTANCE.getStorage().getParty(player));
    }

    /** Whether {@code entity} is a Pokemon in a live battle; the post-crash sweep must not remove those. */
    public static boolean inLiveBattle(net.minecraft.world.entity.Entity entity) {
        return entity instanceof PokemonEntity pokemon && pokemon.isBattling();
    }

    /** Whether the player is in a live Cobblemon battle right now. */
    public static boolean inBattle(ServerPlayer player) {
        try {
            PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(player);
            return battle != null && !battle.getEnded();
        } catch (RuntimeException ex) {
            TowerLog.errorOnce("inBattle", "Could not read a player's battle state; treating them as not in battle", ex);
            return false;
        }
    }

    /** Why a battle did not start, in words: the result's own toString is only an object id. */
    private static String describe(BattleStartResult result, ServerPlayer player) {
        if (result instanceof ErroredBattleStart errored) {
            List<String> parts = new ArrayList<>();
            for (BattleStartError error : errored.getGeneralErrors()) {
                parts.add(error.getMessageFor(player).getString());
            }
            parts.add("participants: " + errored.getParticipantErrors());
            return String.join("; ", parts);
        }
        return String.valueOf(result);
    }

    private static PokemonEntity spawn(ServerLevel level, EncounterSnapshot snapshot, BlockPos where) {
        Pokemon pokemon = build(snapshot);
        if (pokemon == null) return null;
        UncatchableProperty.INSTANCE.uncatchable().apply(pokemon);
        pokemon.setCurrentHealth(pokemon.getMaxHealth());

        PokemonEntity entity = pokemon.sendOut(level, Vec3.atBottomCenterOf(where), null, spawned -> {
            // Persistent so it cannot despawn mid-battle, and outside the spawn cap so a tower never
            // eats the overworld's budget for mobs.
            spawned.addTag(OPPONENT_TAG);
            spawned.setPersistenceRequired();
            spawned.setCountsTowardsSpawnCap(false);
            spawned.setInvulnerable(true);
            return Unit.INSTANCE;
        });
        if (entity == null) {
            TowerLog.error("Cobblemon did not create an entity for tower opponent {}", snapshot.species());
        }
        return entity;
    }

    /**
     * Parses a snapshot into a Pokemon, falling back to the base species if its aspects are unrecognized (TDS #85).
     */
    private static Pokemon build(EncounterSnapshot snapshot) {
        try {
            return PokemonProperties.Companion.parse(snapshot.toProperties(), " ", "=").create();
        } catch (RuntimeException ex) {
            if (snapshot.aspects().isEmpty()) {
                TowerLog.error("Could not build a tower opponent from '{}': {}", snapshot.toProperties(), ex.toString());
                return null;
            }
            TowerLog.warn("Aspect(s) {} not recognized for tower opponent {}, falling back to the base species: {}",
                    snapshot.aspects(), snapshot.species(), ex.toString());
            try {
                return PokemonProperties.Companion.parse(snapshot.toProperties(false), " ", "=").create();
            } catch (RuntimeException fallbackEx) {
                TowerLog.error("Could not build a tower opponent from '{}' even without aspects: {}",
                        snapshot.toProperties(false), fallbackEx.toString());
                return null;
            }
        }
    }

    /** Contained: an exception here would unwind into Cobblemon's battle loop and the server tick. */
    private static void onVictorySafely(BattleVictoryEvent event) {
        try {
            onVictory(event);
        } catch (RuntimeException ex) {
            TowerLog.error("A tower floor failed to handle a battle result", ex);
        }
    }

    /** A faint proves the battle is still moving. Contained like the victory handler. */
    private static void onFaintedSafely(BattleFaintedEvent event) {
        try {
            UUID battleId = event.getBattle().getBattleId();
            if (BY_BATTLE.containsKey(battleId)) LAST_ACTIVITY.put(battleId, System.currentTimeMillis());
        } catch (RuntimeException ex) {
            TowerLog.error("A tower floor failed to note a faint", ex);
        }
    }

    private static void onVictory(BattleVictoryEvent event) {
        Binding binding = forget(event.getBattle().getBattleId());
        if (binding == null) return;  // somebody else's battle; the reason this index exists

        boolean playerWon = BattleOutcome.playerWon(
                event.getWinners().stream().map(actor -> actor.getUuid()).toList(), binding.playerId());
        MinecraftServer server = serverFor(event.getBattle());
        if (server == null) {
            TowerLog.error("Battle of run {} floor {} ended with no server to report it to", binding.runId(),
                    binding.floorIndex());
            return;
        }
        discardOpponent(server, binding);
        listener.onResolved(server, binding, playerWon);
    }

    /** The running server; a participant may already be gone by the time a result arrives. */
    private static MinecraftServer serverFor(PokemonBattle battle) {
        MinecraftServer server = runningServer;
        if (server != null) return server;
        return battle.getPlayers().isEmpty() ? null : battle.getPlayers().get(0).getServer();
    }

    /** A fled battle ends without a victory event; one stopped from outside is left to the watchdog. */
    private static void onFledSafely(BattleFledEvent event) {
        try {
            resolveWithoutWinner(event.getBattle(), "was fled");
        } catch (RuntimeException ex) {
            TowerLog.error("A tower floor failed to handle a fled battle", ex);
        }
    }

    /**
     * A player who left a floor battle has lost it. Idempotent: whoever takes the binding first resolves it.
     * Deferred a tick so Cobblemon finishes ending the battle before the floor moves the player.
     */
    private static void resolveWithoutWinner(PokemonBattle battle, String how) {
        Binding binding = forget(battle.getBattleId());
        if (binding == null) return;
        MinecraftServer server = serverFor(battle);
        if (server == null) {
            TowerLog.error("Battle of run {} floor {} {} with no server to report it to", binding.runId(),
                    binding.floorIndex(), how);
            return;
        }
        TowerLog.info("Floor {} battle of run {} {}; counted as lost", binding.floorIndex(), binding.runId(), how);
        server.execute(() -> {
            discardOpponentNow(server, binding);
            listener.onResolved(server, binding, false);
        });
    }

    /**
     * Removes a tower opponent that is in no battle of ours and in none of Cobblemon's: left standing by a battle that ended
     * some other way. Only entities this adapter tagged, only in the tower dimension, every five seconds.
     */
    static int sweepOrphanOpponents(MinecraftServer server) {
        ServerLevel level = com.cobbletowers.instance.TowerDimension.level(server);
        if (level == null) return 0;
        java.util.Set<UUID> bound = new java.util.HashSet<>();
        for (Binding binding : BY_BATTLE.values()) bound.add(binding.opponentEntity());
        List<? extends PokemonEntity> strays = level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(PokemonEntity.class),
                entity -> entity.getTags().contains(OPPONENT_TAG) && entity.tickCount > ORPHAN_GRACE_TICKS
                        && !entity.isBattling() && !bound.contains(entity.getUUID()));
        for (PokemonEntity stray : strays) {
            TowerLog.warn("Removing a stray tower opponent ({}) that is in no battle", stray.getPokemon().getSpecies().getName());
            stray.discard();
        }
        return strays.size();
    }

    /** Removes a run's opponents and forgets its battles, on every way out that is not a won battle. */
    public static int endRun(MinecraftServer server, UUID runId) {
        List<UUID> battles = BATTLES_BY_RUN.remove(runId);
        if (battles == null) return 0;
        int ended = 0;
        for (UUID battleId : List.copyOf(battles)) {
            Binding binding = forget(battleId);
            if (binding == null) continue;
            closeBattle(server, battleId);
            discardOpponent(server, binding);
            ended++;
        }
        return ended;
    }

    /** Whether the player is a participant in a battle that has not ended, ours or CobbleRaids'. */
    public static boolean inLiveBattle(ServerPlayer player) {
        return inBattle(player);
    }

    /** Ends whatever battle a player is in, including one CobbleRaids started. @return true if one was ended */
    public static boolean endBattleOf(ServerPlayer player) {
        try {
            PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(player);
            if (battle == null || battle.getEnded()) return false;
            onServerThread(player.getServer(), () -> {
                try {
                    if (!battle.getEnded()) battle.end();
                } catch (RuntimeException ex) {
                    TowerLog.error("Could not end the battle of " + player.getUUID(), ex);
                }
            });
            return true;
        } catch (RuntimeException ex) {
            TowerLog.error("Could not end the battle of " + player.getUUID(), ex);
            return false;
        }
    }

    private static void discardOpponent(MinecraftServer server, Binding binding) {
        onServerThread(server, () -> discardOpponentNow(server, binding));
    }

    private static void discardOpponentNow(MinecraftServer server, Binding binding) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(binding.opponentEntity());
            if (entity != null) {
                entity.discard();
                return;
            }
        }
        // Just sent out and not yet findable by id (a disconnect in the first ticks of a battle): try again each tick.
        PENDING_DISCARDS.put(binding.opponentEntity(), PENDING_DISCARD_TICKS);
    }

    private static void retryPendingDiscards(MinecraftServer server) {
        if (PENDING_DISCARDS.isEmpty()) return;
        PENDING_DISCARDS.replaceAll((id, left) -> left - 1);
        PENDING_DISCARDS.entrySet().removeIf(entry -> {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(entry.getKey());
                if (entity != null) {
                    entity.discard();
                    return true;
                }
            }
            return entry.getValue() <= 0;
        });
    }

    /** Ends one player's battle and removes their opponent, leaving the floor alone. */
    public static boolean endPlayer(MinecraftServer server, UUID runId, UUID playerId) {
        for (UUID battleId : List.copyOf(BATTLES_BY_RUN.getOrDefault(runId, List.of()))) {
            Binding binding = BY_BATTLE.get(battleId);
            if (binding == null || !binding.playerId().equals(playerId)) continue;
            forget(battleId);
            closeBattle(server, battleId);
            discardOpponent(server, binding);
            return true;
        }
        return false;
    }

    /**
     * Ends the Cobblemon battle itself. The index entry is removed first so the victory event cannot score a
     * cancelled battle.
     */
    private static void closeBattle(MinecraftServer server, UUID battleId) {
        onServerThread(server, () -> {
            try {
                PokemonBattle battle = BattleRegistry.getBattle(battleId);
                if (battle != null && !battle.getEnded()) battle.end();
            } catch (RuntimeException ex) {
                TowerLog.error("Could not end tower battle " + battleId, ex);
            }
        });
    }

    /**
     * Runs {@code task} on the server thread: now if already there, else queued in order. Disconnects arrive on Netty
     * threads, which must not touch Showdown's GraalJS context.
     */
    private static void onServerThread(MinecraftServer server, Runnable task) {
        com.cobbletowers.runtime.ServerThread.run(server, task);
    }

    /** When each of a run's players last saw their battle do something. */
    public static Map<UUID, Long> lastActivityByPlayer(UUID runId) {
        Map<UUID, Long> activity = new LinkedHashMap<>();
        for (UUID battleId : BATTLES_BY_RUN.getOrDefault(runId, List.of())) {
            Binding binding = BY_BATTLE.get(battleId);
            if (binding == null) continue;
            activity.put(binding.playerId(), LAST_ACTIVITY.getOrDefault(battleId, binding.startedAt()));
        }
        return activity;
    }

    /** Every battle a run has open, for the watchdog to judge. */
    public static List<Binding> battlesOf(UUID runId) {
        List<UUID> battles = BATTLES_BY_RUN.get(runId);
        if (battles == null) return List.of();
        List<Binding> open = new ArrayList<>(battles.size());
        for (UUID battleId : battles) {
            Binding binding = BY_BATTLE.get(battleId);
            if (binding != null) open.add(binding);
        }
        return open;
    }

    public static int activeBattles() {
        return BY_BATTLE.size();
    }

    /** Memory hygiene at shutdown. The subscription itself lives as long as the JVM, by design. */
    public static int onServerStopped(MinecraftServer server) {
        int held = BY_BATTLE.size();
        for (Binding binding : List.copyOf(BY_BATTLE.values())) discardOpponent(server, binding);
        INDEX.clear();
        PENDING_DISCARDS.clear();
        sweepTicks = 0;
        pollTicks = 0;
        runningServer = null;
        return held;
    }
}
