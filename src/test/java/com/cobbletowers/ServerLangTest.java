package com.cobbletowers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ServerLangTest {

    @Test
    void namesAnItemFromTheBundledLang() {
        assertEquals("Tower Key", ServerLang.itemName(ResourceLocation.parse("cobbletowers:tower_key")).getString());
        assertEquals("Rental Key", ServerLang.itemName(ResourceLocation.parse("cobbletowers:rental_key")).getString());
    }

    @Test
    void unknownItemFallsBackToItsId() {
        assertEquals("cobbletowers:nothing", ServerLang.itemName(ResourceLocation.parse("cobbletowers:nothing")).getString());
    }
}
