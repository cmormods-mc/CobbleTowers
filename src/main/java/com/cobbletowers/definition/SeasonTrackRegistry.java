package com.cobbletowers.definition;

import com.cobbletowers.CobbleTowers;
import com.cobbletowers.TowerLog;
import com.cobbletowers.track.TrackConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads the season track (P36b) from {@code data/<namespace>/cobbletowers/season_tracks/*.json} on every reload. A
 * malformed file is skipped with a message naming it. The first file with steps, in id order, is the base; files may
 * add {@code add_steps} and so can the owner's {@code config/cobbletowers-tracks.json}.
 */
public final class SeasonTrackRegistry
        extends SimplePreparableReloadListener<Map<ResourceLocation, SeasonTrackRegistry.File>>
        implements IdentifiableResourceReloadListener {

    public static final ResourceLocation ID = CobbleTowers.id("season_tracks");
    private static final String FOLDER = "cobbletowers/season_tracks";

    /** One parsed file: its steps (empty for an addon file that only adds) and the steps it adds to. */
    public record File(SeasonTrackDefinition track, List<SeasonTrackDefinition.AddStep> adds) {}

    private static volatile Map<ResourceLocation, File> LOADED = Map.of();

    /** The track every season uses, empty when none is loaded (then seasons run without a track). */
    public static Optional<SeasonTrackDefinition> current() {
        Map<ResourceLocation, File> files = LOADED;
        List<SeasonTrackDefinition.AddStep> owner = TrackConfig.current().seasonSteps();
        Merged hit = MERGED;
        // Rebuilt only when the files or the owner's steps are a different object (a reload or a config read).
        if (hit == null || hit.files != files || hit.owner != owner) {
            hit = new Merged(files, owner, merged(files, owner));
            MERGED = hit;
        }
        return hit.track;
    }

    private record Merged(Map<ResourceLocation, File> files, List<SeasonTrackDefinition.AddStep> owner,
                          Optional<SeasonTrackDefinition> track) {}

    private static volatile Merged MERGED;

    /** The base track with every file's additions, then the owner's, applied in order. Pure, for tests. */
    public static Optional<SeasonTrackDefinition> merged(Map<ResourceLocation, File> files, List<SeasonTrackDefinition.AddStep> owner) {
        Optional<SeasonTrackDefinition> base = files.values().stream().map(File::track).filter(track -> track.stepCount() > 0).findFirst();
        if (base.isEmpty()) return Optional.empty();
        List<SeasonTrackDefinition.AddStep> adds = new ArrayList<>();
        for (File file : files.values()) adds.addAll(file.adds());
        adds.addAll(owner);
        return Optional.of(base.get().withAdded(adds));
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    protected Map<ResourceLocation, File> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, File> loaded = new TreeMap<>();
        List<ResourceLocation> rejected = new ArrayList<>();
        manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            String relative = path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - ".json".length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(path.getNamespace(), relative);
            try (Reader reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                loaded.put(id, new File(SeasonTrackDefinition.fromJson(json), SeasonTrackDefinition.addSteps(json)));
            } catch (Exception ex) {
                rejected.add(id);
                TowerLog.error("Skipping malformed season track {}: {}", id, ex.toString());
            }
        });
        if (!rejected.isEmpty()) TowerLog.error("{} season track file(s) were skipped as malformed: {}", rejected.size(), rejected);
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, File> prepared, ResourceManager manager, ProfilerFiller profiler) {
        LOADED = new java.util.LinkedHashMap<>(prepared);
        // /reload re-reads the owner's config too, so an edit to it needs no restart.
        TrackConfig.reload();
        TowerLog.info("Loaded {} season track(s).", prepared.size());
    }
}
