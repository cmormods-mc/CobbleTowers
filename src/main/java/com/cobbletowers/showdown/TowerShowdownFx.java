package com.cobbletowers.showdown;

import com.cobbleraids.api.showdown.ShowdownExtensions;
import com.cobbletowers.TowerLog;
import java.io.InputStream;

/**
 * Registers CobbleTowers' Showdown extension with CobbleRaids (P23) from the mod initializer, before the server
 * starts: a module registered later only installs on the next boot. CobbleRaids does the installing; CobbleTowers
 * never edits Showdown's files.
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
