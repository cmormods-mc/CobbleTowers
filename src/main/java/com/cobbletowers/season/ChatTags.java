package com.cobbletowers.season;

import com.cobbletowers.TowerLog;
import com.cobbletowers.club.ClubBook;
import com.cobbletowers.mastery.MasteryBadge;
import com.cobbletowers.mastery.MasteryBadges;
import com.cobbletowers.mastery.MasteryPerks;
import com.cobbletowers.persistence.TowerClubStore;
import com.cobbletowers.persistence.TowerMasteryStore;
import net.minecraft.resources.ResourceLocation;
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
 * The title and club tag in front of a player's name (P36d), used by the display-name and tab-list mixins ({@link
 * #decorate}, {@link #tabName}) and by placeholders ({@link #segmentsOf}). Off with {@code "chat_tags": false} in the
 * cosmetics config. Reads the stores on demand, so there is no cache.
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
            List<Cosmetics.Segment> decoration = Cosmetics.decoration(title, club.map(ClubBook.Club::tag), club.map(ClubBook.Club::banner));
            List<Cosmetics.Segment> badge = badgeOf(server, player);
            if (badge.isEmpty()) return decoration;
            List<Cosmetics.Segment> all = new java.util.ArrayList<>(badge);
            all.addAll(decoration);
            return List.copyOf(all);
        } catch (RuntimeException ex) {
            TowerLog.errorOnce("chatTags", "Could not build a chat tag; names are left plain", ex);
            return List.of();
        }
    }

    /** The worn season title alone ({@code Champion S1}), for the title placeholder. */
    public static Optional<String> titleOf(MinecraftServer server, UUID player) {
        TowerSeasonProgressStore cosmetics = TowerSeasonProgressStore.get(server);
        return Cosmetics.validSelection(cosmetics.selectedTitle(player), cosmetics.cosmeticsOf(player))
                .flatMap(Cosmetics::parse).flatMap(Cosmetics::shortTitle);
    }

    /**
     * The mastery badge (P47): the icon of the tower with the highest level and that level, empty when the badge is off
     * (server or player) or the player has no level. Only the catalog towers count; a bounded loop over a handful.
     */
    public static List<Cosmetics.Segment> badgeOf(MinecraftServer server, UUID player) {
        if (!CosmeticsConfig.masteryBadge()) return List.of();
        if (TowerMasteryStore.get(server).badgeHidden(player)) return List.of();
        return MasteryBadges.shownFor(server, player)
                .map(badge -> MasteryBadge.segments(badge, MasteryPerks.rankOf(badge.tower(), badge.level())))
                .orElse(List.of());
    }

    private static Component compose(List<Cosmetics.Segment> segments, Component name) {
        MutableComponent out = Component.empty();
        for (Cosmetics.Segment segment : segments) {
            ChatFormatting color = ChatFormatting.getByName(segment.color());
            MutableComponent piece = Component.literal(segment.text());
            if (color != null) piece = piece.withStyle(color);
            Optional<ResourceLocation> font = segment.font().map(ResourceLocation::tryParse);
            if (font.isPresent()) {
                ResourceLocation fontId = font.get();
                piece = piece.withStyle(style -> style.withFont(fontId));
            }
            out.append(piece);
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

    /**
     * The tab-list name: {@code current} (a name some other mod set) or the team-formatted name, with the decoration
     * in front; null when nothing applies.
     */
    public static Component tabName(ServerPlayer player, Component current) {
        if (!CosmeticsConfig.chatTags()) return current;
        MinecraftServer server = player.getServer();
        if (server == null) return current;
        List<Cosmetics.Segment> segments = segmentsOf(server, player.getUUID());
        if (segments.isEmpty()) return current;
        Component base = current != null ? current : PlayerTeam.formatNameForTeam(player.getTeam(), player.getName());
        return compose(segments, base);
    }

    /**
     * Sends the tab list a fresh name for one player, after their title or club changed. A no-op for an offline
     * player.
     */
    public static void refresh(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online == null) return;
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(online)));
    }
}
