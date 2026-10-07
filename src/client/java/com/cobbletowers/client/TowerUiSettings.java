package com.cobbletowers.client;

import java.nio.file.Files;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Client presentation settings. The old industrial blur/opacity keys are ignored; the old "shaders" key still reads as glow. */
public final class TowerUiSettings {
    /** The restrained torch glow behind headers. Off leaves the same materials and every control. */
    public static boolean glow = true;
    public static boolean motion = true;
    public static boolean sounds = true;
    /** The bundled pixel font (Pixelify Sans, OFL) in every CobbleTowers menu; off uses Minecraft's own font. */
    public static boolean pixelFont = true;
    private TowerUiSettings() {}
    public static void load() {
        var file = FabricLoader.getInstance().getConfigDir().resolve("cobbletowers-ui.properties");
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file)) {
            var p = new Properties(); p.load(reader);
            glow = Boolean.parseBoolean(p.getProperty("glow", p.getProperty("shaders", "true")));
            motion = Boolean.parseBoolean(p.getProperty("motion", "true"));
            sounds = Boolean.parseBoolean(p.getProperty("sounds", "true"));
            pixelFont = Boolean.parseBoolean(p.getProperty("pixelFont", "true"));
        } catch (java.io.IOException e) {
            org.slf4j.LoggerFactory.getLogger("cobbletowers-ui").warn("Cannot read UI settings", e);
        }
    }
    public static void save() {
        var file = FabricLoader.getInstance().getConfigDir().resolve("cobbletowers-ui.properties");
        try {
            Files.createDirectories(file.getParent());
            try (var writer = Files.newBufferedWriter(file)) {
                var p = new Properties();
                p.setProperty("glow", Boolean.toString(glow));
                p.setProperty("motion", Boolean.toString(motion));
                p.setProperty("sounds", Boolean.toString(sounds));
                p.setProperty("pixelFont", Boolean.toString(pixelFont));
                p.store(writer, "CobbleTowers client presentation only");
            }
        } catch (java.io.IOException e) {
            org.slf4j.LoggerFactory.getLogger("cobbletowers-ui").warn("Cannot save UI settings", e);
        }
    }
}
