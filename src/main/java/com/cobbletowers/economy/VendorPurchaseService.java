package com.cobbletowers.economy;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.definition.VendorServiceDefinition;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.TowerWalletStore;
import com.cobbletowers.runtime.TowerRuns;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The one place a vendor sale actually happens (TDS #16, #18, #19).
 *
 * <p>Every condition is re-checked here, never trusted from a caller: {@link
 * com.cobbletowers.network.TowerNetworking}'s purchase handler is the only caller today, but nothing
 * about this method assumes that stays true.
 */
public final class VendorPurchaseService {

    /** Why a purchase did or did not go through -- named so a client can show the actual reason. */
    public enum Result {
        SUCCESS, RUN_NOT_FOUND, NOT_INTERMISSION, UNKNOWN_SERVICE, TARGET_NOT_IN_RUN, TARGET_OFFLINE,
        SOLD_OUT, INSUFFICIENT_FUNDS
    }

    private VendorPurchaseService() {}

    public static Result purchase(MinecraftServer server, UUID runId, UUID payingPlayerId, UUID targetPlayerId,
                                  ResourceLocation serviceId) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) return Result.RUN_NOT_FOUND;
        PersistedRun run = found.get();
        // TDS #16's "no automatic free BETWEEN-FLOOR healing": the vendor is not reachable mid-fight.
        if (run.state() != RunState.INTERMISSION) return Result.NOT_INTERMISSION;

        TowerContent content = TowerDefinitionRegistry.content();
        Optional<VendorServiceDefinition> service = content.vendorService(serviceId);
        if (service.isEmpty()) return Result.UNKNOWN_SERVICE;

        boolean targetInRun = run.participants().stream()
                .anyMatch(participant -> participant.playerId().equals(targetPlayerId) && participant.state().isInRun());
        if (!targetInRun) return Result.TARGET_NOT_IN_RUN;

        // Nothing to apply the effect to; refused before anything is charged.
        ServerPlayer target = server.getPlayerList().getPlayer(targetPlayerId);
        if (target == null) return Result.TARGET_OFFLINE;

        int cap = service.get().maxPurchasesPerRun();
        if (cap > 0 && run.purchasesOf(serviceId) >= cap) return Result.SOLD_OUT;

        if (!TowerWalletStore.get(server).debit(payingPlayerId, service.get().priceCobbleDollars())) {
            return Result.INSUFFICIENT_FUNDS;
        }
        // Flushed immediately: the same "the money has already moved" reasoning P9's own grant-then-
        // store ordering uses. A crash between this and the run write below costs a lost purchase
        // count, never a duplicated debit.
        TowerWalletStore.get(server).checkpoint(server);

        VendorServices.apply(service.get().effect(), target);

        long now = System.currentTimeMillis();
        TowerRuns.save(server, run.withVendorPurchase(serviceId, now), false);
        TowerLog.info("Run {}: {} bought {} for {}", runId, payingPlayerId, serviceId, targetPlayerId);
        return Result.SUCCESS;
    }
}
