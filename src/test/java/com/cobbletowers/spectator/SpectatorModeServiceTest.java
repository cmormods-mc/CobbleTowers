package com.cobbletowers.spectator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

class SpectatorModeServiceTest {
    @Test void restorationPreservesOriginalModesAndCustomFlightInsteadOfLeavingSpectatorDefaults() {
        for (GameType mode : GameType.values()) {
            Abilities abilities = new Abilities();
            mode.updatePlayerAbilities(abilities);
            abilities.mayfly = true;
            abilities.flying = true;
            abilities.setFlyingSpeed(.17f);
            abilities.setWalkingSpeed(.23f);
            var original = SpectatorModeService.snapshot(mode, abilities);
            GameType.SPECTATOR.updatePlayerAbilities(abilities);
            mode.updatePlayerAbilities(abilities);
            SpectatorModeService.applyAbilities(abilities, original);
            assertEquals(original, SpectatorModeService.snapshot(mode, abilities), mode.getName());
        }
    }
}
