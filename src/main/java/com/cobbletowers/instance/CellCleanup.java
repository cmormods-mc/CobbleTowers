package com.cobbletowers.instance;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Whether a cell is fit to hand to the next run (TDS #35). Staged, with every failing stage reported: no players, no
 * entities, and no chunk ticket still held. The sweep reaches well below the cell because the tower is a void. Run
 * {@link #verifyReleased} while tickets are still in place.
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
     * The full check before a cell can be reused. Call after dropping the cell's tickets, with contents swept before
     * that: the contents stage needs loaded chunks, the ownership stage needs the tickets gone. Asks whether anything
     * in this mod still claims the cell.
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

    /**
     * Removes whatever is loose in a cell and returns how many things; the same class of thing {@link #verify} calls
     * a problem.
     */
    public static int sweepEntities(MinecraftServer server, int cell) {
        return sweepEntities(server, cell, entity -> false);
    }

    /**
     * The same, sparing entities {@code spare} accepts. The post-crash sweep can run after the next floor has
     * started, so live battle entities must survive.
     */
    public static int sweepEntities(MinecraftServer server, int cell, java.util.function.Predicate<Entity> spare) {
        CellGrid.requireValid(cell);
        ServerLevel level = TowerDimension.level(server);
        if (level == null) return 0;

        List<Entity> loose = level.getEntities((Entity) null, CellGrid.sweepBoundsOf(cell),
                entity -> !(entity instanceof Player) && !spare.test(entity));
        for (Entity entity : loose) entity.discard();
        return loose.size();
    }

    /**
     * Removes the debris a fought-over cell expects: dropped items, experience orbs and falling blocks (the
     * building's own sand and concrete physics). Narrower than {@link #sweepEntities}; anything else found is still a
     * quarantine.
     */
    public static int sweepDebris(MinecraftServer server, int cell) {
        CellGrid.requireValid(cell);
        ServerLevel level = TowerDimension.level(server);
        if (level == null) return 0;

        List<Entity> debris = level.getEntities((Entity) null, CellGrid.sweepBoundsOf(cell),
                entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof FallingBlockEntity);
        for (Entity entity : debris) entity.discard();
        return debris.size();
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
