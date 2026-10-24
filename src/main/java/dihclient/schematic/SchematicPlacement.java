package dihclient.schematic;

import baritone.utils.schematic.LoadedSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A schematic file put somewhere in a world: which file, where its origin is, how it is turned, and whether it is
 * shown. The file's blocks are loaded separately and attached with {@link #attach}.
 */
public final class SchematicPlacement {

    private final String id;
    private final String file;
    private String name;
    private String dimension;
    private BlockPos origin;
    private SchematicTransform transform;
    private boolean visible;

    private LoadedSchematic schematic;
    private String error = "";
    private int revision;
    private final Map<BlockState, BlockState> transformed = new IdentityHashMap<>();

    public SchematicPlacement(String id, String file, String name, String dimension, BlockPos origin,
                              SchematicTransform transform, boolean visible) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.file = file;
        this.name = name == null || name.isBlank() ? file : name;
        this.dimension = dimension == null ? "" : dimension;
        this.origin = origin.immutable();
        this.transform = transform == null ? SchematicTransform.IDENTITY : transform;
        this.visible = visible;
    }

    public static SchematicPlacement create(String file, String dimension, BlockPos origin) {
        return new SchematicPlacement(null, file, null, dimension, origin, SchematicTransform.IDENTITY, true);
    }

    public String id() {
        return this.id;
    }

    /**
     * @return the file, relative to the schematics folder
     */
    public String file() {
        return this.file;
    }

    public String name() {
        return this.name;
    }

    public String dimension() {
        return this.dimension;
    }

    public BlockPos origin() {
        return this.origin;
    }

    public SchematicTransform transform() {
        return this.transform;
    }

    public boolean visible() {
        return this.visible;
    }

    /**
     * @return the loaded blocks, or null while loading or after a failed load
     */
    public LoadedSchematic schematic() {
        return this.schematic;
    }

    /**
     * @return why the file failed to load, or "" when it did not fail
     */
    public String error() {
        return this.error;
    }

    /**
     * @return a number that changes whenever what the placement puts where changes
     */
    public int revision() {
        return this.revision;
    }

    public void attach(LoadedSchematic schematic) {
        this.schematic = schematic;
        this.error = "";
        if (schematic != null && (this.name.equals(this.file) || this.name.isBlank()) && !schematic.name().isBlank()) {
            this.name = schematic.name();
        }
        changed();
    }

    public void fail(String error) {
        this.schematic = null;
        this.error = error == null ? "failed to load" : error;
        changed();
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
    }

    public void setDimension(String dimension) {
        this.dimension = dimension == null ? "" : dimension;
    }

    public void moveTo(BlockPos origin) {
        if (!origin.equals(this.origin)) {
            this.origin = origin.immutable();
            changed();
        }
    }

    public void nudge(int dx, int dy, int dz) {
        moveTo(this.origin.offset(dx, dy, dz));
    }

    public void setTransform(SchematicTransform transform) {
        if (!transform.equals(this.transform)) {
            this.transform = transform;
            this.transformed.clear();
            changed();
        }
    }

    public void rotate(int steps) {
        setTransform(this.transform.rotated(steps));
    }

    public void cycleMirror() {
        setTransform(this.transform.withMirror(this.transform.mirror().next()));
    }

    public void setVisible(boolean visible) {
        if (visible != this.visible) {
            this.visible = visible;
            changed();
        }
    }

    private void changed() {
        this.revision++;
    }

    public boolean isLoaded() {
        return this.schematic != null;
    }

    /**
     * @return the schematic's size once transformed, or zero while not loaded
     */
    public Vec3i size() {
        LoadedSchematic s = this.schematic;
        return s == null ? Vec3i.ZERO : this.transform.size(new Vec3i(s.widthX(), s.heightY(), s.lengthZ()));
    }

    /**
     * @return the lowest world corner the placement covers
     */
    public BlockPos minCorner() {
        LoadedSchematic s = this.schematic;
        if (s == null) {
            return this.origin;
        }
        return this.origin.offset(this.transform.minCorner(new Vec3i(s.widthX(), s.heightY(), s.lengthZ())));
    }

    /**
     * @return the world box the placement covers, or null while not loaded
     */
    public BoundingBox bounds() {
        Vec3i size = size();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            return null;
        }
        BlockPos min = minCorner();
        return new BoundingBox(min.getX(), min.getY(), min.getZ(),
            min.getX() + size.getX() - 1, min.getY() + size.getY() - 1, min.getZ() + size.getZ() - 1);
    }

    /**
     * @return the block the placement wants at a world position, turned the placement's way, or null where the
     * placement wants nothing (outside it, between regions, structure voids, or not loaded)
     */
    public BlockState expected(int x, int y, int z) {
        LoadedSchematic s = this.schematic;
        if (s == null) {
            return null;
        }
        BlockPos local = this.transform.local(x - this.origin.getX(), y - this.origin.getY(), z - this.origin.getZ());
        if (!s.contains(local.getX(), local.getY(), local.getZ())) {
            return null;
        }
        BlockState state = s.getDirect(local.getX(), local.getY(), local.getZ());
        if (this.transform.equals(SchematicTransform.IDENTITY)) {
            return state;
        }
        return this.transformed.computeIfAbsent(state, this.transform::apply);
    }

    public BlockState expected(BlockPos pos) {
        return expected(pos.getX(), pos.getY(), pos.getZ());
    }
}
