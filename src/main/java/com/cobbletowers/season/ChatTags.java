package com.cobbletowers.season;

import com.cobbletowers.club.ClubBook;
import com.cobbletowers.persistence.TowerClubStore;
import com.cobbletowers.persistence.TowerSeasonProgressStore;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

/**
 * The title and club tag in front of a player's name (P36d). The mixins on a player's display name and tab-list name call {@link #decorate} and
 * {@link #tabName}; the placeholders and {@code /tower} lines call {@link #segmentsOf}. Nothing here changes a name for a player with neither a worn
 * title nor a club, and the whole thing is off with {@code "chat_tags": false} in the cosmetics config.
 *
 * <p>Everything reads from the stores on demand (a handful of map lookups and a short scan of the clubs), so there is no cache to go stale.
 */
public final class ChatTags {

    private ChatTags() {}

    /** The pieces in front of this player's name, empty for a plain name. Never throws. */
    public static List<Cosmetics.Segment> segmentsOf(MinecraftServer server, UUID player) {
        try {
            TowerSeasonProgressStore cosmetics = TowerSeasonProgressStore.get(server);
            Optional<String> title = Cosmetics.validSelection(cosmetics.selectedTitle(player), cosmetics.cosmeticsOf(player))
                    .flatMap(Cosmetics::parse).flatMap(Cosmetics::shortTitle);
            Optional<ClubBook.Club> club = TowerClubStore.get(server).book().clubOf(player);
            return Cosmetics.decoration(title, club.map(ClubBook.Club::tag), club.map(ClubBook.Club::banner));
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static Component compose(List<Cosmetics.Segment> segments, Component name) {
        MutableComponent out = Component.empty();
        for (Cosmetics.Segment segment : segments) {
            ChatFormatting color = ChatFormatting.getByName(segment.color());
            MutableComponent piece = Component.literal(segment.text());
            out.append(color == null ? piece : piece.withStyle(color));
        }
        return out.append(name);
    }

    /** The decoration alone, coloured as in chat (for a placeholder). Empty for none. */
    public static Component prefixComponent(List<Cosmetics.Segment> segments) {
        return compose(segments, Component.empty());
    }

    /** The display name with the decoration in front, or {@code base} untouched. */
    public static Component decorate(ServerPlayer player, Component base) {
        if (!CosmeticsConfig.chatTags()) return base;
        MinecraftServer server = player.getServer();
        if (server == null) return base;
        List<Cosmetics.Segment> segments = segmentsOf(server, player.getUUID());
        return segments.isEmpty() ? base : compose(segments, base);
    }

    /** The tab-list name: {@code current} (a name some other mod set) or the team-formatted name, with the decoration in front; null when nothing applies. */
    public static Component tabName(ServerPlayer player, Component current) {
        if (!CosmeticsConfig.chatTags()) return current;
        MinecraftServer server = player.getServer();
        if (server == null) return current;
        List<Cosmetics.Segment> segments = segmentsOf(server, player.getUUID());
        if (segments.isEmpty()) return current;
        Component base = current != null ? current : PlayerTeam.formatNameForTeam(player.getTeam(), player.getName());
        return compose(segments, base);
    }

    /** Sends the tab list a fresh name for one player, after their title or club changed. A no-op for an offline player. */
    public static void refresh(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online == null) return;
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(online)));
    }
}
