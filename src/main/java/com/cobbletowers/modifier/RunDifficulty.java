package com.cobbletowers.modifier;

import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.persistence.RunModifierState;
import net.minecraft.resources.ResourceLocation;

/**
 * How hard a run is right now, as one number and a word (the Current Run tab). Each held challenge scores 1, 2 or 3 by its
 * risk tier (a locked-in copy scores again), and every Ascension scores 3. Relics are boons and score nothing. Pure.
 */
public record RunDifficulty(int points, String label, int challenges, int ascension) {

    static final int ASCENSION_POINTS = 3;

    public static RunDifficulty of(TowerContent content, RunModifierState state, int ascension) {
        int points = ascension * ASCENSION_POINTS;
        for (ResourceLocation id : state.accumulated()) points += pointsOf(content, id);
        for (ResourceLocation id : state.lockedIn()) points += pointsOf(content, id);
        return new RunDifficulty(points, labelOf(points), state.accumulated().size(), ascension);
    }

    private static int pointsOf(TowerContent content, ResourceLocation id) {
        return content.modifier(id).map(ModifierDefinition::risk).map(risk -> risk.ordinal() + 1).orElse(0);
    }

    /** The word for a score: Standard, Challenging, Hard, Brutal, Nightmare. */
    public static String labelOf(int points) {
        if (points <= 0) return "Standard";
        if (points <= 3) return "Challenging";
        if (points <= 7) return "Hard";
        if (points <= 12) return "Brutal";
        return "Nightmare";
    }

    /** One line for the menu: "Hard (6): 3 challenges, Ascension 1". */
    public String line() {
        return label + " (" + points + "): " + challenges + (challenges == 1 ? " challenge" : " challenges")
                + (ascension > 0 ? ", Ascension " + ascension : "");
    }
}
