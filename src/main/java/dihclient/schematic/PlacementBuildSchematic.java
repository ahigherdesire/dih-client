package dihclient.schematic;

import baritone.api.schematic.ISchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A placement as Baritone's builder sees it: an axis-aligned box starting at {@link #origin()}, already turned the
 * placement's way, so {@code #build}'s own builder can build it. Positions the placement wants nothing at are left
 * out, so the builder neither fills them nor clears them.
 */
public final class PlacementBuildSchematic implements ISchematic {

    private final SchematicPlacement placement;
    private final BlockPos origin;
    private final Vec3i size;

    public PlacementBuildSchematic(SchematicPlacement placement) {
        this.placement = placement;
        this.origin = placement.minCorner();
        this.size = placement.size();
    }

    /**
     * @return the world position of the box's (0, 0, 0), to hand to the builder with this schematic
     */
    public BlockPos origin() {
        return this.origin;
    }

    @Override
    public boolean inSchematic(int x, int y, int z, BlockState currentState) {
        return ISchematic.super.inSchematic(x, y, z, currentState)
            && this.placement.expected(this.origin.getX() + x, this.origin.getY() + y, this.origin.getZ() + z) != null;
    }

    @Override
    public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
        BlockState state = this.placement.expected(this.origin.getX() + x, this.origin.getY() + y, this.origin.getZ() + z);
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public int widthX() {
        return this.size.getX();
    }

    @Override
    public int heightY() {
        return this.size.getY();
    }

    @Override
    public int lengthZ() {
        return this.size.getZ();
    }
}
