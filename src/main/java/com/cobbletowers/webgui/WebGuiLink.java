package com.cobbletowers.webgui;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The only class that talks to the WebGUI mod, and it does so by looking up WebGUI's three public API methods by name at
 * run time, so CobbleTowers neither compiles against WebGUI nor needs it present: no build dependency, no class that fails
 * to link on a server without it, and no coupling to the Loom version WebGUI happens to be built with.
 *
 * <p>The methods are those of {@code land.webgui.api.WebviewApi}: {@code openGui(player, url)}, {@code openHud(player, url)},
 * {@code emitToPage(player, event, json)} and {@code onPageEvent(channel, handler)}. {@link #install()} throws if any is
 * missing, which {@link WebGuiBridge#install()} turns into "stay native".
 */
final class WebGuiLink {

    /** The channel a page's {@code webgui.postToGame({channel: "cobbletowers", ...})} arrives on. */
    static final String CHANNEL = "cobbletowers";
    /** The event a page listens for with {@code webgui.on("tower", ...)}. */
    static final String EVENT = "tower";

    private static MethodHandle openGui;
    private static MethodHandle openHud;
    private static MethodHandle emitToPage;

    private WebGuiLink() {}

    static void install() {
        try {
            Class<?> api = Class.forName("land.webgui.api.WebviewApi");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType open = MethodType.methodType(void.class, ServerPlayer.class, String.class);
            openGui = lookup.findStatic(api, "openGui", open);
            openHud = lookup.findStatic(api, "openHud", open);
            emitToPage = lookup.findStatic(api, "emitToPage",
                    MethodType.methodType(void.class, ServerPlayer.class, String.class, String.class));
            MethodHandle onPageEvent = lookup.findStatic(api, "onPageEvent",
                    MethodType.methodType(void.class, String.class, BiConsumer.class));
            BiConsumer<ServerPlayer, String> handler = WebGuiBridge::onPage;
            onPageEvent.invoke(CHANNEL, handler);
        } catch (Throwable ex) {
            throw new IllegalStateException("WebGUI's WebviewApi is not what this build expects: " + ex, ex);
        }
    }

    static void openGui(ServerPlayer player, String url) {
        try {
            openGui.invokeExact(player, url);
        } catch (Throwable ex) {
            throw rethrow(ex);
        }
    }

    static void openHud(ServerPlayer player, String url) {
        try {
            openHud.invokeExact(player, url);
        } catch (Throwable ex) {
            throw rethrow(ex);
        }
    }

    static void emit(ServerPlayer player, String json) {
        try {
            emitToPage.invokeExact(player, EVENT, json);
        } catch (Throwable ex) {
            throw rethrow(ex);
        }
    }

    private static RuntimeException rethrow(Throwable ex) {
        if (ex instanceof RuntimeException runtime) return runtime;
        if (ex instanceof Error error) throw error;
        return new IllegalStateException(ex);
    }
}
