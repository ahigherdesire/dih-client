package dihclient.util;

import dihclient.util.DihSpawnLight.Darkness;
import dihclient.util.DihSpawnLight.Spot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Sweeps a box around the player for spots where hostile mobs can spawn because the light is too
 * low, a fixed number of cells per tick. Each finished sweep replaces the published list in one
 * go, so markers never flicker while a sweep is half done. Reads only the client world, so it
 * works the same in multiplayer.
 */
public final class DihSpawnLightScanner {
    /**
     * Reference mob for the game's own spawn predicates: a two-block-tall ground monster, so a
     * floor's {@code isValidSpawn} and the feet/head checks match what zombies and skeletons need.
     */
    private static final EntityType<?> REFERENCE = EntityTypes.ZOMBIE;

    private int horizontal;
    private int vertical;
    private int cellCount;
    private int cursor;
    private BlockPos centre;
    private int blockLightLimit;
    private int maxBrightness;
    private boolean hasSky;
    private List<Spot> pending = new ArrayList<>();
    private long nextSweepTick;
    private WeakReference<ClientLevel> level = new WeakReference<>(null);

    private volatile List<Spot> published = List.of();

    /** The latest complete sweep, nearest first. */
    public List<Spot> spots() {
        return published;
    }

    public void clear() {
        published = List.of();
        pending = new ArrayList<>();
        centre = null;
        level.clear();
    }

    /**
     * Advances the sweep by up to {@code budget} cells. A new sweep starts once the last one
     * finished and {@code intervalTicks} have passed, or at once when the world or radii change.
     */
    public void tick(ClientLevel currentLevel, Vec3 player, long gameTick, int horizontal, int vertical,
                     int budget, int intervalTicks, int maxSpots) {
        if (currentLevel == null || player == null) {
            clear();
            return;
        }
        boolean worldChanged = currentLevel != level.get();
        if (worldChanged) {
            published = List.of();
            level = new WeakReference<>(currentLevel);
        }
        if (centre == null) {
            if (!worldChanged && horizontal == this.horizontal && vertical == this.vertical
                && gameTick < nextSweepTick) return;
            start(currentLevel, player, horizontal, vertical);
        } else if (worldChanged || horizontal != this.horizontal || vertical != this.vertical) {
            start(currentLevel, player, horizontal, vertical);
        }

        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        int end = Math.min(cellCount, cursor + Math.max(1, budget));
        for (; cursor < end; cursor++) {
            int[] o = DihSpawnLight.offset(cursor, this.horizontal, this.vertical);
            feet.set(centre.getX() + o[0], centre.getY() + o[1], centre.getZ() + o[2]);
            Darkness darkness = darknessAt(currentLevel, feet);
            if (darkness != Darkness.NONE) pending.add(new Spot(feet.getX(), feet.getY(), feet.getZ(), darkness));
        }
        if (cursor >= cellCount) {
            published = DihSpawnLight.nearest(pending, maxSpots, player.x, player.y, player.z);
            pending = new ArrayList<>();
            centre = null;
            nextSweepTick = gameTick + Math.max(0, intervalTicks);
        }
    }

    private void start(ClientLevel currentLevel, Vec3 player, int horizontal, int vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
        this.cellCount = DihSpawnLight.cellCount(horizontal, vertical);
        this.cursor = 0;
        this.centre = BlockPos.containing(player);
        this.pending = new ArrayList<>();
        DimensionType dimension = currentLevel.dimensionType();
        this.blockLightLimit = dimension.monsterSpawnBlockLightLimit();
        this.maxBrightness = dimension.monsterSpawnLightTest().maxInclusive();
        this.hasSky = dimension.hasSkyLight();
    }

    private Darkness darknessAt(ClientLevel currentLevel, BlockPos.MutableBlockPos feet) {
        if (currentLevel.isOutsideBuildHeight(feet.getY() - 1) || currentLevel.isOutsideBuildHeight(feet.getY() + 1)) {
            return Darkness.NONE;
        }
        if (!currentLevel.isLoaded(feet)) return Darkness.NONE;
        // Cheapest reject first: most cells are inside solid ground or high in the air.
        BlockState feetState = currentLevel.getBlockState(feet);
        if (!feetState.getCollisionShape(currentLevel, feet).isEmpty()) return Darkness.NONE;
        BlockPos immutable = feet.immutable();
        // The game's own rule: valid floor (isValidSpawn rejects glass, leaves, bottom slabs, ...),
        // and feet and head blocks a mob may stand in (no fluids, no full colliders, no rails or
        // other PREVENT_MOB_SPAWNING_INSIDE blocks, nothing dangerous like fire).
        if (!SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(currentLevel, immutable, REFERENCE)) return Darkness.NONE;
        // Stricter than isValidEmptySpawnBlock: carpets, snow layers and the like still collide.
        BlockPos head = immutable.above();
        if (!currentLevel.getBlockState(head).getCollisionShape(currentLevel, head).isEmpty()) return Darkness.NONE;
        int blockLight = currentLevel.getBrightness(LightLayer.BLOCK, immutable);
        int skyLight = hasSky ? currentLevel.getBrightness(LightLayer.SKY, immutable) : 0;
        return DihSpawnLight.classify(blockLight, skyLight, blockLightLimit, maxBrightness);
    }
}
