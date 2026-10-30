package com.cobbletowers.vendor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The goblin merchant's stall: a counter in front of her, shelves and cauldrons behind, a striped awning over all of it. Built from the vendor's
 * feet in her own frame (right, up, forward = toward the party) so it fits any facing, only into empty cells, and taken down with her.
 * The counter is a slab, so a click still reaches her over it. Server thread only.
 */
public final class MerchantBooth {

    private record Part(int right, int up, int forward, BlockState state) {}

    private static final Map<UUID, List<BlockPos>> RAISED = new HashMap<>();

    private MerchantBooth() {}

    /** The stall's blocks for a goblin facing {@code front}, as offsets in her frame. */
    private static List<Part> plan(Direction front) {
        List<Part> out = new ArrayList<>();
        BlockState barrel = Blocks.BARREL.defaultBlockState();
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
        BlockState slab = Blocks.SPRUCE_SLAB.defaultBlockState();
        // back wall (forward -2): crates, a row of bookshelves, a shelf board
        for (int r = -2; r <= 2; r++) {
            out.add(new Part(r, 0, -2, r % 2 == 0 ? barrel : planks));
            out.add(new Part(r, 1, -2, Blocks.BOOKSHELF.defaultBlockState()));
            out.add(new Part(r, 2, -2, slab));
        }
        // a cold cauldron and a bubbling one at her sides, webs above them
        out.add(new Part(-2, 0, -1, Blocks.CAULDRON.defaultBlockState()));
        out.add(new Part(2, 0, -1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)));
        out.add(new Part(-2, 2, -1, Blocks.COBWEB.defaultBlockState()));
        out.add(new Part(2, 2, -1, Blocks.COBWEB.defaultBlockState()));
        // the counter (forward +1: the party keeps the rest of the platform in front of it): slabs between two posts, a jack-o'-lantern on one and candles on the other
        for (int r = -1; r <= 1; r++) out.add(new Part(r, 0, 1, slab));
        out.add(new Part(-2, 0, 1, barrel));
        out.add(new Part(-2, 1, 1, Blocks.JACK_O_LANTERN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, front)));
        out.add(new Part(-2, 2, 1, fence));
        out.add(new Part(2, 0, 1, barrel));
        out.add(new Part(2, 1, 1, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true)));
        out.add(new Part(2, 2, 1, fence));
        // the awning: orange and black stripes over the stall, a purple valance on the front edge, two soul lanterns hanging from it
        for (int f = -2; f <= 1; f++) {
            for (int r = -2; r <= 2; r++) out.add(new Part(r, 3, f, (r % 2 == 0 ? Blocks.ORANGE_WOOL : Blocks.BLACK_WOOL).defaultBlockState()));
        }
        for (int r = -2; r <= 2; r++) out.add(new Part(r, 3, 2, Blocks.PURPLE_WOOL.defaultBlockState()));
        BlockState lantern = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        out.add(new Part(-1, 2, 1, lantern));
        out.add(new Part(1, 2, 1, lantern));
        return out;
    }

    /** Raises the stall around {@code feet} (the vendor's block) for a goblin facing {@code front}; blocks already there are left alone. */
    public static void raise(ServerLevel level, UUID runId, BlockPos feet, Direction front) {
        Direction right = front.getClockWise();
        List<BlockPos> placed = new ArrayList<>();
        for (Part part : plan(front)) {
            BlockPos pos = feet.relative(right, part.right).relative(Direction.UP, part.up).relative(front, part.forward);
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) continue;
            level.setBlock(pos, part.state, Block.UPDATE_ALL);
            placed.add(pos);
        }
        RAISED.put(runId, placed);
    }

    /** Takes down what {@link #raise} put up for the run (the cell is cleared at the end of a run anyway). Safe more than once. */
    public static void takeDown(ServerLevel level, UUID runId) {
        List<BlockPos> placed = RAISED.remove(runId);
        if (placed == null) return;
        for (BlockPos pos : placed) {
            if (level.isLoaded(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The booth blocks a run has standing, for tests. */
    public static int standing(UUID runId) {
        return RAISED.getOrDefault(runId, List.of()).size();
    }
}
