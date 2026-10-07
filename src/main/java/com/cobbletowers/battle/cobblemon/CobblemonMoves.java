package com.cobbletowers.battle.cobblemon;

import com.cobbletowers.network.RentalDraftPayload;
import com.cobblemon.mod.common.api.moves.Moves;
import java.util.Locale;

/**
 * A rental card move's type and damage category from Cobblemon's own data, so a card cannot disagree with the battle.
 * An unknown move has no type and draws a plain gem.
 */
public final class CobblemonMoves {

    private CobblemonMoves() {}

    public static RentalDraftPayload.Move resolve(String id) {
        try {
            var template = Moves.INSTANCE.getByName(id);
            if (template == null) return new RentalDraftPayload.Move(id, "", "");
            return new RentalDraftPayload.Move(id, template.getElementalType().getName().toLowerCase(Locale.ROOT),
                    template.getDamageCategory().getName().toLowerCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            return new RentalDraftPayload.Move(id, "", "");
        }
    }
}
