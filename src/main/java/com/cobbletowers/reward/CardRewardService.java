package com.cobbletowers.reward;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.PlaylistDefinition;
import com.cobbletowers.definition.PlaylistRegistry;
import com.cobbletowers.definition.RentalSetRegistry;
import com.cobbletowers.persistence.PartyJournalEntry;
import com.cobbletowers.persistence.PendingTowerReward;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.TowerCardRewardStore;
import com.cobbletowers.persistence.TowerPartyJournalStore;
import com.cobbletowers.persistence.TowerPendingRewardStore;
import com.cobbletowers.rental.CardRewardPolicy;
import com.cobbletowers.rental.RentalCards;
import com.cobbletowers.runtime.TowerRuns;
import com.cobbletowers.trial.TrialClock;
import com.cobbletowers.trial.TrialService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hands the real CobblemonCards cards a completed rental run has earned (P33b), through the same pending-reward queue everything
 * else uses: delivered at once to a player who is online, and at their next login to one who is not.
 *
 * <p>It reads the team from the party journal, which names every rental and the set it was made from before anything moves, so it
 * works even if the Pokemon are already gone and the player is offline. It is idempotent: the run records that it has been done, and
 * the recovery sweep re-attempts a run that completed in a crash window. A server without the card mod grants nothing and says so.
 */
public final class CardRewardService {

    private CardRewardService() {}

    static String commitKey(UUID runId) {
        return "run:" + runId + ":cards";
    }

    /** Whether a run could have card rewards at all: it is on a playlist that grants them. */
    static Optional<PlaylistDefinition.CardRewards> configOf(PersistedRun run) {
        return run.options().playlist().flatMap(PlaylistRegistry::get).map(PlaylistDefinition::cardRewards)
                .filter(PlaylistDefinition.CardRewards::enabled);
    }

    /** Called when a run arrives at {@code COMPLETED}; does nothing for a run that earns no cards, and nothing twice. */
    public static void onCompleted(MinecraftServer server, UUID runId, long now) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) return;
        PersistedRun run = found.get();
        Optional<PlaylistDefinition.CardRewards> config = configOf(run);
        if (config.isEmpty() || run.hasCommitted(commitKey(runId))) return;

        if (!BuiltInRegistries.ITEM.containsKey(RentalCards.ITEM)) {
            TowerLog.warn("Run {} would earn cards, but {} is not installed on this server, so none are granted.", runId, RentalCards.ITEM);
            TowerRuns.save(server, run.committed(commitKey(runId), now), true);
            return;
        }

        TowerPartyJournalStore journals = TowerPartyJournalStore.get(server);
        TowerCardRewardStore days = TowerCardRewardStore.get(server);
        TowerPendingRewardStore pending = TowerPendingRewardStore.get(server);
        String today = TrialClock.dayKey(TrialService.today());
        boolean practice = run.options().isTrial() && !run.options().scored();
        List<UUID> deliverTo = new ArrayList<>();
        for (UUID player : RewardBankService.currentParticipants(run)) {
            List<PartyJournalEntry.LentCard> team = journals.entryFor(player)
                    .filter(entry -> entry.runId().equals(runId)).map(PartyJournalEntry::cards).orElse(List.of());
            CardRewardPolicy.Decision decision = CardRewardPolicy.decide(config.get(), true, practice, days.runsOn(player, today), team,
                    id -> Optional.ofNullable(net.minecraft.resources.ResourceLocation.tryParse(id)).flatMap(RentalSetRegistry::get));
            if (!decision.grants()) {
                TowerLog.info("Run {}: {} earned no cards ({})", runId, player, decision.outcome());
                continue;
            }
            for (CardRewardPolicy.Granted granted : decision.cards()) {
                pending.add(player, new PendingTowerReward(runId, Math.max(1, run.floorIndex()), RentalCards.ITEM, 1, now,
                        granted.card().toItemTag().toString(), granted.label()));
            }
            days.record(player, today);
            deliverTo.add(player);
            TowerLog.info("Run {}: {} earned {} card(s)", runId, player, decision.cards().size());
        }
        TowerRuns.save(server, run.committed(commitKey(runId), now), true);
        pending.checkpoint(server);
        days.checkpoint(server);
        for (UUID id : deliverTo) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) RewardDelivery.deliver(server, player);
        }
    }

    /** Re-attempts every completed run that never got its cards, once, at server start (a crash between completing and granting). */
    public static int sweep(MinecraftServer server, long now) {
        int done = 0;
        for (PersistedRun run : TowerRuns.all()) {
            if (run.state() != com.cobbletowers.api.tower.RunState.COMPLETED) continue;
            if (configOf(run).isEmpty() || run.hasCommitted(commitKey(run.runId()))) continue;
            onCompleted(server, run.runId(), now);
            done++;
        }
        return done;
    }
}
