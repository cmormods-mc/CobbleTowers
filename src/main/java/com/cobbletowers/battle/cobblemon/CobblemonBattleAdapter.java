package com.cobbletowers.battle.cobblemon;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
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

    private static final Map<UUID, Binding> BY_BATTLE = new LinkedHashMap<>();
    private static final Map<UUID, List<UUID>> BATTLES_BY_RUN = new HashMap<>();
    /** When each battle last did something, for the watchdog (a start or a faint). */
    private static final Map<UUID, Long> LAST_ACTIVITY = new HashMap<>();
    private static boolean installed;

    /** Marks an entity this adapter spawned as a tower opponent, so a stray one can be told from anything else. */
    static final String OPPONENT_TAG = "cobbletowers_opponent";
    private static final int ORPHAN_SWEEP_TICKS = 100;
    /** An opponent must have stood this long (ticks) before it can be called stray: the battle starts the tick it spawns. */
    private static final int ORPHAN_GRACE_TICKS = 200;
    private static int sweepTicks;

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
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
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
        BY_BATTLE.put(battleId, binding);
        LAST_ACTIVITY.put(battleId, binding.startedAt());
        BATTLES_BY_RUN.computeIfAbsent(runId, key -> new ArrayList<>()).add(battleId);
        // An Echo's Pokemon is named by what is actually built, not by the pool species the slot was drawn from.
        String opponentName = snapshot.echoProperties().map(com.cobbletowers.echo.EchoPolicy::speciesOf)
                .orElse(snapshot.species().toString());
        TowerLog.info("Floor {} battle {} started{}: {} vs {} at level {}", floorIndex, battleId,
                exhibition ? " (Echo duel)" : "", player.getGameProfile().getName(), opponentName, snapshot.level());
        return Optional.of(battleId);
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
        LAST_ACTIVITY.remove(event.getBattle().getBattleId());
        Binding binding = BY_BATTLE.remove(event.getBattle().getBattleId());
        if (binding == null) return;  // somebody else's battle; the reason this index exists

        List<UUID> battles = BATTLES_BY_RUN.get(binding.runId());
        if (battles != null) {
            battles.remove(event.getBattle().getBattleId());
            if (battles.isEmpty()) BATTLES_BY_RUN.remove(binding.runId());
        }

        boolean playerWon = event.getWinners().stream()
                .anyMatch(actor -> binding.playerId().equals(actor.getUuid()));
        MinecraftServer server = event.getBattle().getPlayers().isEmpty()
                ? null : event.getBattle().getPlayers().get(0).getServer();
        if (server != null) {
            discardOpponent(server, binding);
            listener.onResolved(server, binding, playerWon);
        }
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
            Binding binding = BY_BATTLE.remove(battleId);
            LAST_ACTIVITY.remove(battleId);
            if (binding == null) continue;
            closeBattle(server, battleId);
            discardOpponent(server, binding);
            ended++;
        }
        return ended;
    }

    /** Whether the player is a participant in a battle that has not ended, ours or CobbleRaids'. */
    public static boolean inLiveBattle(ServerPlayer player) {
        try {
            PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(player);
            return battle != null && !battle.getEnded();
        } catch (RuntimeException ex) {
            return false;
        }
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
    }

    /** Ends one player's battle and removes their opponent, leaving the floor alone. */
    public static boolean endPlayer(MinecraftServer server, UUID runId, UUID playerId) {
        for (Map.Entry<UUID, Binding> entry : Map.copyOf(BY_BATTLE).entrySet()) {
            Binding binding = entry.getValue();
            if (!binding.runId().equals(runId) || !binding.playerId().equals(playerId)) continue;
            BY_BATTLE.remove(entry.getKey());
            LAST_ACTIVITY.remove(entry.getKey());
            List<UUID> battles = BATTLES_BY_RUN.get(runId);
            if (battles != null) {
                battles.remove(entry.getKey());
                if (battles.isEmpty()) BATTLES_BY_RUN.remove(runId);
            }
            closeBattle(server, entry.getKey());
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
        BY_BATTLE.clear();
        BATTLES_BY_RUN.clear();
        LAST_ACTIVITY.clear();
        return held;
    }
}
