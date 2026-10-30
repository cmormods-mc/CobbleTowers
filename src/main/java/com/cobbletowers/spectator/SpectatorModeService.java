package com.cobbletowers.spectator;

import com.cobbletowers.persistence.SpectatorRestore;
import com.cobbletowers.persistence.TowerSpectatorStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;

/** Temporary native spectator mode, with durable restoration shared by participants and outside watchers. */
public final class SpectatorModeService {
    private SpectatorModeService() {}

    public static void enter(ServerPlayer player) {
        TowerSpectatorStore store = TowerSpectatorStore.get(player.getServer());
        if (store.hasPending(player.getUUID()) && store.entryFor(player.getUUID()).isEmpty()) {
            throw new IllegalStateException("Cannot replace unreadable original spectator mode for " + player.getUUID());
        }
        if (!store.hasPending(player.getUUID()) && !player.isSpectator()) {
            store.put(player.getUUID(), snapshot(player.gameMode.getGameModeForPlayer(), player.getAbilities()));
        }
        // Also retries a previous failed flush before changing a still-normal player on a later attempt.
        if (!player.isSpectator()) store.checkpoint(player.getServer());
        if (!player.setGameMode(GameType.SPECTATOR) && !player.isSpectator()) {
            throw new IllegalStateException("Temporary spectator mode was refused for " + player.getUUID());
        }
    }

    /** Returns false only when this mod has no original mode to restore. Clear the journal after success. */
    public static boolean restore(ServerPlayer player) {
        TowerSpectatorStore store = TowerSpectatorStore.get(player.getServer());
        var found = store.entryFor(player.getUUID());
        if (found.isEmpty()) {
            if (store.hasPending(player.getUUID())) throw new IllegalStateException("Original spectator mode needs repair for " + player.getUUID());
            return false;
        }
        SpectatorRestore original = found.get();
        GameType originalMode = GameType.byName(original.mode(), null);
        if (originalMode == null) throw new IllegalStateException("Unknown original spectator mode");
        if (!player.setGameMode(originalMode) && player.gameMode.getGameModeForPlayer() != originalMode) {
            throw new IllegalStateException("Restoring spectator mode was refused for " + player.getUUID());
        }
        applyAbilities(player.getAbilities(), original);
        player.onUpdateAbilities();
        store.remove(player.getUUID());
        store.checkpoint(player.getServer());
        return true;
    }

    static SpectatorRestore snapshot(GameType mode, Abilities abilities) {
        return new SpectatorRestore(mode.getName(), abilities.invulnerable, abilities.flying, abilities.mayfly,
                abilities.instabuild, abilities.mayBuild, abilities.getFlyingSpeed(), abilities.getWalkingSpeed());
    }

    static void applyAbilities(Abilities abilities, SpectatorRestore original) {
        abilities.invulnerable = original.invulnerable();
        abilities.flying = original.flying();
        abilities.mayfly = original.mayfly();
        abilities.instabuild = original.instabuild();
        abilities.mayBuild = original.mayBuild();
        abilities.setFlyingSpeed(original.flyingSpeed());
        abilities.setWalkingSpeed(original.walkingSpeed());
    }
}
