package dihclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.IntSetting;
import dihclient.util.DihPackets;
import dihclient.util.DihWorldGeometry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import dihclient.render.mc.DihRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongPredicate;

/**
 * Marks chunks the server generated while you were near. Water and lava are generated as still sources and start to
 * flow only once the chunk ticks, so a chunk that sends flowing-liquid updates after it loads is freshly generated;
 * one whose data already holds flowing liquid has been ticked before, so it is old. Ported from LiquidBounce's
 * NewChunks (GPL-3.0, CCBlueX), which uses only the first test; the second, from Meteor's, keeps old chunks with a
 * waterfall from turning green.
 */
public final class NewChunksModule extends Module {
    enum Kind { NEW, OLD, SEEN }

    private static final float LINE_WIDTH = 1.5F;

    private final Map<Long, Kind> chunks = new ConcurrentHashMap<>();
    private final Queue<Long> toScan = new ConcurrentLinkedQueue<>();
    private Object trackedLevel;

    public NewChunksModule() {
        super("new-chunks", "NewChunks", ModuleCategory.RENDER, "Highlights chunks generated while you were near.");
        add(new IntSetting("render-distance", "Render Distance", 32, 4, 128, 1).unit("chunks"));
        add(new ChoiceSetting("height-mode", "Height", "Fixed", "Fixed", "Relative")
            .description("Draw at a fixed Y, or relative to your feet."));
        add(new IntSetting("y", "Y", 0, -64, 320, 1).visibleWhen(() -> "Fixed".equals(choice("height-mode"))));
        add(new IntSetting("offset", "Offset", -2, -128, 128, 1)
            .visibleWhen(() -> "Relative".equals(choice("height-mode"))));
        add(new BoolSetting("show-old", "Show Old", true).description("Also colour chunks with no sign of being new."));
        add(new BoolSetting("smooth", "Smooth", true).description("Tint old chunks next to new ones."));
        add(new BoolSetting("persist", "Persist", true).description("Keep marks after chunks unload."));
        add(new ColorSetting("new-color", "New Color", 0x5000FF00));
        add(new ColorSetting("old-color", "Old Color", 0x50FF0000));
        ModuleWorldHooks.install(this, this::draw);
    }

    @Override
    public void onDisable() {
        chunks.clear();
        toScan.clear();
    }

    @Override
    public void onGameLeft() {
        chunks.clear();
        toScan.clear();
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        // Runs on the network thread: only record, the chunk is scanned on the next client tick.
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            long key = key(DihPackets.chunkX(chunk), DihPackets.chunkZ(chunk));
            chunks.putIfAbsent(key, Kind.SEEN);
            toScan.add(key);
        } else if (packet instanceof ClientboundBlockUpdatePacket update) {
            if (isFlowing(update.getBlockState())) markNew(update.getPos());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket section) {
            section.runUpdates((pos, state) -> {
                if (isFlowing(state)) markNew(pos);
            });
        } else if (packet instanceof ClientboundForgetLevelChunkPacket forget && !bool("persist")) {
            chunks.remove(key(forget.pos().x(), forget.pos().z()));
        }
        return false;
    }

    private void markNew(BlockPos pos) {
        long key = key(pos.getX() >> 4, pos.getZ() >> 4);
        chunks.compute(key, (k, kind) -> kind == Kind.OLD ? Kind.OLD : Kind.NEW);
    }

    static boolean isFlowing(BlockState state) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && !fluid.isSource();
    }

    @Override
    public void tick() {
        if (MC.level == null) return;
        if (trackedLevel != MC.level) {
            chunks.clear();
            toScan.clear();
            trackedLevel = MC.level;
        }
        Long key;
        int budget = 64;
        while (budget-- > 0 && (key = toScan.poll()) != null) {
            int cx = (int) (key >> 32), cz = (int) (long) key;
            if (!MC.level.hasChunk(cx, cz)) continue;
            if (hasFlowingLiquid(MC.level.getChunk(cx, cz))) {
                chunks.compute(key, (k, kind) -> kind == Kind.NEW ? Kind.NEW : Kind.OLD);
            }
        }
    }

    /** Whether any section holds flowing liquid; the palette is checked first so most sections cost nothing. */
    private static boolean hasFlowingLiquid(LevelChunk chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.getStates().maybeHas(NewChunksModule::isFlowing)) continue;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (isFlowing(section.getBlockState(x, y, z))) return true;
                    }
                }
            }
        }
        return false;
    }

    static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /**
     * How strongly an old chunk takes the new colour: new neighbours one chunk away count 1, two away 0.5, and 12
     * worth (of the 16 possible) is full strength.
     */
    static double smoothRatio(int chunkX, int chunkZ, LongPredicate isNew) {
        double weight = 0.0D;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (!isNew.test(key(chunkX + dx, chunkZ + dz))) continue;
                weight += (3.0D - Math.max(Math.abs(dx), Math.abs(dz))) / 2.0D;
            }
        }
        return Math.min(1.0D, weight / 12.0D);
    }

    private void draw(LevelRenderContext context, Vec3 camera) {
        if (chunks.isEmpty()) return;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.storageEspFillSeeThrough(),
            (pose, buffer) -> emit(pose, buffer, camera, false));
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.storageEspLinesSeeThrough(),
            (pose, buffer) -> emit(pose, buffer, camera, true));
    }

    private void emit(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, boolean outline) {
        int newColor = ModuleRenderUtil.color(this, "new-color", 0x5000FF00);
        int oldColor = ModuleRenderUtil.color(this, "old-color", 0x50FF0000);
        boolean showOld = bool("show-old");
        boolean smooth = bool("smooth");
        double y = "Relative".equals(choice("height-mode"))
            ? Math.floor(MC.player.getY()) + integer("offset")
            : integer("y");
        double maxDist = integer("render-distance") * 16.0D;
        double maxDistSq = maxDist * maxDist;
        double px = MC.player.getX(), pz = MC.player.getZ();
        LongPredicate isNew = k -> chunks.get(k) == Kind.NEW;
        for (Map.Entry<Long, Kind> entry : chunks.entrySet()) {
            long key = entry.getKey();
            int cx = (int) (key >> 32), cz = (int) key;
            double minX = cx * 16.0D, minZ = cz * 16.0D;
            double dx = minX + 8.0D - px, dz = minZ + 8.0D - pz;
            if (dx * dx + dz * dz > maxDistSq) continue;
            int color;
            if (entry.getValue() == Kind.NEW) {
                color = newColor;
            } else {
                if (!showOld) continue;
                color = smooth ? ModuleWorldHooks.blend(oldColor, newColor, smoothRatio(cx, cz, isNew)) : oldColor;
            }
            double x1 = minX - camera.x, x2 = x1 + 16.0D, z1 = minZ - camera.z, z2 = z1 + 16.0D, ry = y - camera.y;
            if (outline) {
                int line = ModuleWorldHooks.withAlpha(color, 2.0D);
                DihWorldGeometry.line(pose, buffer, x1, ry, z1, x2, ry, z1, line, LINE_WIDTH);
                DihWorldGeometry.line(pose, buffer, x2, ry, z1, x2, ry, z2, line, LINE_WIDTH);
                DihWorldGeometry.line(pose, buffer, x2, ry, z2, x1, ry, z2, line, LINE_WIDTH);
                DihWorldGeometry.line(pose, buffer, x1, ry, z2, x1, ry, z1, line, LINE_WIDTH);
            } else {
                ModuleWorldHooks.sheet(pose, buffer, x1, x2, z1, z2, ry, color);
            }
        }
    }
}
