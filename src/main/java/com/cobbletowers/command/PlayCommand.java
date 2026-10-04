package com.cobbletowers.command;

import com.cobbletowers.intermission.IntermissionService;
import com.cobbletowers.lobby.LobbyService;
import com.cobbletowers.lobby.TowerLobby;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
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
        var playRoot = Commands.literal("play");
        // Mastery and leaderboards (P31) hang off the same node, so /tower mastery and /tower leaderboard work.
        MasteryCommand.playerCommands().forEach(playRoot::then);
        TrialCommand.playerCommands().forEach(playRoot::then);
        var play = dispatcher.register(Commands.literal("cobbletowers")
                .then(playRoot
                        .executes(PlayCommand::open)
                        .then(Commands.literal("menu").executes(PlayCommand::open))
                        .then(Commands.literal("vote")
                                .then(Commands.argument("card", IntegerArgumentType.integer(1, 9))
                                        .executes(PlayCommand::pick)))
                        .then(Commands.literal("tower")
                                .then(Commands.argument("tower", ResourceLocationArgument.id())
                                        .executes(PlayCommand::tower)))
                        .then(Commands.literal("playlist")
                                .then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(PlayCommand::playlist)))
                        .then(Commands.literal("draft")
                                .executes(PlayCommand::draft)
                                .then(Commands.literal("restart").executes(PlayCommand::draftRestart))
                                .then(Commands.literal("text").executes(context -> sayAll(context,
                                        com.cobbletowers.lobby.RentalDraftService.text(context.getSource().getServer(),
                                                context.getSource().getPlayerOrException()))))
                                .then(Commands.literal("pick")
                                        .then(Commands.argument("a", IntegerArgumentType.integer(1, 5))
                                                .then(Commands.argument("b", IntegerArgumentType.integer(1, 5))
                                                        .executes(PlayCommand::draftPick)))))
                        .then(Commands.literal("ascension")
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 1000))
                                        .executes(PlayCommand::ascension)))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(PlayCommand::invite)))
                        .then(Commands.literal("accept")
                                .then(Commands.argument("host", EntityArgument.player())
                                        .executes(PlayCommand::accept)))
                        .then(Commands.literal("decline")
                                .then(Commands.argument("host", EntityArgument.player())
                                        .executes(PlayCommand::decline)))
                        .then(Commands.literal("ready").executes(context -> ready(context, true)))
                        .then(Commands.literal("unready").executes(context -> ready(context, false)))
                        .then(Commands.literal("cashout").executes(context -> cashOut(context, true)))
                        .then(Commands.literal("stay").executes(context -> cashOut(context, false)))
                        .then(Commands.literal("pick")
                                .then(Commands.argument("card", IntegerArgumentType.integer(1, 9))
                                        .executes(PlayCommand::pick)))
                        .then(Commands.literal("register")
                                .executes(PlayCommand::openChooser)
                                .then(Commands.literal("toggle")
                                        .then(Commands.argument("pokemon", UuidArgument.uuid())
                                                .executes(PlayCommand::toggle)))
                                .then(Commands.literal("clear").executes(PlayCommand::clearChoice)))
                        .then(Commands.literal("pokemon")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(PlayCommand::pokemon)))
                        .then(Commands.literal("rentals")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> sayAll(context, com.cobbletowers.lobby.RentalAdmin.describe(
                                                EntityArgument.getPlayer(context, "player"))))
                                        .then(Commands.literal("stray").executes(context -> sayAll(context,
                                                com.cobbletowers.lobby.RentalAdmin.giveStray(EntityArgument.getPlayer(context, "player")))))
                                        .then(Commands.literal("dex").executes(context -> sayAll(context,
                                                com.cobbletowers.lobby.RentalAdmin.pokedex(EntityArgument.getPlayer(context, "player")))))
                                        .then(Commands.literal("xp").executes(context -> sayAll(context,
                                                com.cobbletowers.lobby.RentalAdmin.tryExperience(EntityArgument.getPlayer(context, "player")))))))
                        .then(Commands.literal("leave").executes(PlayCommand::leave))
                        .then(Commands.literal("start").executes(PlayCommand::start))
                        .then(Commands.literal("status").executes(PlayCommand::status))
                        .then(Commands.literal("lobbies")
                                .requires(source -> source.hasPermission(2))
                                .executes(PlayCommand::lobbies))));
        // The short form players actually type: /tower, /tower vote 2, /tower ready ...
        dispatcher.register(Commands.literal("tower")
                .executes(PlayCommand::open)
                .redirect(play.getChild("play")));
    }

    private static int open(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        // A team at an intermission wants that menu, not the tower picker.
        if (IntermissionService.isAtIntermission(player)) {
            IntermissionService.openScreen(context.getSource().getServer(), player, "");
        } else {
            LobbyService.openScreen(context.getSource().getServer(), player);
        }
        return 1;
    }

    private static int tower(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return say(context, LobbyService.select(context.getSource().getServer(),
                context.getSource().getPlayerOrException(), ResourceLocationArgument.getId(context, "tower")));
    }

    private static int playlist(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return say(context, LobbyService.setPlaylist(context.getSource().getServer(),
                context.getSource().getPlayerOrException(),
                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "mode")));
    }

    private static int draft(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return sayAll(context, com.cobbletowers.lobby.RentalDraftService.view(context.getSource().getServer(),
                context.getSource().getPlayerOrException()));
    }

    private static int draftRestart(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return sayAll(context, com.cobbletowers.lobby.RentalDraftService.restart(context.getSource().getServer(),
                context.getSource().getPlayerOrException()));
    }

    private static int draftPick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return sayAll(context, com.cobbletowers.lobby.RentalDraftService.pick(context.getSource().getServer(),
                context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "a"),
                IntegerArgumentType.getInteger(context, "b")));
    }

    private static int sayAll(CommandContext<CommandSourceStack> context, java.util.List<String> lines) {
        return say(context, String.join(System.lineSeparator(), lines));
    }

    private static int ascension(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return say(context, LobbyService.setAscension(context.getSource().getServer(),
                context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "level")));
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

    private static int ready(CommandContext<CommandSourceStack> context, boolean value) throws CommandSyntaxException {
        return say(context, IntermissionService.ready(context.getSource().getServer(),
                context.getSource().getPlayerOrException(), value));
    }

    private static int cashOut(CommandContext<CommandSourceStack> context, boolean value) throws CommandSyntaxException {
        return say(context, IntermissionService.cashOut(context.getSource().getServer(),
                context.getSource().getPlayerOrException(), value));
    }

    /** One-based, as a player counts the cards on screen. */
    private static int pick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return say(context, IntermissionService.pick(context.getSource().getServer(),
                context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "card") - 1));
    }

    private static int openChooser(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        LobbyService.sendRegistration(context.getSource().getServer(), context.getSource().getPlayerOrException(), "", true);
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return say(context, LobbyService.choose(context.getSource().getServer(),
                context.getSource().getPlayerOrException(), UuidArgument.getUuid(context, "pokemon")));
    }

    private static int clearChoice(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return say(context, LobbyService.clearChoice(context.getSource().getServer(),
                context.getSource().getPlayerOrException()));
    }

    /** Operator view of everything a player owns, for a live test that cannot see a screen. */
    private static int pokemon(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<String> lines = LobbyService.describePokemon(EntityArgument.getPlayer(context, "player"));
        context.getSource().sendSuccess(() -> Component.literal(lines.size() + " pokemon"), false);
        for (String line : lines) context.getSource().sendSuccess(() -> Component.literal("  " + line), false);
        return lines.size();
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
        String run = IntermissionService.statusOf(player);
        if (run != null) return say(context, run);
        Optional<TowerLobby> lobby = LobbyService.lobbyOf(player.getUUID());
        String text = lobby.map(found -> "Team for " + found.tower() + ": " + found.team().size() + " ready, "
                + found.pending().size() + " pending" + (found.counting() ? ", starting" : ""))
                .orElseGet(() -> LobbyService.noTeam(player));
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
