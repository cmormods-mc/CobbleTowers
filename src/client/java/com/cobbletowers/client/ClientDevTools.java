package com.cobbletowers.client;

/** Test tooling: only in the dev build (-Pdev). Each part stays inert unless its environment variable is set. */
public final class ClientDevTools {

    private ClientDevTools() {}

    public static void install() {
        ScreenshotHarness.installIfRequested();
        ClientRemote.installIfRequested();
    }
}
