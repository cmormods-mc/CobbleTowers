package com.cobbletowers;

import com.mojang.brigadier.CommandDispatcher;
import java.lang.reflect.Method;
import net.minecraft.commands.CommandSourceStack;

/** Reaches the test tooling in {@code com.cobbletowers.dev}, which only the dev build contains. All no-ops in a release. */
public final class Dev {

    private static final Class<?> TOOLS = find();

    private Dev() {}

    /** True in the dev build only. */
    public static boolean present() {
        return TOOLS != null;
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        call("registerCommands", new Class<?>[] {CommandDispatcher.class}, dispatcher);
    }

    public static void afterSeasonStep(int step) {
        call("afterSeasonStep", new Class<?>[] {int.class}, step);
    }

    private static Class<?> find() {
        try {
            return Class.forName("com.cobbletowers.dev.DevTools");
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private static void call(String name, Class<?>[] types, Object arg) {
        if (TOOLS == null) return;
        try {
            Method method = TOOLS.getMethod(name, types);
            method.invoke(null, arg);
        } catch (ReflectiveOperationException ex) {
            TowerLog.error("Dev tool " + name + " failed", ex);
        }
    }
}
