package com.cobbletowers;

import com.cobbletowers.showdown.TowerShowdownFx;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Runs before any mod initializes (P23 fix).
 *
 * <p>The one thing here is registering the Showdown extension with CobbleRaids. It cannot wait for
 * {@link CobbleTowers#onInitialize()}: Cobblemon starts its Showdown service on its own thread while mods are still
 * initializing, and CobbleRaids writes the extension files and the list the simulator loads them from the moment it
 * unbundles. A module registered from the initializer arrives after that and is installed but not loaded until the
 * <em>next</em> boot, so the first version of this patch never ran at all.
 *
 * <p>Kept to plain Java on purpose: a pre-launch entrypoint runs before Minecraft's classes are usable. Guarded, because
 * an exception here would stop the server from starting over a feature that is optional.
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
