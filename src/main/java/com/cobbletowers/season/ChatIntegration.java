package com.cobbletowers.season;

import com.cobbletowers.TowerLog;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Installs the optional chat integrations (P36d). Kept apart from the class that talks to Placeholder API so that a server without it never
 * loads that class: the mod check happens here, and the link to it is only made when the mod is there.
 */
public final class ChatIntegration {

    private ChatIntegration() {}

    /** Resolves placeholder text for a player through Placeholder API, or empty when it is not installed or fails. */
    public static java.util.Optional<String> parse(net.minecraft.server.level.ServerPlayer player, String text) {
        if (!FabricLoader.getInstance().isModLoaded("placeholder-api")) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(PlaceholderBridge.parse(player, text));
        } catch (LinkageError | RuntimeException ex) {
            return java.util.Optional.empty();
        }
    }

    public static void install() {
        CosmeticsConfig.install();
        if (!FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            TowerLog.info("Placeholder API is not installed, so the %cobbletowers:...% placeholders are not registered");
            return;
        }
        try {
            PlaceholderBridge.register();
        } catch (LinkageError | RuntimeException ex) {
            TowerLog.error("Could not register the Placeholder API placeholders", ex);
        }
    }
}
