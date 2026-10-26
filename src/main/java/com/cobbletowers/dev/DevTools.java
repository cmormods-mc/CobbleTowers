package com.cobbletowers.dev;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/** Test tooling: only in the dev build (-Pdev). The release jar leaves this package out and {@code Dev} finds nothing. */
public final class DevTools {

    private DevTools() {}

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        WebPreviewCommand.register(dispatcher);
        PayloadDumpCommand.register(dispatcher);
    }

    /** Halts the JVM after a season finalisation step, to test crash recovery. */
    public static void afterSeasonStep(int step) {
        if (Integer.getInteger("cobbletowers.testOnlyCrashAfterSeasonStep", 0) == step) {
            Runtime.getRuntime().halt(137);
        }
    }
}
