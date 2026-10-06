package com.cobbletowers.menu;

import com.cobbletowers.definition.ModifierDefinition;
import java.util.Locale;

/**
 * Which scene a modifier card is painted with. A short, stable key the client maps to a diorama; the client knows nothing about
 * modifier rules, so the key is chosen here from the definition and says only what the definition does.
 *
 * <p>The art must not imply an effect the modifier does not have: a modifier that pays less is never given the open-coffer scene
 * of one that pays more, so a reward modifier is keyed by the direction of its direct reward factor. Anything unrecognised is
 * {@code unknown}, which the client paints with a neutral scene and no sprite.
 */
public final class ModifierArt {

    public static final String UNKNOWN = "unknown";
    public static final String EVENT = "event";

    private ModifierArt() {}

    public static String theme(ModifierDefinition modifier) {
        if (modifier == null) return UNKNOWN;
        var effect = modifier.effect();
        if (effect.weather().isPresent()) return "weather:" + effect.weather().get().toLowerCase(Locale.ROOT);
        if (effect.terrain().isPresent()) return "terrain:" + effect.terrain().get().toLowerCase(Locale.ROOT);
        if (effect.custom().isPresent()) return "custom:" + effect.custom().get().toLowerCase(Locale.ROOT);
        return switch (modifier.type()) {
            case ENEMY -> "enemy";
            case ENCOUNTER -> "encounter";
            case PLAYER_CONSTRAINT -> "constraint";
            case SCOUTING -> "scouting";
            case REWARD -> effect.rewardPercent() > 100 ? "reward_up" : effect.rewardPercent() < 100 ? "reward_down" : UNKNOWN;
            case FIELD -> UNKNOWN;
            case CUSTOM -> UNKNOWN;
        };
    }
}
