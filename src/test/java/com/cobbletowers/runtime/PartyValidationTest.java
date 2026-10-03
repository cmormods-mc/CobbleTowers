package com.cobbletowers.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.RulesetDefinition;
import com.cobbletowers.runtime.PartyValidation.PartyMember;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a party must be before a run takes an instance, and which of it a floor is levelled from. */
class PartyValidationTest {

    private static RulesetDefinition ruleset(int size, boolean ready) {
        return RulesetDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("cobbletowers", "test"),
                JsonParser.parseString("{\"schema_version\":1,\"revision\":1,\"registered_party_size\":" + size
                        + ",\"requires_battle_ready_party\":" + ready + "}").getAsJsonObject());
    }

    private static PartyMember member(int n, int level, boolean fainted) {
        return new PartyMember(UUID.fromString("00000000-0000-0000-0000-00000000000" + n), level, fainted);
    }

    @Test
    @DisplayName("a healthy party registers in party order and is valid")
    void healthyParty() {
        var party = List.of(member(1, 20, false), member(2, 30, false));
        var result = PartyValidation.validate(party, ruleset(6, true));

        assertTrue(result.valid());
        assertEquals(List.of(party.get(0).id(), party.get(1).id()), result.registered());
    }

    @Test
    @DisplayName("an empty party is not valid")
    void emptyParty() {
        assertFalse(PartyValidation.validate(List.of(), ruleset(6, true)).valid());
    }

    @Test
    @DisplayName("a party over the ruleset's size registers its first members rather than failing")
    void oversizeParty() {
        var party = List.of(member(1, 20, false), member(2, 20, false), member(3, 20, false));
        var result = PartyValidation.validate(party, ruleset(2, true));

        assertTrue(result.valid());
        assertEquals(List.of(party.get(0).id(), party.get(1).id()), result.registered());
    }

    @Test
    @DisplayName("a fainted registered Pokemon fails a battle-ready ruleset and passes one that is not")
    void readiness() {
        var party = List.of(member(1, 20, false), member(2, 20, true));

        assertFalse(PartyValidation.validate(party, ruleset(6, true)).valid());
        assertTrue(PartyValidation.validate(party, ruleset(6, false)).valid());
    }

    @Test
    @DisplayName("a fainted Pokemon outside the registered size does not count against readiness")
    void faintedOutsideRegistered() {
        var party = List.of(member(1, 20, false), member(2, 20, true));

        assertTrue(PartyValidation.validate(party, ruleset(1, true)).valid());
    }

    @Test
    @DisplayName("duplicates are legal: nothing but fainting and size is a rule")
    void duplicatesAllowed() {
        var one = member(1, 20, false);
        assertTrue(PartyValidation.validate(List.of(one, one), ruleset(6, true)).valid());
    }

    @Test
    @DisplayName("levels come from the registered Pokemon, ignoring one registered nowhere")
    void levelsFromRegistered() {
        var live = List.of(member(1, 20, false), member(2, 50, false), member(3, 80, false));

        assertEquals(List.of(20, 50), PartyValidation.levels(live, List.of(live.get(0).id(), live.get(1).id())));
    }

    @Test
    @DisplayName("a registered Pokemon that has left the party is skipped, not fatal")
    void levelsSkipMissing() {
        var live = List.of(member(1, 20, false));
        var gone = UUID.fromString("99999999-0000-0000-0000-000000000000");

        assertEquals(List.of(20), PartyValidation.levels(live, List.of(live.get(0).id(), gone)));
    }

    @Test
    @DisplayName("with nothing registered, or nothing findable, the live party is used")
    void levelsFallBack() {
        var live = List.of(member(1, 20, false), member(2, 40, false));
        var gone = UUID.fromString("99999999-0000-0000-0000-000000000000");

        assertEquals(List.of(20, 40), PartyValidation.levels(live, List.of()));
        assertEquals(List.of(20, 40), PartyValidation.levels(live, List.of(gone)));
    }
}
