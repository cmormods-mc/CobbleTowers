package com.cobbletowers.armor;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokeball.PokemonCatchRateEvent;
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent;
import com.cobblemon.mod.common.api.events.pokemon.ShinyChanceCalculationEvent;
import com.cobbletowers.TowerLog;
import com.google.gson.JsonArray;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where the non-attribute bonuses of a worn set touch the game (P24): the three Cobblemon numbers, plus the small pure
 * helpers the tower mechanics call. Each Cobblemon handler is guarded: it runs inside Cobblemon's own flow, where an
 * exception would unwind into the caller, and the failure mode we want is "Cobblemon's own number, unchanged".
 */
public final class ArmorBonusEffects {

    private static boolean installed;

    private ArmorBonusEffects() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(ArmorBonusEffects::onExperience);
        CobblemonEvents.POKEMON_CATCH_RATE.subscribe(ArmorBonusEffects::onCatchRate);
        CobblemonEvents.SHINY_CHANCE_CALCULATION.subscribe(ArmorBonusEffects::onShiny);
        TowerLog.info("Armor set Cobblemon bonuses installed");
    }

    // ---- Cobblemon ----------------------------------------------------------------------------------------------

    private static void onExperience(ExperienceGainedEvent.Pre event) {
        try {
            ServerPlayer owner = event.getPokemon().getOwnerPlayer();
            if (owner == null) return;
            int percent = WornSets.of(owner.getUUID()).xpPercent();
            if (percent > 0) event.setExperience(scale(event.getExperience(), percent));
        } catch (RuntimeException ex) {
            TowerLog.error("The armor experience bonus failed; the experience is unchanged", ex);
        }
    }

    private static void onCatchRate(PokemonCatchRateEvent event) {
        try {
            if (!(event.getThrower() instanceof ServerPlayer thrower)) return;
            int percent = WornSets.of(thrower.getUUID()).catchRatePercent();
            if (percent > 0) event.setCatchRate(event.getCatchRate() * (1f + percent / 100f));
        } catch (RuntimeException ex) {
            TowerLog.error("The armor catch-rate bonus failed; the rate is unchanged", ex);
        }
    }

    private static void onShiny(ShinyChanceCalculationEvent event) {
        try {
            event.addModificationFunction((chance, player, pokemon) -> {
                try {
                    if (player == null) return chance;
                    int percent = WornSets.of(player.getUUID()).shinyPercent();
                    return percent > 0 ? chance * (1f + percent / 100f) : chance;
                } catch (RuntimeException ex) {
                    return chance;
                }
            });
        } catch (RuntimeException ex) {
            TowerLog.error("The armor shiny bonus failed; the odds are unchanged", ex);
        }
    }

    // ---- tower mechanics ------------------------------------------------------------------------------------------

    /** {@code amount} raised by {@code percent}, rounded to nearest, never below the amount. */
    public static int scale(int amount, int percent) {
        if (amount <= 0 || percent <= 0) return amount;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(amount * (1.0 + percent / 100.0)));
    }

    /** The price {@code player} pays for something that costs {@code base}: discounted, but never free, never negative. */
    public static int discountedPrice(int base, int discountPercent) {
        if (base <= 0 || discountPercent <= 0) return Math.max(0, base);
        return Math.max(1, (int) Math.round(base * (100 - Math.min(discountPercent, 100)) / 100.0));
    }

    /** The player's vendor price for a service listed at {@code base}. */
    public static int vendorPrice(UUID player, int base) {
        return discountedPrice(base, WornSets.of(player).vendorDiscountPercent());
    }

    /** Raid Points to award {@code player} for a base grant of {@code base}. */
    public static int raidPoints(UUID player, int base) {
        return scale(base, WornSets.of(player).raidPointsPercent());
    }

    /** The wearer's battle operations, ready to merge into {@code TowerBattleFx.armFloorBattle}. */
    public static JsonArray battleEffects(UUID player) {
        return WornSets.of(player).battleEffects();
    }
}
