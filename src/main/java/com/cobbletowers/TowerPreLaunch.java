package com.cobbletowers;

import com.cobbletowers.showdown.TowerShowdownFx;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Runs before any mod initializes (P23): registers the Showdown extension with CobbleRaids, which cannot wait for
 * {@link CobbleTowers#onInitialize()} (a late module only loads on the next boot). Plain Java and guarded, since
 * Minecraft is not usable yet and an optional feature must not stop the server.
 */
public final class TowerPreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        try {
            TowerShowdownFx.install();
        } catch (RuntimeException | LinkageError ex) {
            // TowerLog may not be usable this early on every setup, so this goes to the plain logger.
            System.err.println("[CobbleTowers] Could not register the Showdown extension; tower battle effects are off: " + ex);
        }
    }
}
