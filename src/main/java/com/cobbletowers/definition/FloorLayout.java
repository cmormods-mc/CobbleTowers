package com.cobbletowers.definition;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * What a floor is built from, and where the places that matter are inside it (TDS section 12).
 *
 * <p>The schematics the arenas came from carry no marker blocks -- their palettes are plain building
 * blocks -- so the anchors are declared here rather than discovered. Declared is not trusted: each one
 * is checked against the structure's real size offline, and against the pasted result at runtime, so
 * an anchor inside a wall or over a hole is a loud failure rather than a player stuck in stone.
 *
 * @param structure     the template to paste, from data/&lt;namespace&gt;/structure/&lt;name&gt;.nbt
 * @param entry         where the party arrives
 * @param presentation  where the opposing Cobblemon is shown
 * @param spectator     where a knocked-out player watches from (TDS #25)
 * @param exit          where the way to the next floor is
 */
public record FloorLayout(
        ResourceLocation structure,
        FloorAnchor entry,
        FloorAnchor presentation,
        FloorAnchor spectator,
        FloorAnchor exit) {

    public FloorLayout {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(spectator, "spectator");
        Objects.requireNonNull(exit, "exit");
    }

    public static FloorLayout fromJson(JsonObject root) {
        return new FloorLayout(
                TowerJson.requireId(root, "structure"),
                FloorAnchor.fromJson(root, "entry"),
                FloorAnchor.fromJson(root, "presentation"),
                FloorAnchor.fromJson(root, "spectator"),
                FloorAnchor.fromJson(root, "exit"));
    }

    /** Every anchor with the name it is known by, for validation and for messages. */
    public Map<String, FloorAnchor> anchors() {
        return Map.of("entry", entry, "presentation", presentation, "spectator", spectator, "exit", exit);
    }

    /** The anchors in a fixed order, so a report reads the same way twice. */
    public List<String> anchorNames() {
        return List.of("entry", "presentation", "spectator", "exit");
    }
}
