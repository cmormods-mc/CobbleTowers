package com.cobbletowers.spike;

import com.cobbleraids.api.encounter.CobbleRaidsEncounters;
import com.cobbleraids.api.encounter.EncounterPolicy;
import com.cobbleraids.api.encounter.EncounterRequest;
import com.cobbleraids.api.encounter.StartResult;
import com.cobbletowers.TowerLog;
import com.cobbletowers.encounter.TowerLevelSnapshot;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /cobbletowers spike start <definition> <players>} and {@code /cobbletowers spike abort}.
 *
 * <p>Dev-only (permission level 2). Starts a CobbleRaids-backed boss fight owned by CobbleTowers,
 * with the rules a tower floor will have: no catch, no raid rewards or history, no progression, and
 * battle damage and PP carried back to the party (TDS #16: no free healing).
 */
public final class SpikeCommand {

    private static final ResourceLocation OWNER = ResourceLocation.fromNamespaceAndPath("cobbletowers", "spike");
    private static final double DISTANCE_AHEAD = 6.0;
    static final EncounterPolicy POLICY = EncounterPolicy.none().withCarryover(true, true);

    private SpikeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spike")
                        .then(Commands.literal("start")
                                .then(Commands.argument("definition", ResourceLocationArgument.id())
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .executes(SpikeCommand::start))))
                        .then(Commands.literal("abort")
                                .executes(SpikeCommand::abort))));
    }

    private static int start(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ResourceLocation definition = ResourceLocationArgument.getId(context, "definition");
            List<ServerPlayer> players = List.copyOf(EntityArgument.getPlayers(context, "players"));
            if (players.size() > EncounterRequest.MAX_PLAYERS) {
                source.sendFailure(Component.literal("At most " + EncounterRequest.MAX_PLAYERS + " players, got " + players.size()));
                return 0;
            }

            List<Integer> levels = new ArrayList<>();
            for (ServerPlayer player : players) {
                for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                    if (pokemon != null) levels.add(pokemon.getLevel());
                }
            }
            OptionalInt bossLevel = TowerLevelSnapshot.of(levels);
            if (bossLevel.isEmpty()) {
                source.sendFailure(Component.literal("None of those players has a Pokemon in their party."));
                return 0;
            }

            ServerPlayer lead = players.get(0);
            UUID id = UUID.randomUUID();
            EncounterRequest request = new EncounterRequest(OWNER, id, players, definition, lead.serverLevel(),
                    aheadOf(lead), bossLevel.getAsInt(), OptionalLong.empty(), POLICY);

            StartResult result = CobbleRaidsEncounters.start(request, SpikeEncounters.listenerFor(source.getServer()));
            if (result instanceof StartResult.Refused refused) {
                source.sendFailure(Component.literal("Spike refused: " + refused.reason()));
                TowerLog.warn("Spike refused for {}: {}", definition, refused.reason());
                return 0;
            }
            SpikeEncounters.track(new SpikeEncounters.Spike(id, definition, bossLevel.getAsInt(),
                    players.stream().map(ServerPlayer::getUUID).toList(), System.currentTimeMillis()));
            TowerLog.info("Spike {} started: {} at level {} for {} player(s) with {} Pokemon registered",
                    id, definition, bossLevel.getAsInt(), players.size(), levels.size());
            source.sendSuccess(() -> Component.literal("Spike " + id + " started: " + definition + " at level "
                    + bossLevel.getAsInt() + " for " + players.size() + " player(s), " + levels.size() + " Pokemon."), true);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            source.sendFailure(Component.literal(ex.getMessage()));
            return 0;
        } catch (RuntimeException ex) {
            TowerLog.error("Spike start failed", ex);
            source.sendFailure(Component.literal("Spike start failed: " + ex.getMessage()));
            return 0;
        }
    }

    private static int abort(CommandContext<CommandSourceStack> context) {
        int aborted = 0;
        for (UUID id : SpikeEncounters.activeIds()) {
            if (CobbleRaidsEncounters.abort(id)) aborted++;
        }
        int count = aborted;
        context.getSource().sendSuccess(() -> Component.literal("Aborted " + count + " spike encounter(s)."), true);
        return aborted;
    }

    /** Level with the player's gaze, so the boss lands in front of them whatever the pitch. */
    private static Vec3 aheadOf(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-6) flat = new Vec3(0.0, 0.0, 1.0);
        return player.position().add(flat.normalize().scale(DISTANCE_AHEAD));
    }
}
