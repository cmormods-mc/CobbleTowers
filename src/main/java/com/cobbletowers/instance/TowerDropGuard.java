package com.cobbletowers.instance;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Nothing drops on the ground in the tower dimension (P27).
 *
 * <p>A defeated opponent drops its loot table where it stood, and a tower's rewards are the run's, banked and
 * delivered by the reward service -- so a pile of loose items and experience orbs is both a second, unaccounted
 * reward and mess for the cell reset to sweep. Rather than chase every source (Cobblemon's own drops, CobbleRaids
 * bosses, anything another mod adds), this discards the result: an item with no thrower, or an experience orb,
 * that appears in the tower dimension is removed as it loads.
 *
 * <p>Two things are left alone. An item a player threw has a thrower, so tossing something is unaffected. And a
 * dying player's inventory drops with no thrower, so an item is kept when a dead or dying player is next to it:
 * losing someone's whole inventory to a cleanup rule would be far worse than a loot pile.
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
