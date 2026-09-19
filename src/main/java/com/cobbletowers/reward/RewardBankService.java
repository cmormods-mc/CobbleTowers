package com.cobbletowers.reward;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.definition.MilestoneDefinition;
import com.cobbletowers.definition.RewardTableDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.modifier.DraftService;
import com.cobbletowers.modifier.ModifierEffects;
import com.cobbletowers.persistence.LedgerEntry;
import com.cobbletowers.persistence.PendingTowerReward;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.TowerPendingRewardStore;
import com.cobbletowers.runtime.TowerRuns;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The one place a run's ledger becomes a real grant (TDS #24, #30).
 *
 * <p>Split the way {@link DraftService} is: the decisions are pure functions over a run and its
 * loaded content, and only {@link #bank} and {@link #sweepUnbanked} write anything.
 */
public final class RewardBankService {

    private RewardBankService() {}

    // ---------------------------------------------------------------- pure

    /**
     * The commit key one grant is made under, deliberately distinct from any transition's own
     * checkpoint key.
     *
     * <p>By the time a grant runs, the transition has already moved the run's state, so the state
     * machine's own replay guard -- a move cannot be applied twice because applying it moves the
     * state -- cannot cover this. This is the key TDS #30 was left unused for.
     */
    static String grantKey(UUID runId, int throughFloor) {
        return "run:" + runId + ":floor:" + throughFloor + ":granted";
    }

    /**
     * Whether this arrival is a real payout point, and the table to price it from if it is.
     *
     * <p>{@code REWARDS_BANKED} always fires the transition on every floor clear -- P1's table has
     * exactly one edge out of {@code FLOOR_RESOLVING} for a non-final floor, so it must. Whether that
     * arrival also converts what has accumulated into an un-forfeitable grant is a separate question,
     * answered here: at {@code INTERMISSION}, only a milestone floor with {@code banksRewards()} true
     * pays out now -- an ordinary floor has no milestone to consult and the ledger simply keeps
     * accumulating, still forfeitable by a later loss. {@code COMPLETED} and {@code CASHED_OUT} always
     * pay out whatever remains, milestone or not, since nothing can forfeit from a terminal state.
     */
    static Optional<RewardTableDefinition> bankPoint(TowerContent content, PersistedRun run) {
        TowerDefinition tower = content.towers().get(run.towerId());
        if (tower == null) return Optional.empty();
        if (run.state() == RunState.INTERMISSION) {
            boolean banks = content.milestoneAt(run.towerId(), run.floorIndex())
                    .map(MilestoneDefinition::banksRewards)
                    .orElse(false);
            if (!banks) return Optional.empty();
        }
        return content.rewardTable(tower.rewardTableId());
    }

    /** The ledger newly priceable: earned since the run last banked, through the floor it is on now. */
    static List<LedgerEntry> unbanked(PersistedRun run) {
        List<LedgerEntry> priced = new ArrayList<>();
        for (LedgerEntry entry : run.ledger()) {
            if (entry.floorIndex() > run.lastBankedFloor() && entry.floorIndex() <= run.floorIndex()) {
                priced.add(entry);
            }
        }
        return List.copyOf(priced);
    }

    /** Who a grant is split across: everybody still a member, not only those who can fight right now. */
    static List<UUID> currentParticipants(PersistedRun run) {
        List<UUID> ids = new ArrayList<>();
        for (PersistedParticipant participant : run.participants()) {
            if (participant.state().isInRun()) ids.add(participant.playerId());
        }
        return List.copyOf(ids);
    }

    /** {@code amount} split as evenly as possible; the remainder goes to the first participants in order. */
    static Map<UUID, Integer> evenSplit(int amount, List<UUID> participants) {
        Map<UUID, Integer> shares = new LinkedHashMap<>();
        if (participants.isEmpty() || amount <= 0) return shares;
        int base = amount / participants.size();
        int remainder = amount % participants.size();
        for (int i = 0; i < participants.size(); i++) {
            int share = base + (i < remainder ? 1 : 0);
            if (share > 0) shares.put(participants.get(i), share);
        }
        return shares;
    }

    // -------------------------------------------------------------- writing

    /**
     * Banks whatever this run has earned since it last banked, if this arrival is a real payout point
     * and it has not already happened.
     *
     * <p>Idempotent by construction: {@link #grantKey} is checked against
     * {@code run.committedTransactions()} before anything is computed, so a replay -- whether from the
     * live arrival path or from {@link #sweepUnbanked} -- is a safe no-op once the key is committed.
     *
     * <p><b>The run record is written before the pending-reward store is touched.</b> The two are
     * separate files and cannot be flushed as one atomic write, so a crash between them fails one way
     * or the other; this order picks which. Written first, a crash after it leaves the key committed
     * with nothing queued for it -- a grant lost, not duplicated. The reverse order fails the other
     * way: a retry that finds no committed key would recompute and re-queue the same items. CobbleRaids
     * reasoned through the identical trade-off for its own reward queue and landed the same way: on an
     * economy, losing a reward is a complaint, duplicating one is an exploit.
     */
    public static void bank(MinecraftServer server, UUID runId, long now) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) return;
        PersistedRun run = found.get();
        if (run.floorIndex() <= run.lastBankedFloor()) return;

        TowerContent content = TowerDefinitionRegistry.content();
        Optional<RewardTableDefinition> table = bankPoint(content, run);
        if (table.isEmpty()) return;

        String key = grantKey(runId, run.floorIndex());
        if (run.hasCommitted(key)) return;

        List<LedgerEntry> priced = unbanked(run);
        ModifierEffects effects = DraftService.effects(run);
        List<RewardValuation.Grant> grants = RewardValuation.value(run.seed(), priced, table.get(), effects);
        List<UUID> participants = currentParticipants(run);

        TowerRuns.save(server, run.banked(run.floorIndex(), key, now), true);
        TowerLog.info("Run {} banked its rewards through floor {}: {}", runId, run.floorIndex(), grants);

        if (grants.isEmpty() || participants.isEmpty()) return;

        TowerPendingRewardStore store = TowerPendingRewardStore.get(server);
        for (RewardValuation.Grant grant : grants) {
            for (Map.Entry<UUID, Integer> share : evenSplit(grant.amount(), participants).entrySet()) {
                store.add(share.getKey(),
                        new PendingTowerReward(runId, run.floorIndex(), grant.item(), share.getValue(), now));
            }
        }
        store.checkpoint(server);

        for (UUID playerId : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) RewardDelivery.deliver(server, player);
        }
    }

    /**
     * Catches the one crash window the live path cannot: {@code COMPLETED} and {@code CASHED_OUT} are
     * terminal, so {@code RunRecovery} never parks them and no event is ever legal against them again
     * -- a crash between their checkpoint and their grant has no other way back. {@code INTERMISSION}
     * needs no such sweep: it is still live, so {@code RunRecovery} parks it into
     * {@code RECOVERY_REQUIRED} on every restart regardless, and resuming it re-enters the same
     * arrival block in {@code RunTransitionService.apply} that {@link #bank} is already wired into --
     * the same self-healing {@code DraftService.open} already relies on for its own idempotent
     * re-open. Scoping this sweep to include {@code INTERMISSION} would be dead code under that
     * ordering; if {@code RunRecovery} ever stops parking live runs unconditionally, that is exactly
     * what should prompt adding it here.
     */
    public static int sweepUnbanked(MinecraftServer server, long now) {
        int banked = 0;
        for (PersistedRun run : TowerRuns.all()) {
            if (run.state() != RunState.COMPLETED && run.state() != RunState.CASHED_OUT) continue;
            if (run.lastBankedFloor() >= run.floorIndex()) continue;
            bank(server, run.runId(), now);
            banked++;
        }
        return banked;
    }
}
