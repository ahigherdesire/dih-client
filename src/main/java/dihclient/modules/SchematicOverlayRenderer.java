package dihclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dihclient.render.mc.DihRenderTypes;
import dihclient.schematic.SchematicPlacement;
import dihclient.schematic.SchematicVerifier;
import dihclient.schematic.SchematicVerifier.Mismatch;
import dihclient.schematic.Schematics;
import dihclient.util.DihPackets;
import dihclient.util.DihPerf;
import dihclient.util.DihWorldGeometry;
import dihclient.util.oresim.DihOreGhostModels;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Draws schematic placements: ghost blocks where the world is still missing a block, and Litematica's coloured
 * overlay on every position that is not right (missing, extra, wrong block, wrong state).
 * <p>
 * Geometry is cached per placement and 16x16x16 section and rebuilt only when that section changes: a block update
 * from the server, the placement moving or turning, a setting changing, or a slow periodic recheck. Rebuilds share a
 * small time budget per frame, so a large schematic fills in over a few frames instead of stalling one.
 */
public final class SchematicOverlayRenderer {

    private static final long REBUILD_BUDGET_NANOS = 3_000_000L;
    private static final long RECHECK_NANOS = 2_000_000_000L;
    private static final float LINE_WIDTH = 1.5F;

    private static final ConcurrentLinkedQueue<Long> DIRTY_SECTIONS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<Long> DIRTY_COLUMNS = new ConcurrentLinkedQueue<>();
    private static volatile boolean clearRequested;

    private static final Map<String, PlacementCache> CACHE = new HashMap<>();
    private static Object cachedLevel;
    private static boolean hookInstalled;

    /**
     * What one rebuilt section draws, relative to its lowest corner.
     */
    private static final class Section {
        long builtAt;
        int stamp = Integer.MIN_VALUE;
        boolean dirty = true;
        float[] ghost = new float[0];
        int ghostVertices;
        List<ModuleEspMesh.Quad> quads = List.of();
        List<ModuleEspMesh.Edge> edges = List.of();
        final EnumMap<Mismatch, Integer> counts = new EnumMap<>(Mismatch.class);
    }

    private static final class PlacementCache {
        int revision = Integer.MIN_VALUE;
        final Map<Long, Section> sections = new HashMap<>();
    }

    /**
     * The settings that shape the geometry; a change rebuilds everything.
     */
    record Style(boolean ghosts, boolean overlay, boolean outline, boolean seeThrough, int range,
                 String layerMode, int layerY, int ghostAlpha, int missing, int extra, int wrongState, int wrongBlock) {

        int color(Mismatch type) {
            return switch (type) {
                case MISSING -> this.missing;
                case EXTRA -> this.extra;
                case WRONG_STATE -> this.wrongState;
                case WRONG_BLOCK -> this.wrongBlock;
                case NONE -> 0;
            };
        }

        boolean showsLayer(int y) {
            return switch (this.layerMode) {
                case SchematicsModule.LAYER_SINGLE -> y == this.layerY;
                case SchematicsModule.LAYER_BELOW -> y <= this.layerY;
                case SchematicsModule.LAYER_ABOVE -> y >= this.layerY;
                default -> true;
            };
        }
    }

    private SchematicOverlayRenderer() {}

    static synchronized void installRenderHook() {
        if (hookInstalled) {
            return;
        }
        hookInstalled = true;
        dihclient.platform.DihPlatform.onCollectSubmits(context -> {
            try {
                SchematicsModule module = SchematicsModule.instance();
                Minecraft mc = Minecraft.getInstance();
                if (module == null || !module.isEnabled() || mc.level == null || mc.player == null
                    || mc.gui.hud.isHidden() || PackHideState.isActive()) {
                    if (!CACHE.isEmpty()) {
                        CACHE.clear();
                    }
                    return;
                }
                Vec3 camera = context.levelState().cameraRenderState.pos;
                List<Section> drawn = new ArrayList<>();
                List<BlockPos> origins = new ArrayList<>();
                Style style = module.style();
                prepare(mc.level, camera, style, drawn, origins);
                if (drawn.isEmpty()) {
                    return;
                }
                if (style.ghosts()) {
                    context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.schematicGhost(),
                        (pose, buffer) -> emitGhosts(pose, buffer, drawn, origins, camera));
                }
                if (style.overlay()) {
                    context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                        style.seeThrough() ? DihRenderTypes.storageEspFillSeeThrough() : DihRenderTypes.schematicFill(),
                        (pose, buffer) -> emitFill(pose, buffer, drawn, origins, camera));
                }
                if (style.outline()) {
                    context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                        style.seeThrough() ? DihRenderTypes.storageEspLinesSeeThrough() : DihRenderTypes.schematicLines(),
                        (pose, buffer) -> emitEdges(pose, buffer, drawn, origins, camera));
                }
            } catch (Throwable error) {
                dihclient.DihClientAddon.LOG.debug("Schematic overlay draw failed", error);
            }
        });
    }

    /**
     * Notes which sections a packet from the server changed. Runs on the network thread.
     */
    public static void onPacketReceived(Packet<?> packet) {
        if (packet instanceof ClientboundBlockUpdatePacket update) {
            BlockPos pos = update.getPos();
            DIRTY_SECTIONS.add(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
            Set<Long> sections = new HashSet<>();
            sectionUpdate.runUpdates((pos, state) -> sections.add(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4)));
            DIRTY_SECTIONS.addAll(sections);
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            DIRTY_COLUMNS.add(ChunkPos.pack(DihPackets.chunkX(chunk), DihPackets.chunkZ(chunk)));
        } else if (packet instanceof ClientboundRespawnPacket || packet instanceof ClientboundLoginPacket) {
            clearRequested = true;
        }
    }

    /**
     * Drops every cached section, for instance after a block placed client-side.
     */
    public static void invalidate() {
        clearRequested = true;
    }

    /**
     * @return what the drawn sections of a placement show, by kind
     */
    public static Map<Mismatch, Integer> visibleCounts(SchematicPlacement placement) {
        Map<Mismatch, Integer> out = new EnumMap<>(Mismatch.class);
        PlacementCache cache = CACHE.get(placement.id());
        if (cache != null) {
            for (Section section : cache.sections.values()) {
                section.counts.forEach((k, v) -> out.merge(k, v, Integer::sum));
            }
        }
        return out;
    }

    private static void prepare(ClientLevel level, Vec3 camera, Style style, List<Section> drawn, List<BlockPos> origins) {
        if (cachedLevel != level || clearRequested) {
            cachedLevel = level;
            clearRequested = false;
            CACHE.clear();
            DIRTY_SECTIONS.clear();
            DIRTY_COLUMNS.clear();
        }
        Long dirty;
        while ((dirty = DIRTY_SECTIONS.poll()) != null) {
            for (PlacementCache cache : CACHE.values()) {
                Section section = cache.sections.get(dirty);
                if (section != null) {
                    section.dirty = true;
                }
            }
        }
        while ((dirty = DIRTY_COLUMNS.poll()) != null) {
            int cx = ChunkPos.getX(dirty);
            int cz = ChunkPos.getZ(dirty);
            for (PlacementCache cache : CACHE.values()) {
                for (Map.Entry<Long, Section> entry : cache.sections.entrySet()) {
                    long key = entry.getKey();
                    if (SectionPos.x(key) == cx && SectionPos.z(key) == cz) {
                        entry.getValue().dirty = true;
                    }
                }
            }
        }

        long perf = DihPerf.beginSampled();
        long deadline = System.nanoTime() + REBUILD_BUDGET_NANOS;
        boolean rebuiltOne = false;
        int styleKey = style.hashCode();
        String dimension = level.dimension().identifier().toString();
        Set<String> present = new HashSet<>();
        for (SchematicPlacement placement : Schematics.get().placementsIn(dimension)) {
            BoundingBox box = placement.bounds();
            if (!placement.visible() || box == null) {
                continue;
            }
            present.add(placement.id());
            PlacementCache cache = CACHE.computeIfAbsent(placement.id(), id -> new PlacementCache());
            if (cache.revision != placement.revision()) {
                cache.revision = placement.revision();
                cache.sections.clear();
            }
            int stamp = styleKey * 31 + placement.revision();
            double range = style.range();
            int minSx = Math.max(box.minX(), Mth.floor(camera.x - range)) >> 4;
            int maxSx = Math.min(box.maxX(), Mth.floor(camera.x + range)) >> 4;
            int minSy = Math.max(box.minY(), Mth.floor(camera.y - range)) >> 4;
            int maxSy = Math.min(box.maxY(), Mth.floor(camera.y + range)) >> 4;
            int minSz = Math.max(box.minZ(), Mth.floor(camera.z - range)) >> 4;
            int maxSz = Math.min(box.maxZ(), Mth.floor(camera.z + range)) >> 4;
            LongOpenHashSet wanted = new LongOpenHashSet();
            for (int sx = minSx; sx <= maxSx; sx++) {
                for (int sy = minSy; sy <= maxSy; sy++) {
                    for (int sz = minSz; sz <= maxSz; sz++) {
                        long key = SectionPos.asLong(sx, sy, sz);
                        wanted.add(key);
                        Section section = cache.sections.computeIfAbsent(key, k -> new Section());
                        long now = System.nanoTime();
                        boolean stale = section.dirty || section.stamp != stamp || now - section.builtAt > RECHECK_NANOS;
                        if (stale && (!rebuiltOne || now < deadline)) {
                            rebuild(section, placement, level, style, sx, sy, sz, box);
                            section.stamp = stamp;
                            section.dirty = false;
                            section.builtAt = now;
                            rebuiltOne = true;
                        }
                        if (section.stamp == stamp && (section.ghostVertices > 0 || !section.quads.isEmpty())) {
                            drawn.add(section);
                            origins.add(new BlockPos(sx << 4, sy << 4, sz << 4));
                        }
                    }
                }
            }
            cache.sections.keySet().removeIf(key -> !wanted.contains(key.longValue()));
        }
        CACHE.keySet().removeIf(id -> !present.contains(id));
        DihPerf.end("frame.schematicOverlay", perf);
    }

    private static void rebuild(Section section, SchematicPlacement placement, ClientLevel level, Style style,
                                int sx, int sy, int sz, BoundingBox box) {
        section.counts.clear();
        int baseX = sx << 4, baseY = sy << 4, baseZ = sz << 4;
        int x0 = Math.max(box.minX(), baseX), x1 = Math.min(box.maxX(), baseX + 15);
        int y0 = Math.max(box.minY(), baseY), y1 = Math.min(box.maxY(), baseY + 15);
        int z0 = Math.max(box.minZ(), baseZ), z1 = Math.min(box.maxZ(), baseZ + 15);
        List<ModuleEspMesh.Box> boxes = new ArrayList<>();
        List<long[]> ghosts = new ArrayList<>();
        List<BlockState> ghostStates = new ArrayList<>();
        LongOpenHashSet missing = new LongOpenHashSet();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) {
            if (!style.showsLayer(y)) {
                continue;
            }
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    BlockState want = placement.expected(x, y, z);
                    if (want == null || !level.isLoaded(pos.set(x, y, z))) {
                        continue;
                    }
                    Mismatch type = SchematicVerifier.classify(want, level.getBlockState(pos));
                    section.counts.merge(type, 1, Integer::sum);
                    if (type == Mismatch.NONE) {
                        continue;
                    }
                    int lx = x - baseX, ly = y - baseY, lz = z - baseZ;
                    boxes.add(new ModuleEspMesh.Box(new AABB(lx, ly, lz, lx + 1, ly + 1, lz + 1), style.color(type)));
                    if (type == Mismatch.MISSING) {
                        missing.add(BlockPos.asLong(x, y, z));
                        ghosts.add(new long[]{x, y, z});
                        ghostStates.add(want);
                    }
                }
            }
        }
        ModuleEspMesh.Geometry geometry = ModuleEspMesh.build(boxes);
        section.quads = geometry.quads();
        section.edges = geometry.edges();
        buildGhosts(section, level, style, ghosts, ghostStates, missing, baseX, baseY, baseZ);
    }

    private static void buildGhosts(Section section, ClientLevel level, Style style, List<long[]> cells,
                                    List<BlockState> states, LongOpenHashSet missing, int baseX, int baseY, int baseZ) {
        if (!style.ghosts() || cells.isEmpty()) {
            section.ghost = new float[0];
            section.ghostVertices = 0;
            return;
        }
        CardinalLighting lighting = level.cardinalLighting();
        float[] out = new float[Math.max(64, cells.size() * 6 * 4 * 6)];
        int n = 0;
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (int i = 0; i < cells.size(); i++) {
            long[] cell = cells.get(i);
            int x = (int) cell[0], y = (int) cell[1], z = (int) cell[2];
            DihOreGhostModels.Template template = DihOreGhostModels.of(states.get(i));
            for (DihOreGhostModels.Face face : template.faces()) {
                Direction cull = face.cull();
                if (cull != null) {
                    int nx = x + cull.getStepX(), ny = y + cull.getStepY(), nz = z + cull.getStepZ();
                    if (missing.contains(BlockPos.asLong(nx, ny, nz))
                        || level.getBlockState(neighbour.set(nx, ny, nz)).isSolidRender()) {
                        continue;
                    }
                }
                int shade = Mth.clamp(Math.round((face.facing() == null ? 1.0F : lighting.byFace(face.facing())) * 255.0F), 0, 255);
                float color = Float.intBitsToFloat((style.ghostAlpha() << 24) | (shade << 16) | (shade << 8) | shade);
                float[] data = face.data();
                if (n + 24 > out.length) {
                    out = Arrays.copyOf(out, out.length * 2);
                }
                for (int v = 0; v < 4; v++) {
                    int base = v * DihOreGhostModels.VERTEX_STRIDE;
                    out[n++] = x - baseX + data[base];
                    out[n++] = y - baseY + data[base + 1];
                    out[n++] = z - baseZ + data[base + 2];
                    out[n++] = data[base + 3];
                    out[n++] = data[base + 4];
                    out[n++] = color;
                }
            }
        }
        section.ghost = out;
        section.ghostVertices = n / 6;
    }

    private static void emitGhosts(PoseStack.Pose pose, VertexConsumer buffer, List<Section> sections, List<BlockPos> origins, Vec3 camera) {
        for (int s = 0; s < sections.size(); s++) {
            Section section = sections.get(s);
            BlockPos origin = origins.get(s);
            float ox = (float) (origin.getX() - camera.x);
            float oy = (float) (origin.getY() - camera.y);
            float oz = (float) (origin.getZ() - camera.z);
            float[] data = section.ghost;
            int end = section.ghostVertices * 6;
            for (int i = 0; i < end; i += 6) {
                buffer.addVertex(pose, ox + data[i], oy + data[i + 1], oz + data[i + 2])
                    .setUv(data[i + 3], data[i + 4])
                    .setColor(Float.floatToRawIntBits(data[i + 5]));
            }
        }
    }

    private static void emitFill(PoseStack.Pose pose, VertexConsumer buffer, List<Section> sections, List<BlockPos> origins, Vec3 camera) {
        for (int s = 0; s < sections.size(); s++) {
            BlockPos origin = origins.get(s);
            double ox = origin.getX() - camera.x, oy = origin.getY() - camera.y, oz = origin.getZ() - camera.z;
            for (ModuleEspMesh.Quad q : sections.get(s).quads) {
                buffer.addVertex(pose, (float) (ox + q.x1()), (float) (oy + q.y1()), (float) (oz + q.z1())).setColor(q.color());
                buffer.addVertex(pose, (float) (ox + q.x2()), (float) (oy + q.y2()), (float) (oz + q.z2())).setColor(q.color());
                buffer.addVertex(pose, (float) (ox + q.x3()), (float) (oy + q.y3()), (float) (oz + q.z3())).setColor(q.color());
                buffer.addVertex(pose, (float) (ox + q.x4()), (float) (oy + q.y4()), (float) (oz + q.z4())).setColor(q.color());
            }
        }
    }

    private static void emitEdges(PoseStack.Pose pose, VertexConsumer buffer, List<Section> sections, List<BlockPos> origins, Vec3 camera) {
        for (int s = 0; s < sections.size(); s++) {
            BlockPos origin = origins.get(s);
            double ox = origin.getX() - camera.x, oy = origin.getY() - camera.y, oz = origin.getZ() - camera.z;
            for (ModuleEspMesh.Edge e : sections.get(s).edges) {
                DihWorldGeometry.line(pose, buffer, ox + e.x1(), oy + e.y1(), oz + e.z1(), ox + e.x2(), oy + e.y2(), oz + e.z2(),
                    0xE0000000 | (e.color() & 0x00FFFFFF), LINE_WIDTH);
            }
        }
    }
}
