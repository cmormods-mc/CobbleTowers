package com.cobbletowers.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development tooling, inert unless the environment variable {@code COBBLETOWERS_REMOTE} names a directory: lets a test script drive a
 * real client that is connected to a real server (see {@code validation/client_e2e.py}). The script appends one command per line to
 * {@code commands.txt} in that directory; this runs them in order, one per client tick at most, and appends the line number to
 * {@code ack.txt} when each is done.
 *
 * <pre>
 * cmd tower draft        sends /tower draft as the player
 * press Keep these two   presses the button of the open screen whose label contains the text
 * click 213 120          clicks the open screen at a GUI position
 * center                 clicks the middle of the open screen
 * card 3                 clicks card 3 (0-based) of the open rental pack screen
 * inventory              opens the player's inventory screen; close closes the open screen
 * uncap                 removes vsync and the frame limit (frame-time runs)
 * hover card|button|none makes a widget of the open screen draw as hovered
 * sample begin NAME      records frame times under NAME until sample end; report writes frametime.txt
 * shot name              saves a picture of the frame as name.png
 * wait 800               waits that many milliseconds before the next command
 * quit                   closes the client
 * </pre>
 *
 * <p>It presses what a player would press and decides nothing. A production client never sets the variable, so none of it runs.
 */
public final class ClientRemote {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobbletowers-remote");

    private static Path directory;
    private static int done;
    private static long notBefore;

    private ClientRemote() {}

    public static void installIfRequested() {
        String where = System.getenv("COBBLETOWERS_REMOTE");
        if (where == null || where.isBlank() || FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        directory = Path.of(where);
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("ack.txt"), "", StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.error("The client remote could not be set up", ex);
            return;
        }
        LOGGER.info("Client remote armed, watching {}", directory);
        ClientTickEvents.END_CLIENT_TICK.register(ClientRemote::tick);
    }

    private static void tick(Minecraft minecraft) {
        if (System.currentTimeMillis() < notBefore) return;
        List<String> lines;
        try {
            Path file = directory.resolve("commands.txt");
            if (!Files.exists(file)) return;
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return;
        }
        if (done >= lines.size()) return;
        String line = lines.get(done).strip();
        try {
            run(minecraft, line);
        } catch (RuntimeException ex) {
            LOGGER.error("Remote command '{}' failed", line, ex);
        }
        done++;
        try {
            Files.writeString(directory.resolve("ack.txt"), done + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            LOGGER.error("Could not acknowledge", ex);
        }
    }

    private static void run(Minecraft minecraft, String line) {
        int space = line.indexOf(' ');
        String verb = space < 0 ? line : line.substring(0, space);
        String rest = space < 0 ? "" : line.substring(space + 1).strip();
        switch (verb) {
            case "wait" -> notBefore = System.currentTimeMillis() + Long.parseLong(rest);
            case "cmd" -> {
                if (minecraft.player != null) minecraft.player.connection.sendCommand(rest);
            }
            case "press" -> press(minecraft.screen, rest);
            case "click" -> {
                String[] xy = rest.split("\\s+");
                if (minecraft.screen != null) {
                    double x = Double.parseDouble(xy[0]);
                    double y = Double.parseDouble(xy[1]);
                    minecraft.screen.mouseClicked(x, y, 0);
                    minecraft.screen.mouseReleased(x, y, 0);
                }
            }
            case "center" -> {
                if (minecraft.screen != null) {
                    minecraft.screen.mouseClicked(minecraft.screen.width / 2.0, minecraft.screen.height / 2.0, 0);
                    minecraft.screen.mouseReleased(minecraft.screen.width / 2.0, minecraft.screen.height / 2.0, 0);
                }
            }
            case "card" -> {
                if (minecraft.screen instanceof RentalPackScreen pack) {
                    int[] p = pack.visiblePoint(Integer.parseInt(rest), 5, pack.scale());
                    pack.mouseClicked(p[0], p[1], 0);
                }
            }
            case "inventory" -> {
                if (minecraft.player != null) minecraft.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(minecraft.player));
            }
            case "close" -> minecraft.setScreen(null);
            case "summary" -> {
                // Opens Cobblemon's own summary screen on the player's party (select = index, default 0).
                var party = com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getStorage().getParty();
                var pokemon = new java.util.ArrayList<com.cobblemon.mod.common.pokemon.Pokemon>();
                for (var slot : party.getSlots()) if (slot != null) pokemon.add(slot);
                int select = rest.isBlank() ? 0 : Integer.parseInt(rest.trim());
                com.cobblemon.mod.common.client.gui.summary.Summary.Companion.open(pokemon, true, select);
            }
            case "widgets" -> {
                if (minecraft.screen == null) LOGGER.info("Widgets: no screen");
                else for (GuiEventListener child : minecraft.screen.children()) {
                    if (child instanceof AbstractWidget w) LOGGER.info("Widget {} '{}' at {},{} {}x{} visible={} active={}", w.getClass().getName(),
                            w.getMessage().getString(), w.getX(), w.getY(), w.getWidth(), w.getHeight(), w.visible, w.active);
                    else LOGGER.info("Child {}", child.getClass().getName());
                }
                LOGGER.info("Screen {} {}x{}", minecraft.screen == null ? "-" : minecraft.screen.getClass().getName(),
                        minecraft.screen == null ? 0 : minecraft.screen.width, minecraft.screen == null ? 0 : minecraft.screen.height);
            }
            case "widget" -> {
                // widget <class name fragment>: clicks the centre of the first widget whose class name contains the text.
                if (minecraft.screen != null) for (GuiEventListener child : minecraft.screen.children()) {
                    if (child instanceof AbstractWidget w && w.getClass().getName().contains(rest.trim())) {
                        double x = w.getX() + w.getWidth() / 2.0, y = w.getY() + w.getHeight() / 2.0;
                        minecraft.screen.mouseClicked(x, y, 0);
                        minecraft.screen.mouseReleased(x, y, 0);
                        LOGGER.info("Clicked widget {}", w.getClass().getName());
                        break;
                    }
                }
            }
            case "uncap" -> FrameSampler.uncap();
            case "hover" -> FrameSampler.hoverKind(rest.trim());
            case "sample" -> {
                // sample begin <phase> / sample end: frame-time recording (FrameSampler); report writes frametime.txt.
                if (rest.startsWith("begin ")) FrameSampler.begin(rest.substring(6).trim());
                else FrameSampler.end();
            }
            case "report" -> FrameSampler.report(directory);
            case "shot" -> shot(minecraft, rest);
            case "quit" -> minecraft.stop();
            default -> LOGGER.warn("Unknown remote command '{}'", line);
        }
    }

    /** Presses the first button of the open screen whose label contains {@code label}. */
    private static void press(Screen screen, String label) {
        if (screen == null) return;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.visible && button.active
                    && button.getMessage().getString().contains(label)) {
                button.onPress();
                LOGGER.info("Pressed '{}'", button.getMessage().getString());
                return;
            }
        }
        LOGGER.warn("No active button labelled '{}' on {}", label, screen.getClass().getSimpleName());
    }

    private static void shot(Minecraft minecraft, String name) {
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            image.writeToFile(directory.resolve(name + ".png"));
            LOGGER.info("Saved {} ({})", name, minecraft.screen == null ? "no screen" : minecraft.screen.getClass().getSimpleName());
        } catch (IOException ex) {
            LOGGER.error("Could not save {}", name, ex);
        }
    }
}
