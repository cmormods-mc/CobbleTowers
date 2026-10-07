package com.cobbletowers.mixin;

import com.cobbletowers.season.ChatTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts a player's worn title and club tag before their display name (P36d), which chat, death messages and {@code
 * /msg} build from. Only a {@link ServerPlayer} is touched; a failure leaves the name as it was.
 */
@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void cobbletowers$decorate(CallbackInfoReturnable<Component> cir) {
        if ((Object) this instanceof ServerPlayer player) {
            try {
                cir.setReturnValue(ChatTags.decorate(player, cir.getReturnValue()));
            } catch (RuntimeException ignored) {
                // A cosmetic must never break a chat line: keep the plain name.
            }
        }
    }
}
