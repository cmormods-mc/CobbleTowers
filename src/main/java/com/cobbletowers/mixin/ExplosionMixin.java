package com.cobbletowers.mixin;

import com.cobbletowers.instance.TowerDimensionContract;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Disables all explosion effects inside the dedicated Tower dimension. */
@Mixin(Explosion.class)
abstract class ExplosionMixin {
    @Shadow @Final private Level level;

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void cobbletowers$disableTowerExplosionDamage(CallbackInfo ci) {
        if (cobbletowers$isTowerDimension()) ci.cancel();
    }

    @Inject(method = "finalizeExplosion", at = @At("HEAD"), cancellable = true)
    private void cobbletowers$disableTowerExplosionFinalization(boolean spawnParticles, CallbackInfo ci) {
        if (cobbletowers$isTowerDimension()) ci.cancel();
    }

    private boolean cobbletowers$isTowerDimension() {
        return TowerDimensionContract.DIMENSION_ID.equals(level.dimension().location().toString());
    }
}
