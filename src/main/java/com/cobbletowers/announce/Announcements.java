package com.cobbletowers.announce;

import com.cobbletowers.TowerLog;
import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Server-wide announcements (P36e): a new number-one on a board, and a player reaching a milestone Ascension.
 *
 * <p>The sentences and the "is this worth announcing" rules are pure and tested; {@link #broadcast} is the only part that touches a server. Switched
 * on or off in {@code config/cobbletowers-announce.json} ({@code {"enabled": true}}); a bad file leaves them on and says so in the log.
 */
public final class Announcements {

    private static volatile boolean enabled = true;

    private Announcements() {}

    public static void install() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> load());
    }

    public static boolean enabled() {
        return enabled;
    }

    /** Test seam and operator reload. */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    private static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("cobbletowers-announce.json");
        if (!Files.exists(file)) {
            enabled = true;
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
            TowerLog.info("Announcements are {}", enabled ? "on" : "off");
        } catch (IOException | RuntimeException ex) {
            TowerLog.error("{} is not usable ({}); announcements stay on", file, ex.toString());
            enabled = true;
        }
    }

    // ---- the rules and the words ----------------------------------------------------------------------------------

    /** The Ascension levels worth telling the server about: the first, then every fifth. */
    public static boolean isMilestone(int level) {
        return level == 1 || (level > 0 && level % 5 == 0);
    }

    /** Only a board that ranks runs against each other announces a record; Clears and trials do not. */
    public static boolean announcesRecords(Board board) {
        return board == Board.ASCENSION || board == Board.SPEED || board == Board.DIFFICULTY;
    }

    /**
     * The sentence for a new number one, or empty when there is nothing to say (not first place, not a record board, no names).
     *
     * @param rank the all-time rank the entry took, 1 meaning it now leads the board
     * @param value the board's own number: milliseconds for Speed, otherwise the score or Ascension level
     */
    public static Optional<String> record(Board board, int rank, String tower, List<String> names, long value) {
        if (rank != 1 || !announcesRecords(board) || names.isEmpty()) return Optional.empty();
        String who = joined(names);
        String what = switch (board) {
            case SPEED -> "the fastest cycle of " + tower + " (" + duration(value) + ")";
            case DIFFICULTY -> "the highest-difficulty clear of " + tower + " (score " + value + ")";
            default -> "the deepest Ascension in " + tower + " (Ascension " + value + ")";
        };
        return Optional.of(who + " set a new record: " + what + "!");
    }

    /** The sentence for a player reaching a milestone Ascension for the first time, or empty if it is not one. */
    public static Optional<String> ascension(String tower, String player, int level) {
        if (!isMilestone(level)) return Optional.empty();
        return Optional.of(player + " reached Ascension " + level + " in " + tower + "!");
    }

    static String joined(List<String> names) {
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + " and " + names.get(1);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    static String duration(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    // ---- the door ---------------------------------------------------------------------------------------------------

    /** Tells every player on the server, if announcements are on. */
    public static void broadcast(MinecraftServer server, String sentence) {
        if (!enabled) return;
        server.getPlayerList().broadcastSystemMessage(Component.literal(sentence).withStyle(ChatFormatting.GOLD), false);
        TowerLog.info("Announced: {}", sentence);
    }
}
