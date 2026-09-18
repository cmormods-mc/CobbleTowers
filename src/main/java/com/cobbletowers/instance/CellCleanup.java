package com.cobbletowers.instance;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Whether a cell is fit to hand to the next run (TDS #35).
 *
 * <p>Staged, and every stage that fails is reported rather than the first one: an operator reading a
 * quarantine wants to know everything that was left behind, not the first thing noticed.
 *
 * <p>Two stages exist today -- no players, no entities. Chunk tickets are a third that <b>P4 adds</b>
 * when tickets exist. A stage that always passed would read as verified and verify nothing, so it is
 * absent rather than stubbed.
 *
 * <p>The sweep reaches well below the cell, because the tower is a void: anything left behind is
 * falling by the time anyone looks. A sweep bounded by the cell's own floor reported clean with a pig
 * plainly inside it, which is how that was found.
 *
 * <p><b>Known limit.</b> An entity query only sees loaded chunks, so a cell whose chunks are unloaded
 * reports clean whatever is actually in it. That is honest rather than hidden: P4's lifecycle-owned
 * tickets are what make this sound, because then the cell's chunks are loaded exactly when the run
 * that owns them is live. Until then this catches the case that actually matters -- releasing a cell
 * somebody is still standing in.
 */
public final class CellCleanup {

    /** What a sweep found. Clean means every stage passed. */
    public record Report(int cell, List<String> problems) {

        public Report {
            problems = List.copyOf(problems);
        }

        public boolean isClean() {
            return problems.isEmpty();
        }

        /** One line, for a quarantine reason or a log. */
        public String summary() {
            return problems.isEmpty() ? "clean" : String.join("; ", problems);
        }
    }

    private CellCleanup() {}

    public static Report verify(MinecraftServer server, int cell) {
        CellGrid.requireValid(cell);
        List<String> problems = new ArrayList<>();

        ServerLevel level = TowerDimension.level(server);
        if (level == null) {
            // Cannot be verified, so it cannot be declared clean. Refusing to reuse a cell we
            // cannot inspect is the whole point of quarantine.
            return new Report(cell, List.of("the tower dimension is not loaded, so the cell cannot be inspected"));
        }

        // The sweep volume, not the interior: leftovers fall out of the bottom of a void cell.
        AABB bounds = CellGrid.sweepBoundsOf(cell);
        List<ServerPlayer> players = level.getPlayers(player -> bounds.contains(player.position()));
        if (!players.isEmpty()) {
            problems.add(players.size() + " player(s) still inside: "
                    + players.stream().map(player -> player.getGameProfile().getName()).toList());
        }

        List<Entity> entities = level.getEntities((Entity) null, bounds, entity -> !(entity instanceof Player));
        if (!entities.isEmpty()) {
            problems.add(entities.size() + " entity/entities still inside: "
                    + entities.stream().limit(5).map(entity -> entity.getType().toString()).toList());
        }
        return new Report(cell, problems);
    }
}
