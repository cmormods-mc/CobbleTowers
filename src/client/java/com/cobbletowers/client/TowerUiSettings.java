package com.cobbletowers.client;

import java.nio.file.Files;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class TowerUiSettings {
    public static boolean shaders = true;
    public static boolean motion = true;
    public static boolean sounds = true;
    public static float blur=1.0f, opacity=0.82f;
    private TowerUiSettings() {}
    public static void load() {
        var file = FabricLoader.getInstance().getConfigDir().resolve("cobbletowers-ui.properties");
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file)) {
            var p = new Properties(); p.load(reader);
            blur=parse(p.getProperty("blur"),1f,0f,3f);
            opacity=parse(p.getProperty("opacity"),.82f,.65f,.98f);
            shaders = Boolean.parseBoolean(p.getProperty("shaders", "true"));
            motion = Boolean.parseBoolean(p.getProperty("motion", "true"));
            sounds = Boolean.parseBoolean(p.getProperty("sounds", "true"));
        } catch (java.io.IOException e) {
            org.slf4j.LoggerFactory.getLogger("cobbletowers-ui").warn("Cannot read UI settings", e);
        }
    }
    private static float parse(String value,float fallback,float min,float max){try{float f=Float.parseFloat(value);return Float.isFinite(f)?Math.max(min,Math.min(max,f)):fallback;}catch(Exception e){return fallback;}}
    public static void save() {
        var file = FabricLoader.getInstance().getConfigDir().resolve("cobbletowers-ui.properties");
        try {
            Files.createDirectories(file.getParent());
            try (var writer = Files.newBufferedWriter(file)) {
                var p = new Properties();
                p.setProperty("blur",Float.toString(blur));
                p.setProperty("opacity",Float.toString(opacity));
                p.setProperty("shaders", Boolean.toString(shaders));
                p.setProperty("motion", Boolean.toString(motion));
                p.setProperty("sounds", Boolean.toString(sounds));
                p.store(writer, "CobbleTowers client presentation only");
            }
        } catch (java.io.IOException e) {
            org.slf4j.LoggerFactory.getLogger("cobbletowers-ui").warn("Cannot save UI settings", e);
        }
    }
}
