package com.cobbletowers.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The geometry every other part of the allocator trusts. */
class CellGridTest {

    @Test
    @DisplayName("no two cells share a chunk")
    void cellsNeverShareAChunk() {
        // The isolation the whole design rests on: if two cells shared a chunk, one run's activity
        // would load and tick another's, and a cleanup sweep would reach into a neighbour.
        Set<Long> seen = new HashSet<>();
        for (int index = 0; index < CellGrid.MAX_CELLS; index++) {
            ChunkPos min = CellGrid.minChunk(index);
            ChunkPos max = CellGrid.maxChunk(index);
            for (int x = min.x; x <= max.x; x++) {
                for (int z = min.z; z <= max.z; z++) {
                    assertTrue(seen.add(ChunkPos.asLong(x, z)),
                            "chunk " + x + "," + z + " is claimed by more than one cell (cell " + index + ")");
                }
            }
        }
    }

    @Test
    @DisplayName("no two cells share a region file either")
    void cellsNeverShareARegion() {
        Set<Long> regions = new HashSet<>();
        for (int index = 0; index < CellGrid.MAX_CELLS; index++) {
            ChunkPos min = CellGrid.minChunk(index);
            ChunkPos max = CellGrid.maxChunk(index);
            // A region is 32x32 chunks. A cell that straddled two would make its storage
            // inseparable from a neighbour's, which is the point of the 512-block span.
            assertEquals(min.getRegionX(), max.getRegionX(), "cell " + index + " straddles two regions in x");
            assertEquals(min.getRegionZ(), max.getRegionZ(), "cell " + index + " straddles two regions in z");
            assertTrue(regions.add((long) min.getRegionX() << 32 | (min.getRegionZ() & 0xFFFFFFFFL)),
                    "cell " + index + " shares a region with another cell");
        }
    }

    @Test
    @DisplayName("a position inside a cell maps back to that cell")
    void inverseRoundTrips() {
        for (int index = 0; index < CellGrid.MAX_CELLS; index += 7) {
            BlockPos center = CellGrid.centerOf(index);
            assertEquals(OptionalInt.of(index), CellGrid.indexAt(center.getX(), center.getZ()));

            BlockPos origin = CellGrid.originOf(index);
            assertEquals(OptionalInt.of(index), CellGrid.indexAt(origin.getX(), origin.getZ()),
                    "the north-west corner is inside its own cell");
            assertEquals(OptionalInt.of(index),
                    CellGrid.indexAt(origin.getX() + CellGrid.INTERIOR - 1, origin.getZ() + CellGrid.INTERIOR - 1),
                    "and so is the far corner");
        }
    }

    @Test
    @DisplayName("the gap between cells belongs to nobody")
    void bufferBelongsToNoCell() {
        BlockPos origin = CellGrid.originOf(0);
        // One block short of the interior on each side, and one block past it.
        assertEquals(OptionalInt.empty(), CellGrid.indexAt(origin.getX() - 1, origin.getZ()));
        assertEquals(OptionalInt.empty(), CellGrid.indexAt(origin.getX(), origin.getZ() - 1));
        assertEquals(OptionalInt.empty(),
                CellGrid.indexAt(origin.getX() + CellGrid.INTERIOR, origin.getZ()));
        assertEquals(OptionalInt.empty(),
                CellGrid.indexAt(origin.getX(), origin.getZ() + CellGrid.INTERIOR));
    }

    @Test
    @DisplayName("anywhere outside the grid belongs to nobody, negative coordinates included")
    void outsideTheGrid() {
        assertEquals(OptionalInt.empty(), CellGrid.indexAt(-1, -1));
        assertEquals(OptionalInt.empty(), CellGrid.indexAt(-1000, 500));
        assertEquals(OptionalInt.empty(),
                CellGrid.indexAt((double) CellGrid.CELLS_PER_ROW * CellGrid.CELL_SPAN + 200, 200));
    }

    @Test
    @DisplayName("bounds cover the interior and the full height, and nothing more")
    void bounds() {
        AABB bounds = CellGrid.boundsOf(5);
        BlockPos origin = CellGrid.originOf(5);

        assertEquals(origin.getX(), bounds.minX);
        assertEquals(origin.getX() + CellGrid.INTERIOR, bounds.maxX);
        assertEquals(CellGrid.MIN_Y, bounds.minY);
        assertEquals(CellGrid.MAX_Y, bounds.maxY);
        assertEquals(CellGrid.INTERIOR * CellGrid.INTERIOR * (double) (CellGrid.MAX_Y - CellGrid.MIN_Y),
                bounds.getXsize() * bounds.getZsize() * bounds.getYsize());
    }

    @Test
    @DisplayName("the sweep volume reaches below the cell, where anything left behind falls to")
    void sweepReachesBelowTheFloor() {
        // A void dimension has no floor, so a leftover entity is under the cell within a second or
        // two. Found live: a pig summoned in a cell was at y=-28 when the cell verified "clean".
        AABB interior = CellGrid.boundsOf(1);
        AABB sweep = CellGrid.sweepBoundsOf(1);
        BlockPos centre = CellGrid.centerOf(1);

        assertFalse(interior.contains(centre.getX(), -28, centre.getZ()),
                "the interior is where gameplay happens, and it stops at the floor");
        assertTrue(sweep.contains(centre.getX(), -28, centre.getZ()),
                "the sweep has to see what fell out of the bottom");
        assertTrue(sweep.contains(centre.getX(), CellGrid.MIN_Y - CellGrid.SWEEP_BELOW + 1, centre.getZ()));

        // Same footprint, so a sweep still cannot reach into a neighbouring cell.
        assertEquals(interior.minX, sweep.minX);
        assertEquals(interior.maxX, sweep.maxX);
        assertEquals(interior.minZ, sweep.minZ);
        assertEquals(interior.maxZ, sweep.maxZ);
        assertEquals(interior.maxY, sweep.maxY);
    }

    @Test
    @DisplayName("an index off the end is refused rather than wrapped")
    void invalidIndex() {
        assertThrows(IllegalArgumentException.class, () -> CellGrid.originOf(-1));
        assertThrows(IllegalArgumentException.class, () -> CellGrid.originOf(CellGrid.MAX_CELLS));
        assertFalse(CellGrid.indexAt(0, 0).isPresent(), "the world origin is in cell 0's buffer, not in cell 0");
    }
}
