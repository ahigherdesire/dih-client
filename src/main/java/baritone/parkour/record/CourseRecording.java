package baritone.parkour.record;

import baritone.utils.schematic.format.BlockStateReader;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parkour course copied from a world: its blocks, where a run starts (and which way it faces), and the route a
 * player took through it (the blocks stood on, in order). Saved as a vanilla structure file (so structure blocks and
 * schematic tools load it too) with the parkour part in a {@code dih_parkour} tag.
 *
 * <p>Positions are relative to the recording's lowest corner once saved or loaded.
 */
public final class CourseRecording {
    public final Map<BlockPos, BlockState> blocks;
    public final BlockPos start;
    public final float startYaw;
    public final List<BlockPos> route;

    public CourseRecording(Map<BlockPos, BlockState> blocks, BlockPos start, float startYaw, List<BlockPos> route) {
        this.blocks = blocks;
        this.start = start;
        this.startYaw = startYaw;
        this.route = route;
    }

    /** The pressure plates of the course (Hypixel's start, checkpoints and finish), in route order where on it. */
    public List<BlockPos> plates() {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos stood : route) {
            BlockPos above = stood.above();
            if (isPlate(blocks.get(above)) && !out.contains(above)) out.add(above);
            if (isPlate(blocks.get(stood)) && !out.contains(stood)) out.add(stood);
        }
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            if (isPlate(e.getValue()) && !out.contains(e.getKey())) out.add(e.getKey());
        }
        return out;
    }

    static boolean isPlate(BlockState state) {
        return state != null && state.getBlock() instanceof BasePressurePlateBlock;
    }

    /** The lowest corner of the blocks (and the route), so everything is at 0 or above. */
    private BlockPos min() {
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
        for (BlockPos p : blocks.keySet()) {
            x = Math.min(x, p.getX());
            y = Math.min(y, p.getY());
            z = Math.min(z, p.getZ());
        }
        for (BlockPos p : route) {
            x = Math.min(x, p.getX());
            y = Math.min(y, p.getY());
            z = Math.min(z, p.getZ());
        }
        x = Math.min(x, start.getX());
        y = Math.min(y, start.getY());
        z = Math.min(z, start.getZ());
        return new BlockPos(x, y, z);
    }

    public CompoundTag toNbt() {
        BlockPos min = min();
        int sx = 1, sy = 1, sz = 1;
        Map<BlockState, Integer> palette = new LinkedHashMap<>();
        ListTag blockList = new ListTag();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockPos p = e.getKey().subtract(min);
            sx = Math.max(sx, p.getX() + 1);
            sy = Math.max(sy, p.getY() + 1);
            sz = Math.max(sz, p.getZ() + 1);
            int index = palette.computeIfAbsent(e.getValue(), s -> palette.size());
            CompoundTag block = new CompoundTag();
            block.put("pos", pos(p));
            block.putInt("state", index);
            blockList.add(block);
        }
        ListTag paletteList = new ListTag();
        for (BlockState state : palette.keySet()) paletteList.add(NbtUtils.writeBlockState(state));

        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", BlockStateReader.currentDataVersion());
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(sx));
        size.add(IntTag.valueOf(sy));
        size.add(IntTag.valueOf(sz));
        root.put("size", size);
        root.put("palette", paletteList);
        root.put("blocks", blockList);
        root.put("entities", new ListTag());

        CompoundTag parkour = new CompoundTag();
        parkour.put("start", pos(start.subtract(min)));
        parkour.putFloat("yaw", startYaw);
        ListTag routeList = new ListTag();
        for (BlockPos p : route) routeList.add(pos(p.subtract(min)));
        parkour.put("route", routeList);
        root.put("dih_parkour", parkour);
        return root;
    }

    public static CourseRecording fromNbt(CompoundTag root) {
        BlockStateReader reader = new BlockStateReader(root.getIntOr("DataVersion", BlockStateReader.currentDataVersion()));
        ListTag paletteList = root.getListOrEmpty("palette");
        BlockState[] palette = new BlockState[paletteList.size()];
        for (int i = 0; i < palette.length; i++) palette[i] = reader.read(paletteList.getCompoundOrEmpty(i));
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (Tag entry : root.getListOrEmpty("blocks")) {
            if (!(entry instanceof CompoundTag block)) continue;
            int state = block.getIntOr("state", -1);
            BlockPos p = pos(block.getListOrEmpty("pos"));
            if (p != null && state >= 0 && state < palette.length && !palette[state].isAir()) blocks.put(p, palette[state]);
        }
        CompoundTag parkour = root.getCompoundOrEmpty("dih_parkour");
        BlockPos start = pos(parkour.getListOrEmpty("start"));
        List<BlockPos> route = new ArrayList<>();
        for (Tag entry : parkour.getListOrEmpty("route")) {
            if (entry instanceof ListTag list) {
                BlockPos p = pos(list);
                if (p != null) route.add(p);
            }
        }
        if (start == null) start = route.isEmpty() ? BlockPos.ZERO : route.getFirst().above();
        return new CourseRecording(blocks, start, parkour.getFloatOr("yaw", 0.0F), route);
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(toNbt(), file);
    }

    public static CourseRecording load(Path file) throws IOException {
        return fromNbt(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
    }

    private static ListTag pos(BlockPos p) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(p.getX()));
        list.add(IntTag.valueOf(p.getY()));
        list.add(IntTag.valueOf(p.getZ()));
        return list;
    }

    private static BlockPos pos(ListTag list) {
        if (list.size() != 3) return null;
        return new BlockPos(list.getIntOr(0, 0), list.getIntOr(1, 0), list.getIntOr(2, 0));
    }
}
