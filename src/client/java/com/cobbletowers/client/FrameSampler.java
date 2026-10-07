package com.cobbletowers.client;

import com.cobbletowers.CobbleTowers;
import com.cobbletowers.network.PlayStatePayload;
import com.cobbletowers.network.TowerHallStatePayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.lwjgl.opengl.GL11;

/**
 * Development tooling for {@link ScreenshotHarness} and {@link ClientRemote} ({@code COBBLETOWERS_FRAMETIME_ONLY=1}):
 * measures Hall draw time and whole-frame time, then each building block on its own. The report is {@code
 * frametime.txt} in the harness's output folder.
 */
final class FrameSampler {

    /** Schedules one harness step: wait, label, action. */
    interface Adder {
        void add(long waitMs, String label, Runnable action);
    }

    /** What one phase collected: draw nanoseconds of the screen, whole-frame nanoseconds, frames seen hovered. */
    private static final class Stats {
        final List<Long> draw = new ArrayList<>();
        final List<Long> frame = new ArrayList<>();
        int hovered;
    }

    private static final Map<String, Stats> SAMPLES = new LinkedHashMap<>();
    private static String phase = "";
    private static boolean recording;
    private static boolean installed;
    private static long renderStart;
    private static long lastFrame;
    private static AbstractWidget target;
    private static String blocks = "";
    private static Path directory;

    private FrameSampler() {}

    /** Starts watching every frame. Idempotent; nothing is recorded until {@link #begin}. */
    static void install() {
        if (installed) return;
        installed = true;
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (!(screen instanceof TowerScreen)) return;
            ScreenEvents.beforeRender(screen).register((s, g, mouseX, mouseY, delta) -> renderStart = System.nanoTime());
            ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, delta) -> {
                if (!recording) return;
                Stats stats = SAMPLES.computeIfAbsent(phase, key -> new Stats());
                stats.draw.add(System.nanoTime() - renderStart);
                if (target != null && target.isHoveredOrFocused()) stats.hovered++;
            });
        });
        HudRenderCallback.EVENT.register((g, delta) -> {
            long now = System.nanoTime();
            if (recording && lastFrame != 0) SAMPLES.computeIfAbsent(phase, key -> new Stats()).frame.add(now - lastFrame);
            lastFrame = now;
        });
    }

    static void begin(String name) {
        install();
        phase = name;
        recording = true;
    }

    static void end() {
        recording = false;
    }

    /**
     * Removes the frame limit and vsync, so the whole-frame time is what the machine can do rather than the monitor's
     * rate.
     */
    static void uncap() {
        var options = Minecraft.getInstance().options;
        options.enableVsync().set(false);
        options.framerateLimit().set(260);
        Minecraft.getInstance().getWindow().updateVsync(false);
    }

    /** Hovers the first widget of the open screen of a kind: card, button, or none. */
    static void hoverKind(String kind) {
        switch (kind) {
            case "card" -> hover(pick(w -> w.getClass().getSimpleName().equals("DestinationButton")));
            case "button" -> hover(pick(w -> w instanceof TowerButton));
            default -> hover(null);
        }
    }

    static void script(Adder adder, Path out) {
        directory = out;
        install();
        var towers = List.of("tideforge", "rootvale", "duskvale", "neutral", "custom_arena").stream()
                .map(id -> new TowerHallStatePayload.Destination(CobbleTowers.id(id),
                        id.substring(0, 1).toUpperCase() + id.substring(1) + " Tower", 10, 2, true)).toList();
        var play = new PlayStatePayload(List.of(), PlayStatePayload.Lobby.none(), List.of(50, 50, 50), "", false);
        var state = new TowerHallStatePayload(towers, List.of(), play, "", false);

        adder.add(0, "Hall", () -> Minecraft.getInstance().setScreen(new TowerHallScreen(state)));
        phase(adder, "idle", () -> hover(null));
        phase(adder, "hover_card", () -> hoverKind("card"));
        phase(adder, "hover_button", () -> hoverKind("button"));
        phase(adder, "hover_card_no_tooltip", () -> {
            for (AbstractWidget w : widgets()) w.setTooltip(null);
            hoverKind("card");
        });
        phase(adder, "idle_no_glow", () -> {
            TowerUiSettings.glow = false;
            hover(null);
        });
        adder.add(0, "Modifier screen", () -> {
            TowerUiSettings.glow = true;
            Minecraft.getInstance().setScreen(ScreenshotHarness.sampleModifierScreen());
        });
        adder.add(2200, "Modifier revealed", () -> {});
        phase(adder, "modifier_idle", () -> hover(null));
        adder.add(0, "Building blocks", FrameSampler::microbench);
        adder.add(0, "Report", () -> report(directory));
    }

    private static void phase(Adder adder, String name, Runnable setup) {
        adder.add(0, name + " setup", () -> {
            recording = false;
            setup.run();
        });
        adder.add(800, name + " start", () -> begin(name));
        adder.add(3500, name + " stop", FrameSampler::end);
    }

    private static List<AbstractWidget> widgets() {
        var screen = Minecraft.getInstance().screen;
        if (screen == null) return List.of();
        List<AbstractWidget> widgets = new ArrayList<>();
        for (var child : screen.children()) if (child instanceof AbstractWidget widget) widgets.add(widget);
        return widgets;
    }

    private static AbstractWidget pick(Predicate<AbstractWidget> test) {
        return widgets().stream().filter(test).findFirst().orElseThrow(() -> new IllegalStateException("no such widget on the screen"));
    }

    /**
     * Makes a widget draw as hovered (nothing for null) by giving it keyboard focus, since a scripted window gets no
     * mouse events.
     */
    private static void hover(AbstractWidget widget) {
        target = widget;
        var screen = Minecraft.getInstance().screen;
        if (screen != null) screen.setFocused(widget);
    }

    private static void microbench() {
        var minecraft = Minecraft.getInstance();
        var g = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        int w = minecraft.getWindow().getGuiScaledWidth();
        int h = minecraft.getWindow().getGuiScaledHeight();
        StringBuilder out = new StringBuilder();
        out.append(String.format("%nBuilding blocks at GUI %dx%d (ms per call, GPU waited on):%n", w, h));
        bench(out, "backdrop (oak tiles + gradient)", g, () -> PixelUi.backdrop(g, w, h, 0));
        bench(out, "one nine-slice frame 120x40", g, () -> PixelUi.frame(g, PixelUi.Frame.BUTTON, 20, 20, 120, 40));
        bench(out, "20 nine-slice frames", g, () -> {
            for (int i = 0; i < 20; i++) PixelUi.frame(g, PixelUi.Frame.BUTTON, 10, 10 + i * 3, 120, 20);
        });
        for (String region : new String[] {"tideforge", "rootvale", "duskvale", "neutral"}) {
            bench(out, "panorama " + region + " 100x70", g, () -> TowerPanorama.draw(g, 20, 20, 100, 70, region, 1400, false));
        }
        bench(out, "tooltip (3 lines)", g, () -> g.renderTooltip(minecraft.font, List.of(
                net.minecraft.network.chat.Component.literal("Tideforge Tower"), net.minecraft.network.chat.Component.literal("A sea tower"),
                net.minecraft.network.chat.Component.literal("10 floors")).stream().map(c -> c.getVisualOrderText()).toList(), 100, 100));
        for (String theme : new String[] {"weather:raindance", "enemy:tough", "constraint:no_switch", "reward_up:hoard", "encounter:crowd"}) {
            bench(out, "modifier scene " + theme + " 90x100", g, () -> ModifierScene.draw(g, 20, 20, 90, 100, theme, 1400, true, 26));
        }
        bench(out, "one line of text (UI font)", g, () -> g.drawString(TowerFonts.get(), "Tideforge Tower", 20, 20, 0xFFFFFFFF, false));
        bench(out, "one line of text", g, () -> g.drawString(minecraft.font, "Tideforge Tower", 20, 20, 0xFFFFFFFF, false));
        blocks = out.toString();
    }

    private static void bench(StringBuilder out, String name, GuiGraphics g, Runnable draw) {
        for (int i = 0; i < 30; i++) draw.run();
        g.flush();
        GL11.glFinish();
        int runs = 200;
        long start = System.nanoTime();
        for (int i = 0; i < runs; i++) {
            draw.run();
            g.flush();
        }
        GL11.glFinish();
        out.append(String.format("  %-36s %8.3f ms%n", name, (System.nanoTime() - start) / 1e6 / runs));
    }

    static void report(Path out) {
        StringBuilder text = new StringBuilder();
        var window = Minecraft.getInstance().getWindow();
        text.append(String.format("Frame time, GUI scale %d, window %dx%d%n", (int) window.getGuiScale(), window.getWidth(), window.getHeight()));
        text.append(String.format("%-24s %7s %10s %10s %10s %10s %8s %8s%n",
                "phase", "frames", "draw avg", "draw p95", "draw p99", "frame avg", "fps", "hovered"));
        for (var entry : SAMPLES.entrySet()) {
            Stats stats = entry.getValue();
            long[] draw = stats.draw.stream().mapToLong(Long::longValue).sorted().toArray();
            double drawAvg = Arrays.stream(draw).average().orElse(0) / 1e6;
            double frameAvg = stats.frame.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
            double p95 = draw.length == 0 ? 0 : draw[Math.min(draw.length - 1, (int) (draw.length * 0.95))] / 1e6;
            double p99 = draw.length == 0 ? 0 : draw[Math.min(draw.length - 1, (int) (draw.length * 0.99))] / 1e6;
            text.append(String.format("%-24s %7d %8.3fms %8.3fms %8.3fms %8.3fms %8.1f %7.0f%%%n", entry.getKey(), stats.frame.size(),
                    drawAvg, p95, p99, frameAvg, frameAvg == 0 ? 0 : 1000 / frameAvg,
                    stats.draw.isEmpty() ? 0 : 100.0 * stats.hovered / stats.draw.size()));
        }
        text.append(blocks);
        try {
            Files.writeString(out.resolve("frametime.txt"), text.toString());
        } catch (IOException ex) {
            org.slf4j.LoggerFactory.getLogger("cobbletowers-screens").error("Could not write the frame-time report", ex);
        }
    }
}
