package com.cobbletowers.webgui;

import com.cobbletowers.TowerLog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Puts the web UI the jar carries ({@code web/cobbletowers/}, built from {@code web/} in the repo) where WebGUI serves it
 * ({@code config/webgui/web/cobbletowers/}). Installing the jar is the whole install: the copy is refreshed whenever the
 * bundle's stamp ({@code version.txt}) differs from the one already on disk.
 */
final class WebUiFiles {

    private static final String BUNDLE = "web/cobbletowers";

    private WebUiFiles() {}

    static void install() {
        try {
            Path bundle = FabricLoader.getInstance().getModContainer("cobbletowers")
                    .flatMap(c -> c.findPath(BUNDLE)).orElse(null);
            if (bundle == null || !Files.isDirectory(bundle)) {
                TowerLog.warn("This jar carries no web UI ({}); pages are served from whatever is in config/webgui/web/cobbletowers", BUNDLE);
                return;
            }
            Path target = FabricLoader.getInstance().getConfigDir().resolve("webgui/web/cobbletowers");
            String have = stamp(bundle);
            if (have.equals(stamp(target))) return;
            if (Files.isDirectory(target)) deleteTree(target);
            copyTree(bundle, target);
            TowerLog.info("Installed the web UI {} into {}", have, target);
        } catch (IOException | UncheckedIOException ex) {
            TowerLog.error("Could not install the web UI files ({}); pages may be missing or stale", ex.toString());
        }
    }

    private static String stamp(Path dir) {
        try {
            Path file = dir.resolve("version.txt");
            return Files.isRegularFile(file) ? Files.readString(file).trim() : "";
        } catch (IOException ex) {
            return "";
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path source : (Iterable<Path>) walk::iterator) {
                if (source.getFileName() != null && source.getFileName().toString().equals("version.txt")) continue;
                Path dest = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) Files.createDirectories(dest);
                else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        // The stamp goes last, so an interrupted copy is redone at the next start.
        Files.copy(from.resolve("version.txt"), to.resolve("version.txt"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) Files.delete(p);
        }
    }
}
