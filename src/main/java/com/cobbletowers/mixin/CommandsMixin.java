package com.cobbletowers.mixin;

import com.cobbletowers.runtime.TowerCommandGuard;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every command a player types goes through {@code Commands.performCommand}; Fabric has no event before it, so this is
 * where a command is refused inside the tower (see {@link TowerCommandGuard}).
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void cobbletowers$refuseInTower(ParseResults<CommandSourceStack> parse, String command, CallbackInfo ci) {
        if (TowerCommandGuard.refuse(parse.getContext().getSource(), command)) ci.cancel();
    }
}
