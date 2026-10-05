package com.cobbletowers.season;

import com.cobbletowers.TowerLog;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Placeholder API side of the chat tag (P36d): {@code %cobbletowers:title%}, {@code %cobbletowers:club_tag%}, {@code %cobbletowers:club_name%}
 * and {@code %cobbletowers:prefix%}, so a server's own chat or tab formatter can place them. Each is empty for a player who has none.
 *
 * <p>The only class that touches Placeholder API, and only ever loaded by {@link ChatIntegration} when that mod is present, so a server without it
 * never links against it.
 */
final class PlaceholderBridge {

    private PlaceholderBridge() {}

    static void register() {
        add("title", player -> {
            var server = player.getServer();
            return server == null ? "" : ChatTags.segmentsOf(server, player.getUUID()).stream()
                    .filter(segment -> segment.color().equals("gold")).map(segment -> segment.text().trim()).findFirst().orElse("");
        });
        add("club_tag", player -> clubOf(player, true));
        add("club_name", player -> clubOf(player, false));
        // The whole decoration, coloured as in chat, for a formatter that wants one thing to place.
        Placeholders.register(ResourceLocation.fromNamespaceAndPath("cobbletowers", "prefix"), (context, argument) -> {
            if (!context.hasPlayer()) return PlaceholderResult.invalid("no player");
            ServerPlayer player = context.player();
            var server = player.getServer();
            List<Cosmetics.Segment> segments = server == null ? List.of() : ChatTags.segmentsOf(server, player.getUUID());
            return PlaceholderResult.value(ChatTags.prefixComponent(segments));
        });
        TowerLog.info("Registered the %cobbletowers:...% placeholders with Placeholder API");
    }

    /** Resolves {@code text} (such as {@code %cobbletowers:title%}) the way any formatter would, through Placeholder API's parser. */
    static String parse(ServerPlayer player, String text) {
        return Placeholders.parseText(Component.literal(text), eu.pb4.placeholders.api.PlaceholderContext.of(player)).getString();
    }

    private static void add(String name, Function<ServerPlayer, String> value) {
        Placeholders.register(ResourceLocation.fromNamespaceAndPath("cobbletowers", name), (context, argument) -> {
            if (!context.hasPlayer()) return PlaceholderResult.invalid("no player");
            return PlaceholderResult.value(Component.literal(value.apply(context.player())));
        });
    }

    private static String clubOf(ServerPlayer player, boolean tag) {
        var server = player.getServer();
        if (server == null) return "";
        return com.cobbletowers.persistence.TowerClubStore.get(server).book().clubOf(player.getUUID())
                .map(club -> tag ? club.tag() : club.name()).orElse("");
    }
}
