package dihclient.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * How a placed schematic is turned: mirrored first, then rotated clockwise (seen from above) in 90 degree steps,
 * both about the placement's origin, which stays where the schematic's (0, 0, 0) block is.
 * <p>
 * Ported from Autism's placement math ({@code AiPlacementControls} and the blueprint transform behind
 * {@code AiSchematicPlacement}): the same order, the same axis for each mirror and the same rotation steps.
 */
public record SchematicTransform(int rotation, Mirroring mirror) {

    public static final SchematicTransform IDENTITY = new SchematicTransform(0, Mirroring.NONE);

    /**
     * Mirroring across the origin. {@code X} flips east and west, {@code Z} flips north and south.
     */
    public enum Mirroring {
        NONE("none", Mirror.NONE),
        X("x", Mirror.FRONT_BACK),
        Z("z", Mirror.LEFT_RIGHT);

        private final String id;
        private final Mirror vanilla;

        Mirroring(String id, Mirror vanilla) {
            this.id = id;
            this.vanilla = vanilla;
        }

        public String id() {
            return this.id;
        }

        public Mirror vanilla() {
            return this.vanilla;
        }

        /**
         * @return the next one in the order none, x, z
         */
        public Mirroring next() {
            return switch (this) {
                case NONE -> X;
                case X -> Z;
                case Z -> NONE;
            };
        }

        /**
         * @return the mirroring named {@code text} (none, x or z; also "left_right"/"front_back"), or NONE
         */
        public static Mirroring parse(String text) {
            if (text == null) {
                return NONE;
            }
            return switch (text.trim().toLowerCase(Locale.ROOT)) {
                case "x", "front_back" -> X;
                case "z", "left_right" -> Z;
                default -> NONE;
            };
        }
    }

    /**
     * @param rotation degrees clockwise; rounded to a multiple of 90 and kept in 0 to 270
     */
    public SchematicTransform {
        rotation = Math.floorMod(Math.round(rotation / 90.0f) * 90, 360);
        if (mirror == null) {
            mirror = Mirroring.NONE;
        }
    }

    /**
     * @param steps quarter turns, clockwise when positive
     */
    public SchematicTransform rotated(int steps) {
        return new SchematicTransform(Math.floorMod(this.rotation + Math.floorMod(steps, 4) * 90, 360), this.mirror);
    }

    public SchematicTransform withMirror(Mirroring mirror) {
        return new SchematicTransform(this.rotation, mirror);
    }

    public int turns() {
        return this.rotation / 90;
    }

    public Rotation vanillaRotation() {
        return switch (turns()) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /**
     * @return where the schematic's block at {@code (x, y, z)} lands, relative to the origin
     */
    public BlockPos offset(int x, int y, int z) {
        int dx = x;
        int dz = z;
        if (this.mirror == Mirroring.X) {
            dx = -dx;
        } else if (this.mirror == Mirroring.Z) {
            dz = -dz;
        }
        for (int i = 0; i < turns(); i++) {
            int swap = dx;
            dx = -dz;
            dz = swap;
        }
        return new BlockPos(dx, y, dz);
    }

    /**
     * The inverse of {@link #offset}.
     *
     * @return the schematic position whose block lands at {@code (dx, dy, dz)} from the origin
     */
    public BlockPos local(int dx, int dy, int dz) {
        int x = dx;
        int z = dz;
        for (int i = 0; i < turns(); i++) {
            int swap = x;
            x = z;
            z = -swap;
        }
        if (this.mirror == Mirroring.X) {
            x = -x;
        } else if (this.mirror == Mirroring.Z) {
            z = -z;
        }
        return new BlockPos(x, dy, z);
    }

    public BlockState apply(BlockState state) {
        return state.mirror(this.mirror.vanilla()).rotate(vanillaRotation());
    }

    /**
     * @return the lowest corner, relative to the origin, of a schematic of {@code size} once transformed
     */
    public BlockPos minCorner(Vec3i size) {
        BlockPos a = offset(0, 0, 0);
        BlockPos b = offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    /**
     * @return the size of a schematic of {@code size} once transformed; x and z swap on quarter turns
     */
    public Vec3i size(Vec3i size) {
        return turns() % 2 == 0 ? size : new Vec3i(size.getZ(), size.getY(), size.getX());
    }
}
