package com.cobbletowers.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokeball.PokemonCatchRateEvent;
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent;
import com.cobblemon.mod.common.api.events.pokemon.ShinyChanceCalculationEvent;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletowers.armor.ActiveBonuses;
import com.cobbletowers.armor.ArmorBonusEffects;
import com.cobbletowers.armor.WornSets;
import com.cobbletowers.economy.RaidPointsCurrency;
import com.cobbletowers.persistence.PendingTowerReward;
import com.cobbletowers.persistence.TowerPendingRewardStore;
import com.cobbletowers.reward.RewardDelivery;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Operator tools for armor sets (P24): what a player's worn armor switches on, and a probe that proves the bonuses
 * reach the game.
 *
 * <p>The probe exists because three of the bonuses live inside Cobblemon's own event flow (experience, catch rate,
 * shiny odds), where nothing in a tower run reliably triggers them. It builds each event the way Cobblemon does, posts
 * it on the real {@code CobblemonEvents} bus -- so the handlers CobbleTowers subscribed are what answers, on whatever
 * Cobblemon is actually installed -- and reports the number that came out. Nothing is granted, caught or rolled.
 */
public final class ArmorCommand {

    private ArmorCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers")
                .then(Commands.literal("armor")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("show")
                                .then(Commands.argument("player", EntityArgument.player()).executes(ArmorCommand::show)))
                        .then(Commands.literal("probe")
                                .then(Commands.argument("player", EntityArgument.player()).executes(ArmorCommand::probe)))
                        .then(Commands.literal("points")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
                                                .executes(ArmorCommand::points))))));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        WornSets.refresh(player);
        ActiveBonuses bonuses = WornSets.of(player.getUUID());
        String text = String.format(Locale.ROOT,
                "armor bonuses for %s: attributes=%d xp=%d%% catch=%d%% shiny=%d%% battle=%d vendor=-%d%% raidpoints=+%d%%",
                player.getGameProfile().getName(), bonuses.attributes().size(), bonuses.xpPercent(),
                bonuses.catchRatePercent(), bonuses.shinyPercent(), bonuses.battleEffects().size(),
                bonuses.vendorDiscountPercent(), bonuses.raidPointsPercent());
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** Fires the three Cobblemon events for the player's first party Pokemon and reports each result. */
    private static int probe(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        WornSets.refresh(player);

        Pokemon pokemon = null;
        for (Pokemon candidate : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            pokemon = candidate;
            break;
        }
        if (pokemon == null) {
            source.sendFailure(Component.literal(player.getGameProfile().getName() + " has no Pokemon to probe with"));
            return 0;
        }

        ExperienceGainedEvent.Pre experience = new ExperienceGainedEvent.Pre(pokemon, new SidemodExperienceSource("cobbletowers"), 1000);
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.post(experience);

        ShinyChanceCalculationEvent shiny = new ShinyChanceCalculationEvent(1000f, pokemon);
        CobblemonEvents.SHINY_CHANCE_CALCULATION.post(shiny);
        float shinyRate = shiny.calculate(player);

        PokemonEntity target = new PokemonEntity(player.serverLevel(), pokemon, CobblemonEntities.POKEMON);
        EmptyPokeBallEntity ball = new EmptyPokeBallEntity(PokeBalls.INSTANCE.getPOKE_BALL(), player.serverLevel(), player,
                CobblemonEntities.EMPTY_POKEBALL);
        PokemonCatchRateEvent catchRate = new PokemonCatchRateEvent(player, ball, target, 100f);
        CobblemonEvents.POKEMON_CATCH_RATE.post(catchRate);

        String text = String.format(Locale.ROOT, "armor probe for %s: xp 1000 -> %d | catch 100.0 -> %.1f | shiny 1000.0 -> %.1f",
                player.getGameProfile().getName(), experience.getExperience(), catchRate.getCatchRate(), shinyRate);
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /**
     * Queues a Raid Points reward and delivers it through the real path ({@link RewardDelivery#deliver}), so the Raid
     * Points bonus is measured where it is applied: in CobbleRaids' own balance, afterwards.
     */
    private static int points(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        WornSets.refresh(player);
        TowerPendingRewardStore.get(source.getServer()).add(player.getUUID(),
                new PendingTowerReward(UUID.randomUUID(), 1, RaidPointsCurrency.ITEM_ID, amount, System.currentTimeMillis()));
        int delivered = RewardDelivery.deliver(source.getServer(), player);
        source.sendSuccess(() -> Component.literal("delivered " + delivered + " reward(s) of " + amount
                + " Raid Points; the bonus applied would be " + ArmorBonusEffects.raidPoints(player, amount)), false);
        return delivered;
    }
}
