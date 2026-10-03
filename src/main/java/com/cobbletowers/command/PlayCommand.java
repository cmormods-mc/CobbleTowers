package com.cobbletowers.command;

import com.cobbletowers.lobby.LobbyService;
import com.cobbletowers.lobby.TowerLobby;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /cobbletowers play}: the way a player gets into a run, with no operator level needed.
 *
 * <p>Bare {@code play} opens the screen; every subcommand is the same action a screen button sends,
 * through the same {@link LobbyService} methods, so a client without the screen can still play and a
 * test can drive it over RCON with {@code execute as}.
 */
public final class PlayCommand {

    private PlayCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers")
                .then(Commands.literal("play")
                        .executes(PlayCommand::open)
                        .then(Commands.literal("tower")
                                .then(Commands.argument("tower", ResourceLocationArgument.id())
                                        .executes(PlayCommand::tower)))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(PlayCommand::invite)))
                        .then(Commands.literal("accept")
                                .then(Commands.argument("host", EntityArgument.player())
                                        .executes(PlayCommand::accept)))
                        .then(Commands.literal("decline")
                                .then(Commands.argument("host", EntityArgument.player())
                                        .executes(PlayCommand::decline)))
                        .then(Commands.literal("leave").executes(PlayCommand::leave))
                        .then(Commands.literal("start").executes(PlayCommand::start))
                        .then(Commands.literal("status").executes(PlayCommand::status))
                        .then(Commands.literal("lobbies")
                                .requires(source -> source.hasPermission(2))
                                .executes(PlayCommand::lobbies))));
    }

    private static int open(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LobbyService.openScreen(context.getSource().getServer(), player);
        return 1;
    }

    private static int tower(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return say(context, LobbyService.select(context.getSource().getServer(),
                context.getSource().getPlayerOrException(), ResourceLocationArgument.getId(context, "tower")));
    }

    private static int invite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return say(context, LobbyService.invite(context.getSource().getServer(),
                context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")));
    }

    private static int accept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return say(context, LobbyService.accept(context.getSource().getServer(),
                context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "host").getUUID()));
    }

    private static int decline(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return say(context, LobbyService.decline(context.getSource().getServer(),
                context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "host").getUUID()));
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return say(context, LobbyService.leave(context.getSource().getServer(),
                context.getSource().getPlayerOrException()));
    }

    private static int start(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return say(context, LobbyService.start(context.getSource().getServer(),
                context.getSource().getPlayerOrException()));
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<TowerLobby> lobby = LobbyService.lobbyOf(player.getUUID());
        String text = lobby.map(found -> "Team for " + found.tower() + ": " + found.team().size() + " ready, "
                + found.pending().size() + " pending" + (found.counting() ? ", starting" : ""))
                .orElse("You are not in a team.");
        return say(context, text);
    }

    /** Operator view of every forming team, for a live test that cannot see a player's chat. */
    private static int lobbies(CommandContext<CommandSourceStack> context) {
        List<String> lines = LobbyService.describeAll();
        context.getSource().sendSuccess(() -> Component.literal(lines.size() + " lobby(ies)"), false);
        for (String line : lines) context.getSource().sendSuccess(() -> Component.literal("  " + line), false);
        return lines.size();
    }

    private static int say(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
