package com.cobbletowers.battle.cobblemon;

import com.cobbletowers.network.RentalDraftPayload;
import com.cobblemon.mod.common.api.moves.Moves;
import java.util.Locale;

/**
 * Where a rental card's move gets its type and damage category: Cobblemon's own move data, so a card can never disagree with the
 * battle. A move Cobblemon does not know (a typo in a set, or data not loaded) comes back with no type, and the client draws a plain gem.
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
