package com.cobbletowers.command;

import com.cobbletowers.season.ChatTags;
import com.cobbletowers.season.Cosmetics;
import com.cobbletowers.season.CosmeticsService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Cosmetics (P36d): {@code /tower cosmetics} lists what a player earned and {@code /tower title} wears a title.
 * Operators get {@code /cobbletowers cosmeticsadmin} to grant one and read how names are decorated.
 */
public final class CosmeticsCommand {

    private CosmeticsCommand() {}

    /** The subcommands players use, to be added under {@code /cobbletowers play}. */
    public static List<LiteralArgumentBuilder<CommandSourceStack>> playerCommands() {
        return List.of(
                Commands.literal("badge")
                        .executes(context -> one(context, badge(context, null)))
                        .then(Commands.literal("on").executes(context -> one(context, badge(context, false))))
                        .then(Commands.literal("off").executes(context -> one(context, badge(context, true))))
                        .then(Commands.literal("auto").executes(context -> one(context, pinBadge(context, "auto"))))
                        .then(Commands.argument("tower", StringArgumentType.word())
                                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        com.cobbletowers.definition.TowerDefinitionRegistry.content().sortedTowerIds().stream()
                                                .map(id -> id.getPath()), builder))
                                .executes(context -> one(context, pinBadge(context, StringArgumentType.getString(context, "tower"))))),
                Commands.literal("cosmetics").executes(context -> lines(context, CosmeticsService.cosmeticsLines(
                        context.getSource().getServer(), context.getSource().getPlayerOrException().getUUID()))),
                Commands.literal("title")
                        .executes(context -> lines(context, CosmeticsService.titleLines(
                                context.getSource().getServer(), context.getSource().getPlayerOrException().getUUID())))
                        .then(Commands.literal("off").executes(context -> one(context, CosmeticsService.takeOff(
                                context.getSource().getServer(), context.getSource().getPlayerOrException()))))
                        .then(Commands.argument("number", IntegerArgumentType.integer(1, 999)).executes(context -> one(context,
                                CosmeticsService.wear(context.getSource().getServer(), context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "number"))))));
    }

    /** {@code /tower badge [on|off]}: shows or hides your own mastery badge (P47); with no argument, says which it is. */
    private static String badge(CommandContext<CommandSourceStack> context, Boolean hide) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var server = context.getSource().getServer();
        var store = com.cobbletowers.persistence.TowerMasteryStore.get(server);
        if (hide != null && store.setBadgeHidden(player.getUUID(), hide)) {
            store.checkpoint(server);
            ChatTags.refresh(server, player.getUUID());
        }
        String shown = store.badgeHidden(player.getUUID()) ? "hidden" : "shown";
        if (!com.cobbletowers.season.CosmeticsConfig.masteryBadge()) shown += " (this server has the mastery badge switched off)";
        String which = com.cobbletowers.mastery.MasteryBadges.shownFor(server, player.getUUID())
                .map(badge -> " It shows " + badge.tower().getPath() + " (level " + badge.level() + ", "
                        + (store.badgePickOf(player.getUUID()).isPresent() ? "pinned" : "automatic") + ").").orElse("");
        return "Your mastery badge is " + shown + "." + which + " The Hall lets you hide it, pin one tower, or follow your highest.";
    }

    /** {@code /tower badge <tower>} pins the badge to a tower you have a level in; {@code auto} follows your highest (P47). */
    private static String pinBadge(CommandContext<CommandSourceStack> context, String tower) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var server = context.getSource().getServer();
        var outcome = com.cobbletowers.mastery.MasteryBadges.pin(server, player.getUUID(), tower);
        if (outcome.changed()) ChatTags.refresh(server, player.getUUID());
        return outcome.message();
    }

    /** {@code /cobbletowers cosmeticsadmin ...}: operator only. */
    public static void registerAdmin(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers").then(Commands.literal("cosmeticsadmin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("grant")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.greedyString()).executes(CosmeticsCommand::grant))))
                .then(Commands.literal("parse")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("text", StringArgumentType.greedyString()).executes(CosmeticsCommand::parse))))
                .then(Commands.literal("names")
                        .then(Commands.argument("player", EntityArgument.player()).executes(CosmeticsCommand::names)))));
    }

    private static int grant(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String id = StringArgumentType.getString(context, "id");
        if (Cosmetics.parse(id).isEmpty()) return one(context, id + " is not a cosmetic name (such as s1:title_champion).");
        CosmeticsService.award(context.getSource().getServer(), player.getUUID(), Set.of(id));
        return one(context, "Granted " + id + " to " + player.getGameProfile().getName() + " (nothing happens if they had it).");
    }

    /**
     * Resolves placeholder text through Placeholder API for a player, so a test can see what a formatter would get.
     */
    private static int parse(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String text = StringArgumentType.getString(context, "text");
        return one(context, com.cobbletowers.season.ChatIntegration.parse(player, text)
                .map(result -> "parsed=[" + result + "]").orElse("Placeholder API is not available"));
    }

    /**
     * Prints the plain text of the player's display name and tab-list name, which is how a test sees what the mixins
     * produce.
     */
    private static int names(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        Component tab = player.getTabListDisplayName();
        return one(context, "display=[" + player.getDisplayName().getString() + "] tab=[" + (tab == null ? "" : tab.getString())
                + "] decoration=[" + Cosmetics.plain(ChatTags.segmentsOf(context.getSource().getServer(), player.getUUID())) + "]");
    }

    private static int lines(CommandContext<CommandSourceStack> context, List<String> lines) {
        for (String line : lines) one(context, line);
        return 1;
    }

    private static int one(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
