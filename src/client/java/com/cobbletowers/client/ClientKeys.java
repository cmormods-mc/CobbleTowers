package com.cobbletowers.client;

import net.minecraft.client.KeyMapping;

/** Key names the HUDs print, read from the player's own bindings. */
final class ClientKeys {

    private ClientKeys() {}

    static String runEffectsKey() {
        KeyMapping mapping = CobbleTowersClient.RUN_EFFECTS;
        return mapping == null ? "K" : mapping.getTranslatedKeyMessage().getString();
    }
}
