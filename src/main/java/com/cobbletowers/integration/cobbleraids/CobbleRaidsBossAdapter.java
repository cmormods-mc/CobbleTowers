package com.cobbletowers.integration.cobbleraids;

import com.cobbleraids.api.CobbleRaidsApi;
import com.cobbleraids.api.RaidBossDescriptor;
import com.cobbleraids.api.RaidEncounterHandle;
import com.cobbleraids.api.RaidEncounterOutcome;
import com.cobbleraids.api.RaidEncounterResult;
import com.cobbletowers.api.boss.TowerBossDefinition;
import com.cobbletowers.api.boss.TowerBossEncounter;
import com.cobbletowers.api.boss.TowerBossProvider;
import com.cobbletowers.api.boss.TowerBossResult;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Typed anti-corruption adapter between Tower boss contracts and the public CobbleRaids API. */
public final class CobbleRaidsBossAdapter implements TowerBossProvider {
    private final Map<UUID, RaidEncounterHandle> active = new ConcurrentHashMap<>();

    @Override
    public List<TowerBossDefinition> bosses() {
        return CobbleRaidsApi.encounters().bosses().stream()
                .map(CobbleRaidsBossAdapter::toTowerDefinition)
                .toList();
    }

    @Override
    public Optional<TowerBossDefinition> boss(ResourceLocation definitionId) {
        return CobbleRaidsApi.encounters().boss(definitionId).map(CobbleRaidsBossAdapter::toTowerDefinition);
    }

    @Override
    public TowerBossEncounter start(
            ServerLevel level,
            Vec3 bossPosition,
            Collection<ServerPlayer> participants,
            ResourceLocation definitionId,
            double healthMultiplier,
            Consumer<TowerBossResult> completion
    ) {
        RaidEncounterHandle handle = CobbleRaidsApi.encounters().start(
                level,
                bossPosition,
                participants,
                definitionId,
                healthMultiplier,
                result -> complete(result, completion)
        );
        RaidEncounterHandle previous = active.putIfAbsent(handle.encounterId(), handle);
        if (previous != null) {
            CobbleRaidsApi.encounters().abort(handle);
            throw new IllegalStateException("Duplicate CobbleRaids encounter id: " + handle.encounterId());
        }
        return new TowerBossEncounter(handle.encounterId(), handle.definitionId());
    }

    @Override
    public boolean withdraw(TowerBossEncounter encounter, ServerPlayer player) {
        RaidEncounterHandle handle = active.get(encounter.encounterId());
        return handle != null && handle.definitionId().equals(encounter.definitionId())
                && CobbleRaidsApi.encounters().withdraw(handle, player);
    }

    @Override
    public boolean abort(TowerBossEncounter encounter) {
        RaidEncounterHandle handle = active.get(encounter.encounterId());
        return handle != null && handle.definitionId().equals(encounter.definitionId())
                && CobbleRaidsApi.encounters().abort(handle);
    }

    /** Releases only adapter-owned handle state; CobbleRaids owns battle cleanup itself. */
    public int clear() {
        int dropped = active.size();
        active.clear();
        return dropped;
    }

    private void complete(RaidEncounterResult result, Consumer<TowerBossResult> completion) {
        active.remove(result.encounterId());
        completion.accept(new TowerBossResult(
                result.encounterId(),
                result.definitionId(),
                toTowerOutcome(result.outcome()),
                result.participants(),
                result.activeParticipants(),
                result.elapsedCombatTicks(),
                result.contribution()
        ));
    }

    private static TowerBossDefinition toTowerDefinition(RaidBossDescriptor descriptor) {
        return new TowerBossDefinition(
                descriptor.id(),
                descriptor.species(),
                descriptor.rarityTier(),
                descriptor.level(),
                descriptor.baseHealth(),
                descriptor.maxPlayers()
        );
    }

    private static TowerBossResult.Outcome toTowerOutcome(RaidEncounterOutcome outcome) {
        return switch (outcome) {
            case VICTORY -> TowerBossResult.Outcome.VICTORY;
            case DEFEAT -> TowerBossResult.Outcome.DEFEAT;
            case ABORTED -> TowerBossResult.Outcome.ABORTED;
        };
    }
}
