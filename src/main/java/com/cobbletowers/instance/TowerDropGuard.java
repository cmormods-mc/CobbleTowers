package com.cobbletowers.instance;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Nothing drops on the ground in the tower dimension (P27); an item with no thrower, or an experience orb, is removed
 * as it loads. Items a player threw, and items beside a dead or dying player, are left alone.
 */
public final class TowerDropGuard {

    /** How far from a dying player an unowned item still counts as theirs. */
    private static final double DEATH_DROP_REACH = 4.0;

    private TowerDropGuard() {}

    public static void install() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!level.dimension().equals(TowerDimension.LEVEL)) return;
            if (entity instanceof ExperienceOrb) {
                entity.discard();
            } else if (entity instanceof ItemEntity item && item.getOwner() == null && !fromDyingPlayer(item)) {
                item.discard();
            }
        });
    }

    private static boolean fromDyingPlayer(ItemEntity item) {
        return !item.level().getEntitiesOfClass(ServerPlayer.class, item.getBoundingBox().inflate(DEATH_DROP_REACH),
                Player::isDeadOrDying).isEmpty();
    }
}
