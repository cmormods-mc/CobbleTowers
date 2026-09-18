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
 * <p>Three stages: no players, no entities, and -- once the cell has been let go of -- no chunk
 * ticket still held for it. The third was left out in P3 rather than stubbed, because a stage that
 * always passes reads as verified and verifies nothing; P4 gives it something real to check.
 *
 * <p>The sweep reaches well below the cell, because the tower is a void: anything left behind is
 * falling by the time anyone looks. A sweep bounded by the cell's own floor reported clean with a pig
 * plainly inside it, which is how that was found.
 *
 * <p>P3's known limit is closed for the normal path: a cell in use holds its chunks, so the sweep
 * runs against loaded chunks and sees what is really there. It still holds for a cell nobody has
 * held recently -- an unloaded chunk reports empty -- which is why {@link #verifyReleased} is run
 * while the tickets are still in place and the ticket stage is checked after they are dropped.
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

    /**
     * The full check a cell must pass before it can be handed to anyone else.
     *
     * <p>Call this <b>after</b> dropping the cell's tickets, and sweep the contents before that: the
     * contents stage needs loaded chunks to see anything, and the ownership stage needs the tickets
     * to be gone. Doing both at one moment would make one of them a lie.
     *
     * <p>What it really asks is whether anything in this mod still claims the cell -- a ticket that
     * was not dropped, or a warm-pool entry that outlived the cell it names. Written first as "are
     * the tickets gone" alone, it was almost a tautology, because release drops them on the line
     * above; asking who still claims the cell is a question that a bug can actually answer wrongly,
     * which is the only kind of check worth running.
     */
    public static Report verifyReleased(int cell, Report contents) {
        List<String> problems = new ArrayList<>(contents.problems());
        if (CellTickets.isHeld(cell)) {
            problems.add("chunk tickets are still held for this cell");
        }
        if (CellWarmPool.isWarm(cell)) {
            problems.add("the warm pool still lists this cell as ready to hand out");
        }
        return new Report(cell, problems);
    }

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
