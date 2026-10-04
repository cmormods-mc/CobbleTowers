package com.cobbletowers.runtime;

import java.util.Locale;
import java.util.Set;

/**
 * Which commands are turned off inside the tower (P27): the ones that undo what the tower is for.
 *
 * <p>A tower fight is won with the party as it stands -- healed only by the vendor, which costs CobbleDollars, and
 * moved only by the run. {@code /pokeheal}, a home or warp command, a PC or ender chest, or a kit would each be a way
 * around that, so none of them may be used from inside it. Pure: a command line in, a yes or no out.
 *
 * <p>This is a deny list, not an allow list, because the server's command set is whatever its mods add and an allow
 * list would break every one of them. It matches the first word only, with or without a {@code namespace:} in front
 * (so {@code /cobblemon:pokeheal} does not slip past {@code pokeheal}). Operators extend it from a file; see
 * {@link TowerCommandGuard}.
 */
public final class CommandRules {

    /** Healing and cheating, party and storage changes, getting out, and carrying things in. */
    public static final Set<String> DEFAULTS = Set.of(
            // healing and feeding
            "pokeheal", "healpokemon", "heal", "feed", "repair", "fix", "god", "fly", "flyspeed", "walkspeed",
            // changing the party
            "pokegive", "pokegiveother", "givepokemon", "pokespawn", "spawnpokemon", "evolve", "releasepokemon",
            "deletepokemon", "changeaspect", "teachmove", "setlevel", "levelup", "pokemonedit", "pokeedit",
            // storage: PC, boxes, chests, trading
            "pc", "box", "boxes", "pokebox", "pokepc", "party", "trade", "ec", "enderchest", "echest", "chest",
            "backpack", "bp", "craft", "workbench", "wb", "anvil", "grindstone", "smithingtable", "stonecutter",
            // getting out, or in somewhere else
            "tp", "teleport", "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tpr", "spawn", "hub", "lobby",
            "home", "homes", "sethome", "delhome", "warp", "warps", "setwarp", "back", "dback", "rtp", "wild",
            "randomtp", "top", "jump", "spreadplayers", "spawnpoint", "setworldspawn",
            // shops, kits and free items
            "kit", "kits", "give", "shop", "sell", "buy", "market", "ah", "auction", "bank", "gift", "skull", "hat",
            "more", "item", "i",
            // changing yourself
            "gamemode", "gm", "gmc", "gms", "gmsp", "effect", "xp", "experience", "clear", "enchant",
            "suicide", "kill");

    /** Commands that always work, even if an operator lists them by mistake: the way out and the way to play. */
    private static final Set<String> ALWAYS = Set.of("tower", "cobbletowers");

    private CommandRules() {}

    /** The command word: the first token, without a leading slash or a {@code namespace:}, in lower case. */
    public static String wordOf(String commandLine) {
        String line = commandLine.strip();
        if (line.startsWith("/")) line = line.substring(1);
        int space = line.indexOf(' ');
        String first = space < 0 ? line : line.substring(0, space);
        int colon = first.indexOf(':');
        if (colon >= 0) first = first.substring(colon + 1);
        return first.toLowerCase(Locale.ROOT);
    }

    /** Whether {@code commandLine} is turned off, given the full deny list (defaults plus any the operator added). */
    public static boolean isBlocked(String commandLine, Set<String> denied) {
        String word = wordOf(commandLine);
        return !word.isEmpty() && !ALWAYS.contains(word) && denied.contains(word);
    }
}
