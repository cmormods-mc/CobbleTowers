package com.cobbletowers.showdown;

import com.cobbleraids.api.showdown.ShowdownExtensions;
import com.cobbletowers.TowerLog;
import java.io.InputStream;

/**
 * Registers CobbleTowers' Showdown extension with CobbleRaids (P23).
 *
 * <p>Called from the mod initializer, before the server starts: the simulator is unbundled and its context built
 * during server start-up, and a module registered later is only installed on the next boot. CobbleRaids does the
 * installing, at the same moments and with the same repairs as its own patch -- CobbleTowers never edits Showdown's
 * files, which is the whole reason this does not become a third party racing for them.
 */
public final class TowerShowdownFx {

    /** The extension id; becomes {@code showdown/ext-cobbletowers-fx.js}. */
    public static final String MODULE_ID = "cobbletowers-fx";
    static final String RESOURCE = "/assets/cobbletowers/showdown/tower-fx.js";

    private TowerShowdownFx() {}

    public static void install() {
        ShowdownExtensions.registerModule(MODULE_ID, () -> TowerShowdownFx.class.getResourceAsStream(RESOURCE));
        ShowdownExtensions.registerFormatFields(TowerBattleFx::fieldsFor);
        TowerLog.info("Registered the CobbleTowers Showdown extension.");
    }

    /** For a test that wants to read the module the same way CobbleRaids will. */
    static InputStream module() {
        return TowerShowdownFx.class.getResourceAsStream(RESOURCE);
    }
}
