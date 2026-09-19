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
import com.cobblemon.mod.common.battles.BattleStartResult;
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
 * The only place in this mod that names a Cobblemon battle (TDS #31).
 *
 * <p>A tower floor's prerequisite is one battle per player, run at the same time. Cobblemon's
 * {@code BattleBuilder.pve} takes a single player, and several players against one opponent is what
 * forced CobbleRaids into custom actors and mixins -- so the shared fight is the boss, which
 * CobbleRaids' own API already runs, and the ordinary opponents are simply one battle each. Nothing
 * here touches Cobblemon internals, and no single battle ever holds four players, which is the shape
 * that stalled Showdown for CobbleRaids.
 *
 * <p>Two rules this keeps that a tower depends on:
 * <ul>
 *   <li><b>Parties are not cloned and not healed first</b>, so damage and PP carry between floors
 *       (TDS #16: no free healing). The battle is fought with the real party.</li>
 *   <li><b>Opponents are uncatchable</b> (TDS #78). A tower Pokemon is never captured.</li>
 * </ul>
 */
public final class CobblemonBattleAdapter {

    /** What a running battle belongs to. Ids only -- an entity reference here would pin its level. */
    public record Binding(UUID runId, UUID playerId, int floorIndex, ResourceLocation species, UUID opponentEntity,
                          long startedAt) {}

    private static final Map<UUID, Binding> BY_BATTLE = new LinkedHashMap<>();
    private static final Map<UUID, List<UUID>> BATTLES_BY_RUN = new HashMap<>();
    /**
     * When each battle last did something, for the watchdog to read.
     *
     * <p>Cobblemon raises no per-turn event, so the honest signals are the two it does raise: the
     * battle starting, and something fainting in it. A battle that has produced neither for a long
     * time is what "stopped" can be said to mean from out here without reaching into Showdown.
     */
    private static final Map<UUID, Long> LAST_ACTIVITY = new HashMap<>();
    private static boolean installed;

    private CobblemonBattleAdapter() {}

    /** What happens when a floor's battle ends. Implemented by the encounter, called by this. */
    public interface Listener {
        void onResolved(MinecraftServer server, Binding binding, boolean playerWon);
    }

    private static Listener listener = (server, binding, won) -> {};

    /**
     * Subscribes to Cobblemon's battle events, once.
     *
     * <p>The events are global -- every battle on the server raises them, ours and everybody else's.
     * Routing by battle id is therefore the whole correctness of this class: miss the filter and one
     * floor ends another's battle, or a wild encounter in the overworld clears a tower floor.
     */
    public static void install(Listener encounterListener) {
        listener = encounterListener;
        if (installed) return;
        installed = true;
        CobblemonEvents.BATTLE_VICTORY.subscribe(CobblemonBattleAdapter::onVictorySafely);
        CobblemonEvents.BATTLE_FAINTED.subscribe(CobblemonBattleAdapter::onFaintedSafely);
        TowerLog.info("Cobblemon battle adapter installed");
    }

    /**
     * Spawns an opponent and starts one player's battle with it.
     *
     * @return the battle's id, or empty when the opponent could not be made or the battle refused
     */
    public static Optional<UUID> start(ServerLevel level, ServerPlayer player, EncounterSnapshot snapshot,
                                       BlockPos where, UUID runId, int floorIndex) {
        PokemonEntity opponent = spawn(level, snapshot, where);
        if (opponent == null) return Optional.empty();

        BattleStartResult result = BattleBuilder.INSTANCE.pve(
                player,
                opponent,
                // Leading Pokemon: null lets Cobblemon pick the party's first able one, which is the
                // same choice a wild encounter makes.
                null,
                BattleFormat.Companion.getGEN_9_SINGLES(),
                // Do not clone the party, and do not heal it first: a tower floor is fought with what
                // the party has left, which is the whole of TDS #16.
                false,
                false,
                Float.MAX_VALUE,
                // The player's real party, not null: the full-arity method is Kotlin non-null, and
                // only the generated pve$default overload fills this in. Passing null threw
                // "Parameter specified as non-null is null" the first time a floor was played, and
                // no unit test could have seen it -- there is no Cobblemon runtime in one.
                Cobblemon.INSTANCE.getStorage().getParty(player));

        if (!(result instanceof SuccessfulBattleStart success)) {
            TowerLog.error("Cobblemon refused a tower battle for {} on floor {}: {}",
                    player.getGameProfile().getName(), floorIndex, result);
            opponent.discard();
            return Optional.empty();
        }

        UUID battleId = success.getBattle().getBattleId();
        Binding binding = new Binding(runId, player.getUUID(), floorIndex, snapshot.species(), opponent.getUUID(),
                System.currentTimeMillis());
        BY_BATTLE.put(battleId, binding);
        LAST_ACTIVITY.put(battleId, binding.startedAt());
        BATTLES_BY_RUN.computeIfAbsent(runId, key -> new ArrayList<>()).add(battleId);
        TowerLog.info("Floor {} battle {} started: {} vs {} at level {}", floorIndex, battleId,
                player.getGameProfile().getName(), snapshot.species(), snapshot.level());
        return Optional.of(battleId);
    }

    private static PokemonEntity spawn(ServerLevel level, EncounterSnapshot snapshot, BlockPos where) {
        Pokemon pokemon;
        try {
            // The property string is what Cobblemon's own /pokegive parses, so species, level and any
            // aspect all take one path rather than three setters that drift apart.
            pokemon = PokemonProperties.Companion.parse(snapshot.toProperties(), " ", "=").create();
        } catch (RuntimeException ex) {
            TowerLog.error("Could not build a tower opponent from '{}': {}", snapshot.toProperties(), ex.toString());
            return null;
        }
        UncatchableProperty.INSTANCE.uncatchable().apply(pokemon);
        pokemon.setCurrentHealth(pokemon.getMaxHealth());

        PokemonEntity entity = pokemon.sendOut(level, Vec3.atBottomCenterOf(where), null, spawned -> {
            // Persistent so it cannot despawn mid-battle, and outside the spawn cap so a tower never
            // eats the overworld's budget for mobs.
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
     * Contained, because of where this is called from.
     *
     * <p>Cobblemon raises these from its own battle loop on the server thread, so an exception
     * thrown back into it does not merely lose one floor -- it unwinds through Cobblemon's event
     * dispatch and onto the tick, taking every other battle on the server with it. One broken tower
     * floor is the better failure.
     */
    private static void onVictorySafely(BattleVictoryEvent event) {
        try {
            onVictory(event);
        } catch (RuntimeException ex) {
            TowerLog.error("A tower floor failed to handle a battle result", ex);
        }
    }

    /**
     * A faint in one of our battles: proof the battle is still moving.
     *
     * <p>Guarded like the victory handler, and for the same reason -- this runs inside Cobblemon's
     * own battle loop, where an exception unwinds onto the tick and takes every other battle with it.
     */
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
     * Removes a run's opponents and forgets its battles.
     *
     * <p>Called on every way out that is not a won battle -- abandoning, parking, shutting down. An
     * entity left standing in a cell is exactly what P3's cleanup quarantines the cell for, and
     * quarantining a cell because this phase forgot to tidy up would be a poor way to find out.
     */
    public static int endRun(MinecraftServer server, UUID runId) {
        List<UUID> battles = BATTLES_BY_RUN.remove(runId);
        if (battles == null) return 0;
        int ended = 0;
        for (UUID battleId : List.copyOf(battles)) {
            Binding binding = BY_BATTLE.remove(battleId);
            LAST_ACTIVITY.remove(battleId);
            if (binding == null) continue;
            closeBattle(battleId);
            discardOpponent(server, binding);
            ended++;
        }
        return ended;
    }

    private static void discardOpponent(MinecraftServer server, Binding binding) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(binding.opponentEntity());
            if (entity != null) {
                entity.discard();
                return;
            }
        }
    }

    /**
     * Ends one player's battle and removes their opponent, leaving the rest of the floor alone.
     *
     * <p>What a disconnect and the watchdog both need: {@link #endRun} would take the whole floor
     * down with it, which is precisely what the user asked not to happen when one player stops
     * answering.
     */
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
            closeBattle(entry.getKey());
            discardOpponent(server, binding);
            return true;
        }
        return false;
    }

    /**
     * Ends the Cobblemon battle itself, not merely this mod's memory of it.
     *
     * <p>Forgetting a battle and discarding its opponent leaves the player sitting in a battle UI
     * they cannot leave, with Showdown still holding their actors. {@code end()} sends the end
     * packet, lets entity actors clear their battle id, and closes the registry entry -- the same
     * call CobbleRaids makes on every path where no win packet is coming.
     *
     * <p>The index entry is removed before this is called, so a victory event raised on the way out
     * finds nothing to resolve and cannot score a battle that was cancelled.
     */
    private static void closeBattle(UUID battleId) {
        try {
            PokemonBattle battle = BattleRegistry.getBattle(battleId);
            if (battle != null && !battle.getEnded()) battle.end();
        } catch (RuntimeException ex) {
            TowerLog.error("Could not end tower battle " + battleId, ex);
        }
    }

    /**
     * When each of a run's players last saw their battle do something.
     *
     * <p>Exactly what the watchdog needs and nothing more: an id and a clock reading, so the verdict
     * itself can be decided without a server.
     */
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
