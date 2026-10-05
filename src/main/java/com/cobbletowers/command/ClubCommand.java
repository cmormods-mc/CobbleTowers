package com.cobbletowers.command;

import com.cobbletowers.club.ClubBook;
import com.cobbletowers.club.ClubService;
import com.cobbletowers.persistence.TowerClubStore;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Clubs (P35): {@code /tower club ...} for players, and operator tools under {@code /cobbletowers clubs}. */
public final class ClubCommand {

    private ClubCommand() {}

    /** The subcommands players use, to be added under {@code /cobbletowers play}. */
    public static List<LiteralArgumentBuilder<CommandSourceStack>> playerCommands() {
        return List.of(Commands.literal("club")
                .executes(context -> lines(context, ClubService.info(context.getSource().getServer(),
                        context.getSource().getPlayerOrException())))
                .then(Commands.literal("top").executes(context -> lines(context, ClubService.top(context.getSource().getServer())))
                        .then(Commands.literal("alltime").executes(context -> lines(context,
                                ClubService.top(context.getSource().getServer(), true)))))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> one(context, ClubService.create(context.getSource().getServer(),
                                        context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name"), null)))
                                .then(Commands.argument("tag", StringArgumentType.word())
                                        .executes(context -> one(context, ClubService.create(context.getSource().getServer(),
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "tag")))))))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> one(context, ClubService.invite(context.getSource().getServer(),
                                        context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("club", StringArgumentType.word())
                                .executes(context -> one(context, ClubService.accept(context.getSource().getServer(),
                                        context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "club"))))))
                .then(Commands.literal("leave").executes(context -> one(context, ClubService.leave(
                        context.getSource().getServer(), context.getSource().getPlayerOrException()))))
                .then(Commands.literal("disband").executes(context -> one(context, ClubService.disband(
                        context.getSource().getServer(), context.getSource().getPlayerOrException()))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> one(context, ClubService.kick(context.getSource().getServer(),
                                        context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("banner")
                        .then(Commands.argument("color", StringArgumentType.word())
                                .executes(context -> one(context, ClubService.banner(context.getSource().getServer(),
                                        context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "color"))))))
                .then(Commands.literal("claim").executes(context -> one(context, ClubService.claim(
                        context.getSource().getServer(), context.getSource().getPlayerOrException())))));
    }

    /** {@code /cobbletowers clubs ...}: operator only. */
    public static void registerAdmin(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers").then(Commands.literal("clubs")
                .requires(source -> source.hasPermission(2))
                .executes(context -> lines(context, ClubService.top(context.getSource().getServer())))
                .then(Commands.literal("clear").executes(context -> {
                    TowerClubStore store = TowerClubStore.get(context.getSource().getServer());
                    store.book().clear();
                    store.changed();
                    return one(context, "Every club and best cleared.");
                }))
                .then(Commands.literal("addclears")
                        .then(Commands.argument("club", StringArgumentType.word())
                                .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1000))
                                        .executes(ClubCommand::addClears))))));
    }

    private static int addClears(CommandContext<CommandSourceStack> context) {
        TowerClubStore store = TowerClubStore.get(context.getSource().getServer());
        var club = store.book().find(StringArgumentType.getString(context, "club"));
        if (club.isEmpty()) return one(context, "No such club.");
        boolean met = store.book().addWeekClears(club.get(),
                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count"), ClubService.weekKey());
        store.changed();
        return one(context, "Added; the club is at " + store.book().weekClears(club.get(), ClubService.weekKey()) + "/"
                + ClubBook.WEEKLY_GOAL + (met ? " (goal met)" : "") + ".");
    }

    private static int lines(CommandContext<CommandSourceStack> context, List<String> lines) throws CommandSyntaxException {
        for (String line : lines) one(context, line);
        return 1;
    }

    private static int one(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
