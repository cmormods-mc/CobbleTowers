package com.cobbletowers.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cobbletowers.definition.RentalSetDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A rental's AscensionLib rarity is its set's rarity, spelled the way the library spells it. */
class AscensionLibGrantsTest {

    @Test
    @DisplayName("every rental rarity maps to the library's id for it, and only mythic is spelled differently")
    void rarityIds() {
        assertEquals("common", AscensionLibGrants.rarityId(RentalSetDefinition.Rarity.COMMON));
        assertEquals("uncommon", AscensionLibGrants.rarityId(RentalSetDefinition.Rarity.UNCOMMON));
        assertEquals("rare", AscensionLibGrants.rarityId(RentalSetDefinition.Rarity.RARE));
        assertEquals("epic", AscensionLibGrants.rarityId(RentalSetDefinition.Rarity.EPIC));
        assertEquals("legendary", AscensionLibGrants.rarityId(RentalSetDefinition.Rarity.LEGENDARY));
        assertEquals("mythical", AscensionLibGrants.rarityId(RentalSetDefinition.Rarity.MYTHIC));
    }
}
