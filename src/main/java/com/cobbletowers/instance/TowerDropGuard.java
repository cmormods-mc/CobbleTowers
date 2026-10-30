package com.cobbletowers.instance;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Nothing drops on the ground in the tower dimension (P27); an item with no thrower, or an experience orb, is removed
 * after tracking has finished. Items a player threw, and items beside a dead or dying player, are left alone.
 */
public final class TowerDropGuard {

    /** How far from a dying player an unowned item still counts as theirs. */
    private static final double DEATH_DROP_REACH = 4.0;
    private static final int REMOVALS_PER_TICK = 256;
    private static final DeferredRemovalQueue<Entity> PENDING = new DeferredRemovalQueue<>();

    static {
        com.cobbletowers.ServerState.onStop(PENDING::clear);
    }

    private TowerDropGuard() {}

    public static void install() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!level.dimension().equals(TowerDimension.LEVEL)) return;
            // ENTITY_LOAD also fires inside a live section iteration on chunk visibility changes.
            // Even on the server thread, discarding here invalidates that iterator.
            if (entity instanceof ExperienceOrb
                    || entity instanceof ItemEntity item && item.getOwner() == null && !fromDyingPlayer(item)) {
                PENDING.add(entity);
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (!level.dimension().equals(TowerDimension.LEVEL)) return;
            PENDING.drain(REMOVALS_PER_TICK,
                    entity -> !entity.isRemoved() && entity.level() == level && eligible(entity),
                    entity -> {
                        try {
                            entity.discard();
                        } catch (RuntimeException ex) {
                            com.cobbletowers.TowerLog.errorOnce("drop-guard", "Could not remove tower debris", ex);
                        }
                    });
        });
    }

    private static boolean eligible(Entity entity) {
        return entity instanceof ExperienceOrb
                || entity instanceof ItemEntity item && item.getOwner() == null && !fromDyingPlayer(item);
    }

    private static boolean fromDyingPlayer(ItemEntity item) {
        return !item.level().getEntitiesOfClass(ServerPlayer.class, item.getBoundingBox().inflate(DEATH_DROP_REACH),
                Player::isDeadOrDying).isEmpty();
    }
}
