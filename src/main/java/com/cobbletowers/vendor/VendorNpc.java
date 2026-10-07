package com.cobbletowers.vendor;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.FloorLayout;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.instance.CellGrid;
import com.cobbletowers.instance.TowerDimension;
import com.cobbletowers.intermission.IntermissionService;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.runtime.TowerRuns;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * The vendor as a mob (P28): a cleric villager at the floor's exit anchor during an intermission that opens the
 * vendor screen on right-click. Vanilla, no AI, invulnerable and silent. Spawned on arrival, discarded when the run
 * leaves that state or the cell is released.
 */
public final class VendorNpc {

    private VendorNpc() {}

    public static void install() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!entity.getTags().contains(VendorNpcRules.VENDOR_TAG)) return InteractionResult.PASS;
            // Consume the click on both sides so the vanilla trading screen never opens.
            if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
            use(serverPlayer, entity);
            return InteractionResult.SUCCESS;
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!level.dimension().equals(TowerDimension.LEVEL)) return;
            if (!entity.getTags().contains(VendorNpcRules.VENDOR_TAG)) return;
            if (!isLive(entity)) entity.discard();
        });
    }

    private static Optional<UUID> runOf(Entity entity) {
        return entity.getTags().stream().map(VendorNpcRules::runOf).flatMap(Optional::stream).findFirst();
    }

    /** Whether the vendor's run still exists and is at an intermission. */
    private static boolean isLive(Entity entity) {
        return runOf(entity).flatMap(TowerRuns::get).filter(run -> run.state() == RunState.INTERMISSION).isPresent();
    }

    private static void use(ServerPlayer player, Entity vendor) {
        Optional<UUID> vendorRun = runOf(vendor);
        if (vendorRun.isEmpty()) return;
        Optional<PersistedRun> playerRun = TowerRuns.forPlayer(player.getUUID());
        VendorNpcRules.Access access = VendorNpcRules.access(vendorRun.get(), playerRun.map(PersistedRun::runId),
                playerRun.map(run -> run.state() == RunState.INTERMISSION).orElse(false));
        TowerLog.info("Vendor of run {}: {} used it ({})", vendorRun.get(), player.getGameProfile().getName(), access);
        if (access != VendorNpcRules.Access.ALLOWED) {
            player.displayClientMessage(Component.literal(VendorNpcRules.describe(access)), true);
            return;
        }
        String message = IntermissionService.vendor(player.server, player);
        if (!message.isEmpty()) player.displayClientMessage(Component.literal(message), true);
    }

    /** Puts a vendor at the run's exit anchor. Idempotent: a run never has two. Returns whether one stands there. */
    public static boolean spawn(MinecraftServer server, PersistedRun run) {
        if (run.cell().isEmpty()) return false;
        // A mode that closes the vendor (Hardcore, P32) has nobody standing there.
        if (com.cobbletowers.definition.PlaylistRegistry.vendorClosed(run)) return false;
        ServerLevel level = TowerDimension.level(server);
        Optional<FloorLayout> layout = TowerDefinitionRegistry.content().floorAt(run.towerId(), run.floorIndex())
                .flatMap(floor -> floor.layout());
        if (level == null || layout.isEmpty()) return false;
        if (!vendorsOf(server, run).isEmpty()) return true;

        BlockPos origin = CellGrid.originOf(run.cell().getAsInt());
        BlockPos spot = layout.get().exit().in(origin);
        BlockPos entry = layout.get().entry().in(origin);

        Villager vendor = new Villager(EntityType.VILLAGER, level);
        vendor.setVillagerData(vendor.getVillagerData().setProfession(VillagerProfession.CLERIC));
        vendor.setNoAi(true);
        vendor.setInvulnerable(true);
        vendor.setSilent(true);
        vendor.setPersistenceRequired();
        vendor.setCustomName(Component.literal("Tower Vendor"));
        vendor.setCustomNameVisible(true);
        vendor.addTag(VendorNpcRules.VENDOR_TAG);
        vendor.addTag(VendorNpcRules.runTag(run.runId()));
        vendor.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                VendorNpcRules.yawToward(spot.getX(), spot.getZ(), entry.getX(), entry.getZ()), 0f);
        if (!level.addFreshEntity(vendor)) {
            TowerLog.warn("Run {}: the vendor could not be spawned at {}", run.runId(), spot);
            return false;
        }
        return true;
    }

    /** Removes the run's vendor, if it has one. Safe to call at any time and more than once. */
    public static void despawn(MinecraftServer server, PersistedRun run) {
        for (Entity vendor : vendorsOf(server, run)) vendor.discard();
    }

    /** How many vendors the run has standing; a test seam, and the reason {@link #spawn} is idempotent. */
    public static int count(MinecraftServer server, PersistedRun run) {
        return vendorsOf(server, run).size();
    }

    /** Searched inside the run's own cell, so a lookup never walks the whole dimension. */
    private static List<Entity> vendorsOf(MinecraftServer server, PersistedRun run) {
        ServerLevel level = TowerDimension.level(server);
        if (level == null || run.cell().isEmpty()) return List.of();
        String tag = VendorNpcRules.runTag(run.runId());
        return level.getEntities((Entity) null, CellGrid.boundsOf(run.cell().getAsInt()),
                entity -> entity.getTags().contains(tag));
    }
}
