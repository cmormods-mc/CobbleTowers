package com.cobbletowers.economy;

import com.cobbletowers.TowerLog;
import com.cobbletowers.persistence.PendingLibSettlement;
import com.cobbletowers.persistence.TowerLibSettlementStore;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntPredicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * Pays a boss victory in AscensionLib's wallet, reached by reflection since the library is optional. Each settlement
 * is written to disk ({@link TowerLibSettlementStore}) before the library is asked and removed once confirmed; the
 * library pays once per encounter, so retries every {@link #RETRY_TICKS} and at start are safe. A failure never
 * touches the run.
 */
public final class AscensionLibRewards {

    private static final String MOD_ID = "ascensionlib";
    private static final String REWARDS = "com.ascensionlib.AscensionRewards";

    /** How often unconfirmed settlements are tried again (30 seconds). */
    static final int RETRY_TICKS = 20 * 30;

    private static boolean resolved;
    private static Method settleBoss;
    private static Method settleScouters;
    private static Method settleTrial;
    private static int tickCounter;

    private AscensionLibRewards() {}

    /** Wires the retry: once at server start, then on a timer. Called once at startup. */
    public static void install() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> retryPending(server, System.currentTimeMillis()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter < RETRY_TICKS) return;
            tickCounter = 0;
            try {
                retryPending(server, System.currentTimeMillis());
            } catch (RuntimeException ex) {
                TowerLog.error("Retrying AscensionLib settlements failed", ex);
            }
        });
    }

    /**
     * The first floor of the segment a milestone boss pays for: the floor after the previous milestone, or 1.
     * @param isMilestone whether a run floor is a milestone floor
     */
    public static int segmentStart(IntPredicate isMilestone, int bossFloor) {
        for (int floor = bossFloor - 1; floor >= 1; floor--) {
            if (isMilestone.test(floor)) return floor + 1;
        }
        return 1;
    }

    /** Pays a milestone boss victory from the library's bands to {@code players}, every member of the run. */
    public static void settleMilestone(MinecraftServer server, UUID encounterId, String outcome, int fromFloor,
                                       int bossFloor, Collection<UUID> players, long now) {
        // Flushed to disk before the attempt: this is real value, and a crash must not forget it is owed.
        submit(server, new PendingLibSettlement(encounterId, PendingLibSettlement.Kind.MILESTONE, outcome, fromFloor,
                bossFloor, false, List.copyOf(players), now, 0), true);
    }

    /**
     * Rolls the Scouter drop for a cleared floor, once per player. Not flushed first: a crash costs at most a small
     * roll.
     */
    public static void settleScouterDrops(MinecraftServer server, UUID encounterId, String outcome, boolean keenEyeFloor,
                                          Collection<UUID> players, long now) {
        submit(server, new PendingLibSettlement(encounterId, PendingLibSettlement.Kind.SCOUTER_DROPS, outcome, 0, 0,
                keenEyeFloor, List.copyOf(players), now, 0), false);
    }

    /**
     * Pays a won Trial at its rank from the library's trial bands. Flushed first because it is real value; the rank
     * rides in the boss-floor field.
     */
    public static void settleTrial(MinecraftServer server, UUID encounterId, String outcome, int rank,
                                   Collection<UUID> players, long now) {
        submit(server, new PendingLibSettlement(encounterId, PendingLibSettlement.Kind.TRIAL, outcome, 0, rank, false,
                List.copyOf(players), now, 0), true);
    }

    /** Tries every unconfirmed settlement again; gives up on, and says so for, any older than a week. */
    public static void retryPending(MinecraftServer server, long now) {
        TowerLibSettlementStore store = TowerLibSettlementStore.get(server);
        if (store.size() == 0 || resolve(PendingLibSettlement.Kind.MILESTONE) == null) return;
        for (PendingLibSettlement settlement : store.all()) {
            if (settlement.expired(now)) {
                store.remove(settlement);
                TowerLog.error("Giving up on AscensionLib {} for encounter {} after {} attempt(s); still owed: {}",
                        settlement.kind(), settlement.encounterId(), settlement.attempts(), settlement.players());
                continue;
            }
            attempt(store, settlement);
        }
    }

    private static void submit(MinecraftServer server, PendingLibSettlement settlement, boolean flush) {
        // Not installed (or a contract that does not match): there is nothing to pay and nothing to remember.
        if (resolve(PendingLibSettlement.Kind.MILESTONE) == null) return;
        TowerLibSettlementStore store = TowerLibSettlementStore.get(server);
        store.put(settlement);
        if (flush) store.checkpoint(server);
        attempt(store, settlement);
    }

    /** One call to the library, then the entry is trimmed to whoever is still owed (or removed). */
    private static void attempt(TowerLibSettlementStore store, PendingLibSettlement settlement) {
        Map<?, ?> statuses = invoke(settlement);
        Optional<PendingLibSettlement> remaining = settlement.afterAttempt(statuses);
        remaining.ifPresentOrElse(store::put, () -> store.remove(settlement));
        if (statuses != null) {
            statuses.forEach((player, status) -> {
                // GRANTED and ALREADY_GRANTED are the normal answers; anything else is worth one line.
                // A retry that fails the same way again is not said again, or a refused wallet fills the log.
                boolean fresh = settlement.attempts() == 0 || remaining.isEmpty();
                if (fresh && !"GRANTED".equals(String.valueOf(status)) && !"ALREADY_GRANTED".equals(String.valueOf(status))) {
                    TowerLog.warn("AscensionLib {} for encounter {}: player {} -> {}{}", settlement.kind(),
                            settlement.encounterId(), player, status,
                            remaining.isPresent() ? " (will retry)" : "");
                }
            });
        }
    }

    /** The library's per-player statuses, or null when the call could not be made or threw (nothing confirmed). */
    private static Map<?, ?> invoke(PendingLibSettlement settlement) {
        Method method = resolve(settlement.kind());
        if (method == null) return null;
        try {
            Object result = switch (settlement.kind()) {
                case MILESTONE -> method.invoke(null, settlement.encounterId(), settlement.outcome(), settlement.fromFloor(),
                        settlement.bossFloor(), settlement.players());
                case TRIAL -> method.invoke(null, settlement.encounterId(), settlement.outcome(), settlement.bossFloor(),
                        settlement.players());
                case SCOUTER_DROPS -> method.invoke(null, settlement.encounterId(), settlement.outcome(),
                        settlement.keenEyeFloor(), settlement.players());
            };
            return result instanceof Map<?, ?> statuses ? statuses : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            TowerLog.error("AscensionLib {} for encounter {} could not be settled; will retry",
                    settlement.kind(), settlement.encounterId(), ex);
            return null;
        }
    }

    /** Resolved once; null when the mod is absent or its contract is not what this expects (said in the log). */
    private static Method resolve(PendingLibSettlement.Kind kind) {
        if (!resolved) {
            resolved = true;
            if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return null;
            try {
                Class<?> rewards = Class.forName(REWARDS);
                settleBoss = rewards.getMethod("settleTowerBoss", UUID.class, String.class, int.class, int.class,
                        Collection.class);
                settleScouters = rewards.getMethod("settleScouterDrops", UUID.class, String.class, boolean.class,
                        Collection.class);
                try {
                    // Newer than the others: an older library still pays bosses and Scouters, and trials simply pay
                    // nothing.
                    settleTrial = rewards.getMethod("settleTrial", UUID.class, String.class, int.class, Collection.class);
                } catch (NoSuchMethodException missing) {
                    settleTrial = null;
                }
            } catch (ReflectiveOperationException | LinkageError ex) {
                settleBoss = null;
                settleScouters = null;
                settleTrial = null;
                TowerLog.error("AscensionLib is installed but " + REWARDS + " does not match what CobbleTowers expects",
                        ex);
            }
        }
        return switch (kind) {
            case MILESTONE -> settleBoss;
            case TRIAL -> settleTrial;
            case SCOUTER_DROPS -> settleScouters;
        };
    }
}
