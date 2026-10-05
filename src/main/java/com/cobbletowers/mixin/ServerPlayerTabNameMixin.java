package com.cobbletowers.mixin;

import com.cobbletowers.season.ChatTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The tab-list counterpart of {@link PlayerDisplayNameMixin} (P36d): the same decoration in front of the name in the player list. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTabNameMixin {

    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void cobbletowers$decorate(CallbackInfoReturnable<Component> cir) {
        try {
            cir.setReturnValue(ChatTags.tabName((ServerPlayer) (Object) this, cir.getReturnValue()));
        } catch (RuntimeException ignored) {
            // Keep whatever the tab list would have shown.
        }
    }
}
