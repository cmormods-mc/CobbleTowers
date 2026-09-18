package com.cobbletowers.instance;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.FloorLayout;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Turns an empty cell into a floor somebody can play, and back again.
 *
 * <p>The template is vanilla structure NBT, loaded by Minecraft's own
 * {@code StructureTemplateManager}. The arenas were authored as WorldEdit schematics and converted
 * once by {@code validation/schem_to_structure.py}; nothing parses WorldEdit's format at runtime,
 * because the alternative is a hand-written binary parser in the path that builds a floor while
 * players wait.
 *
 * <p>Order matters: the chunks are held <b>before</b> the paste, or the placement writes into chunks
 * that are not loaded.
 */
public final class CellPreparer {

    /** Placement flags: tell clients, and skip the neighbour-shape updates a solid build does not need. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /**
     * The box {@link #reset} sweeps, around the cell centre at floor height.
     *
     * <p>Wide and tall enough for any arena this phase ships (the largest is 51x10x51) with margin.
     * A cell is not told what was pasted into it -- after a restart nothing remembers -- so the
     * reset sweeps a fixed volume and clears whatever it finds. A tower-sized structure, like the
     * 127-block Tideforge interior, would need this raised with it.
     */
    private static final int RESET_RADIUS = 32;
    private static final int RESET_BELOW = 1;
    private static final int RESET_ABOVE = 16;

    private CellPreparer() {}

    /**
     * What preparing a cell produced.
     *
     * @param millis   how long the paste took, measured rather than assumed
     * @param problems anchors that are not fit to use; empty means the floor is playable
     */
    public record Prepared(int cell, BlockPos origin, Vec3i size, long millis, List<String> problems) {

        public Prepared {
            problems = List.copyOf(problems);
        }

        public boolean isPlayable() {
            return problems.isEmpty();
        }

        public String summary() {
            return problems.isEmpty() ? "playable" : String.join("; ", problems);
        }
    }

    /**
     * Where this floor's structure sits in this cell.
     *
     * <p>The origin depends on the structure's size, because a floor is centred in its cell. Anything
     * that needs to turn an anchor into a world position has to ask <b>this</b> -- working it out
     * separately is how the entry anchor ended up twenty-five blocks from the arena it belonged to,
     * and the party arrived in the void beside their own floor.
     */
    public static Optional<BlockPos> originFor(MinecraftServer server, int cell, FloorLayout layout) {
        return server.getStructureManager().get(layout.structure())
                .map(template -> CellTickets.pasteOrigin(cell, template.getSize().getX(), template.getSize().getZ()));
    }

    /**
     * Pastes a floor's structure into a cell and checks the result is playable.
     *
     * <p>Returns problems rather than throwing: a floor that cannot be built is a cell to quarantine
     * and a run to park, not a server to take down.
     */
    public static Optional<Prepared> prepare(MinecraftServer server, int cell, FloorLayout layout) {
        CellGrid.requireValid(cell);
        ServerLevel level = TowerDimension.level(server);
        if (level == null) return Optional.empty();

        Optional<StructureTemplate> found = server.getStructureManager().get(layout.structure());
        if (found.isEmpty()) {
            TowerLog.error("Floor structure {} is not loaded; cell {} cannot be prepared", layout.structure(), cell);
            return Optional.empty();
        }
        StructureTemplate template = found.get();
        Vec3i size = template.getSize();
        BlockPos origin = CellTickets.pasteOrigin(cell, size.getX(), size.getZ());
        // originFor() must agree with this; it is the same call, kept together on purpose.

        CellTickets.hold(server, cell);

        long started = System.nanoTime();
        boolean placed = template.placeInWorld(level, origin, origin,
                new StructurePlaceSettings().setIgnoreEntities(true), level.getRandom(), PLACE_FLAGS);
        long millis = (System.nanoTime() - started) / 1_000_000;
        if (!placed) {
            TowerLog.error("Placing {} into cell {} failed", layout.structure(), cell);
            return Optional.empty();
        }

        List<String> problems = CellAnchors.validate(level, cell, origin, layout);
        Prepared prepared = new Prepared(cell, origin, size, millis, problems);
        if (prepared.isPlayable()) {
            TowerLog.info("Cell {} prepared with {} at {} in {} ms", cell, layout.structure(), origin, millis);
        } else {
            TowerLog.error("Cell {} was built but is not playable: {}", cell, prepared.summary());
        }
        return Optional.of(prepared);
    }

    /**
     * Clears whatever is in the cell. Returns how many blocks were removed.
     *
     * <p>Reads the whole sweep volume but only writes where something is there, which for an arena
     * is a few thousand of the seventy-odd thousand positions looked at. Reads are far cheaper than
     * writes, and this way the reset does not need to know what was pasted -- which matters, because
     * after a restart nothing does.
     */
    public static int reset(MinecraftServer server, int cell) {
        CellGrid.requireValid(cell);
        ServerLevel level = TowerDimension.level(server);
        if (level == null) return 0;

        BlockPos centre = CellGrid.centerOf(cell);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int cleared = 0;
        for (int y = CellGrid.FLOOR_Y - RESET_BELOW; y <= CellGrid.FLOOR_Y + RESET_ABOVE; y++) {
            for (int x = centre.getX() - RESET_RADIUS; x < centre.getX() + RESET_RADIUS; x++) {
                for (int z = centre.getZ() - RESET_RADIUS; z < centre.getZ() + RESET_RADIUS; z++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).isAir()) continue;
                    level.setBlock(cursor, air, PLACE_FLAGS);
                    cleared++;
                }
            }
        }
        if (cleared > 0) TowerLog.info("Cell {} reset, {} block(s) cleared", cell, cleared);
        return cleared;
    }
}
