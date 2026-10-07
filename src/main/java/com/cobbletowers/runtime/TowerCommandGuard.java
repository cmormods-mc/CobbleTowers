package com.cobbletowers.runtime;

import com.cobbletowers.TowerLog;
import com.cobbletowers.instance.TowerDimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The runtime half of {@link CommandRules}, called by {@code CommandsMixin}. Only refuses a player in the tower
 * dimension; console, RCON, command blocks and creative or spectator operators are exempt. Operators add commands in
 * {@code config/cobbletowers/blocked_commands.txt} (one per line, {@code #} comments), read once at startup.
 */
public final class TowerCommandGuard {

    private static final String CONFIG = "cobbletowers/blocked_commands.txt";
    private static Set<String> denied = CommandRules.DEFAULTS;

    private TowerCommandGuard() {}

    /** Loads the operator's additions. A missing file is normal; an unreadable one is logged and ignored. */
    public static void install() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(CONFIG);
        Set<String> all = new HashSet<>(CommandRules.DEFAULTS);
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file)) {
                    String entry = line.strip();
                    if (entry.isEmpty() || entry.startsWith("#")) continue;
                    all.add(CommandRules.wordOf(entry));
                }
                TowerLog.info("Loaded {} blocked tower command(s) ({} added by {})", all.size(),
                        all.size() - CommandRules.DEFAULTS.size(), file.getFileName());
            } catch (IOException ex) {
                TowerLog.warn("Could not read {}: {}; using the built-in list", file, ex.toString());
            }
        }
        denied = Set.copyOf(all);
    }

    /** True when this command must not run: it has been refused and the player told. */
    public static boolean refuse(CommandSourceStack source, String commandLine) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        if (!player.level().dimension().equals(TowerDimension.LEVEL)) return false;
        if (player.hasPermissions(2) && (player.isCreative() || player.isSpectator())) return false;
        if (!CommandRules.isBlocked(commandLine, denied)) return false;
        player.sendSystemMessage(Component.literal("/" + CommandRules.wordOf(commandLine)
                + " is turned off inside the tower. /tower leave takes you home.").withStyle(ChatFormatting.RED));
        return true;
    }
}
