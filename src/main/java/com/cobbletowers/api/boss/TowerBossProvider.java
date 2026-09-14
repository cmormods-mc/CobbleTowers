package com.cobbletowers.api.boss;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Boss-engine boundary used by the Tower run engine. */
public interface TowerBossProvider {
    List<TowerBossDefinition> bosses();

    Optional<TowerBossDefinition> boss(ResourceLocation definitionId);

    TowerBossEncounter start(
            ServerLevel level,
            Vec3 bossPosition,
            Collection<ServerPlayer> participants,
            ResourceLocation definitionId,
            double healthMultiplier,
            Consumer<TowerBossResult> completion
    );

    boolean withdraw(TowerBossEncounter encounter, ServerPlayer player);

    boolean abort(TowerBossEncounter encounter);
}
