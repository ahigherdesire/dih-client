package dihclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dihclient.api.module.BoolSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.IntSetting;
import dihclient.render.mc.DihRenderTypes;
import dihclient.util.DihUiScale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Shows the defence layers around nearby beds and the fastest way in. Layer k is every
 * block at taxicab distance k from the bed (at or above bed height). The fastest way in is
 * a Dijkstra search where breaking a block costs its mining time with the best hotbar tool
 * and every step costs walking time, starting from where the player stands.
 */
public final class BedEspModule extends Module {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int WALK_TICKS_PER_BLOCK = 4;
    private static final int UNBREAKABLE = Integer.MAX_VALUE / 4;
    private static final float LINE_WIDTH = 1.5f;
    private static final float PATH_LINE_WIDTH = 2.5f;
    private static final double INFLATE = 0.002;

    private static volatile boolean renderHookInstalled;

    private volatile List<Bed> beds = List.of();
    private int scanCooldown;

    private int cachedRevision = -1;
    private boolean fill = true;
    private boolean outline = true;
    private boolean showLayers = true;
    private boolean showPath = true;
    private int bedColor;
    private int pathColor;
    private final int[] layerColors = new int[3];

    BedEspModule() {
        super("bed-esp", "BedESP", ModuleCategory.RENDER,
            "Shows bed defence layers and the fastest way through them.");

        add(new IntSetting("range", "Range", 48, 8, 128, 4)
            .description("Horizontal distance to look for beds.").build());
        add(new IntSetting("layers", "Layers", 3, 1, 6, 1)
            .description("How many defence layers to show.").build());
        add(new IntSetting("max-beds", "Max Beds", 8, 1, 32, 1)
            .description("Most beds analysed at once (nearest first).").build());

        add(new BoolSetting("show-layers", "Show Layers", true)
            .group("Display").description("Colour each defence block by its layer.").build());
        add(new BoolSetting("show-path", "Fastest Path", true)
            .group("Display").description("Highlight the blocks to break on the fastest way in.").build());
        add(new BoolSetting("hud", "HUD Summary", true)
            .group("Display").description("Show the nearest bed's layers and break time on screen.").build());
        add(new BoolSetting("fill", "Fill", true)
            .group("Display").description("Draw translucent faces.").build());
        add(new BoolSetting("outline", "Outline", true)
            .group("Display").description("Draw edges.").build());

        add(new ColorSetting("color-bed", "Bed", 0xFFFF3B3B)
            .group("Colors").description("The bed itself.").build());
        add(new ColorSetting("color-layer-1", "Layer 1", 0xFFFF8C42)
            .group("Colors").description("Blocks touching the bed.").build());
        add(new ColorSetting("color-layer-2", "Layer 2", 0xFFFFD166)
            .group("Colors").description("The second layer.").build());
        add(new ColorSetting("color-layer-3", "Layer 3+", 0xFF06D6A0)
            .group("Colors").description("The third layer and beyond.").build());
        add(new ColorSetting("color-path", "Path", 0xFF4CC9F0)
            .group("Colors").description("Blocks on the fastest way in.").build());
    }

    private static BedEspModule instance() {
        Module module = ModuleRegistry.get("bed-esp");
        return module instanceof BedEspModule bedEsp ? bedEsp : null;
    }

    static boolean hasHudWork() {
        BedEspModule module = instance();
        return module != null && module.isEnabled() && module.bool("hud") && !module.beds.isEmpty();
    }

    static void renderHud(GuiGraphicsExtractor context) {
        BedEspModule module = instance();
        if (module == null || !module.isEnabled() || !module.bool("hud")) return;
        List<Bed> current = module.beds;
        if (current.isEmpty()) return;
        Bed nearest = current.get(0);
        Font font = MC.font;
        String first = String.format(Locale.ROOT, "Bed %dm", Math.round(nearest.distance()));
        if (nearest.totalTicks() >= 0) {
            first += String.format(Locale.ROOT, "  ·  fastest ~%.1fs (breaking %.1fs)",
                nearest.totalTicks() / 20.0, nearest.breakTicks() / 20.0);
        } else {
            first += "  ·  no way in found";
        }
        int screenWidth = DihUiScale.getVirtualScreenWidth();
        // Above the action bar: the top edge holds the watermark, info lines and module list.
        int y = DihUiScale.getVirtualScreenHeight() - 70 - 2 * (font.lineHeight + 2);
        context.text(font, first, (screenWidth - font.width(first)) / 2, y, 0xFFFFFFFF, true);
        y += font.lineHeight + 2;
        String second = nearest.summary();
        context.text(font, second, (screenWidth - font.width(second)) / 2, y, 0xFFC8C8C8, true);
    }

    @Override
    public String info() {
        return Integer.toString(beds.size());
    }

    @Override
    public void onEnable() {
        scanCooldown = 0;
        cachedRevision = -1;
        installRenderHook();
    }

    @Override
    public void onDisable() {
        beds = List.of();
    }

    @Override
    public void onGameLeft() {
        beds = List.of();
    }

    @Override
    public void tick() {
        installRenderHook();
        refreshSettings();
        if (MC.player == null || MC.level == null) {
            beds = List.of();
            return;
        }
        if (--scanCooldown > 0) return;
        scanCooldown = SCAN_INTERVAL_TICKS;
        beds = scan(MC.level);
    }

    private void refreshSettings() {
        int revision = ModuleRegistry.revision();
        if (revision == cachedRevision) return;
        cachedRevision = revision;
        fill = bool("fill");
        outline = bool("outline");
        showLayers = bool("show-layers");
        showPath = bool("show-path");
        bedColor = ModuleRenderUtil.color(this, "color-bed", 0xFFFF3B3B);
        pathColor = ModuleRenderUtil.color(this, "color-path", 0xFF4CC9F0);
        layerColors[0] = ModuleRenderUtil.color(this, "color-layer-1", 0xFFFF8C42);
        layerColors[1] = ModuleRenderUtil.color(this, "color-layer-2", 0xFFFFD166);
        layerColors[2] = ModuleRenderUtil.color(this, "color-layer-3", 0xFF06D6A0);
    }

    private static boolean isBed(BlockState state) {
        return state.is(BlockTags.BEDS)
            && state.hasProperty(BlockStateProperties.BED_PART)
            && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING);
    }

    private List<Bed> scan(ClientLevel level) {
        int range = Math.max(1, integer("range"));
        int layers = Math.max(1, integer("layers"));
        int maxBeds = Math.max(1, integer("max-beds"));
        Vec3 player = MC.player.position();
        int chunkRadius = (range >> 4) + 1;
        int playerChunkX = Mth.floor(player.x) >> 4;
        int playerChunkZ = Mth.floor(player.z) >> 4;
        int minSection = level.getMinSectionY();
        long rangeSq = (long) range * range;

        List<BlockPos> feet = new ArrayList<>();
        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                ChunkAccess chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(BedEspModule::isBed)) continue;
                    int baseY = (minSection + i) << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!isBed(state) || state.getValue(BlockStateProperties.BED_PART) != BedPart.FOOT) continue;
                                int worldX = (cx << 4) + x;
                                int worldZ = (cz << 4) + z;
                                double dx = worldX + 0.5 - player.x;
                                double dz = worldZ + 0.5 - player.z;
                                if (dx * dx + dz * dz > rangeSq) continue;
                                feet.add(new BlockPos(worldX, baseY + y, worldZ));
                            }
                        }
                    }
                }
            }
        }

        feet.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(player)));
        Map<BlockState, Integer> breakCache = new HashMap<>();
        List<Bed> out = new ArrayList<>();
        for (BlockPos foot : feet) {
            if (out.size() >= maxBeds) break;
            Bed bed = analyze(level, foot, layers, player, breakCache);
            if (bed != null) out.add(bed);
        }
        return List.copyOf(out);
    }

    private Bed analyze(ClientLevel level, BlockPos foot, int layers, Vec3 player, Map<BlockState, Integer> breakCache) {
        BlockState footState = level.getBlockState(foot);
        if (!isBed(footState)) return null;
        Direction facing = footState.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos head = foot.relative(facing);
        int bedY = foot.getY();

        List<LayerBlock> layerBlocks = new ArrayList<>();
        List<Map<String, Integer>> materials = new ArrayList<>();
        for (int i = 0; i < layers; i++) materials.add(new LinkedHashMap<>());

        int minX = Math.min(foot.getX(), head.getX()) - layers;
        int maxX = Math.max(foot.getX(), head.getX()) + layers;
        int minZ = Math.min(foot.getZ(), head.getZ()) - layers;
        int maxZ = Math.max(foot.getZ(), head.getZ()) + layers;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = bedY; y <= bedY + layers; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int layer = Math.min(taxicab(x, y, z, foot), taxicab(x, y, z, head));
                    if (layer < 1 || layer > layers) continue;
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.canBeReplaced()) continue;
                    layerBlocks.add(new LayerBlock(cursor.immutable(), layer));
                    materials.get(layer - 1).merge(state.getBlock().getName().getString(), 1, Integer::sum);
                }
            }
        }

        PathResult path = fastestPath(level, foot, head, layers, player, breakCache);
        double distance = Math.sqrt(foot.distToCenterSqr(player));
        return new Bed(foot, head, distance, List.copyOf(layerBlocks), path.blocks(),
            path.breakTicks(), path.totalTicks(), summarize(materials));
    }

    private PathResult fastestPath(ClientLevel level, BlockPos foot, BlockPos head, int layers, Vec3 player,
                                   Map<BlockState, Integer> breakCache) {
        int pad = layers + 2;
        int minX = Math.min(foot.getX(), head.getX()) - pad;
        int maxX = Math.max(foot.getX(), head.getX()) + pad;
        int minZ = Math.min(foot.getZ(), head.getZ()) - pad;
        int maxZ = Math.max(foot.getZ(), head.getZ()) + pad;
        int minY = foot.getY();
        int maxY = foot.getY() + pad;
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        int cells = sizeX * sizeY * sizeZ;

        int[] enter = new int[cells];
        boolean[] goal = new boolean[cells];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int index = 0; index < cells; index++) {
            int x = minX + index % sizeX;
            int y = minY + (index / sizeX) % sizeY;
            int z = minZ + index / (sizeX * sizeY);
            cursor.set(x, y, z);
            if (cursor.equals(foot) || cursor.equals(head)) {
                goal[index] = true;
                continue;
            }
            BlockState state = level.getBlockState(cursor);
            enter[index] = passable(level, state, cursor) ? 0 : breakTicks(level, state, cursor, breakCache);
        }

        int[] dist = new int[cells];
        int[] previous = new int[cells];
        Arrays.fill(dist, Integer.MAX_VALUE);
        Arrays.fill(previous, -1);
        PriorityQueue<long[]> queue = new PriorityQueue<>(Comparator.comparingLong(entry -> entry[0]));

        int playerX = Mth.floor(player.x);
        int playerY = Mth.floor(player.y);
        int playerZ = Mth.floor(player.z);
        boolean playerInside = playerX >= minX && playerX <= maxX && playerY >= minY && playerY <= maxY
            && playerZ >= minZ && playerZ <= maxZ;
        for (int index = 0; index < cells; index++) {
            int x = minX + index % sizeX;
            int y = minY + (index / sizeX) % sizeY;
            int z = minZ + index / (sizeX * sizeY);
            boolean boundary = x == minX || x == maxX || z == minZ || z == maxZ || y == maxY;
            boolean playerCell = playerInside && x == playerX && y == playerY && z == playerZ;
            if (!boundary && !playerCell) continue;
            if (enter[index] >= UNBREAKABLE) continue;
            double dx = x + 0.5 - player.x;
            double dz = z + 0.5 - player.z;
            int walk = playerCell ? 0 : (int) Math.round(Math.sqrt(dx * dx + dz * dz) * WALK_TICKS_PER_BLOCK);
            int start = walk + enter[index];
            if (start < dist[index]) {
                dist[index] = start;
                queue.add(new long[]{start, index});
            }
        }

        int reached = -1;
        while (!queue.isEmpty()) {
            long[] entry = queue.poll();
            int index = (int) entry[1];
            if (entry[0] > dist[index]) continue;
            if (goal[index]) {
                reached = index;
                break;
            }
            int x = index % sizeX;
            int y = (index / sizeX) % sizeY;
            int z = index / (sizeX * sizeY);
            for (Direction direction : Direction.values()) {
                int nx = x + direction.getStepX();
                int ny = y + direction.getStepY();
                int nz = z + direction.getStepZ();
                if (nx < 0 || nx >= sizeX || ny < 0 || ny >= sizeY || nz < 0 || nz >= sizeZ) continue;
                int next = nx + ny * sizeX + nz * sizeX * sizeY;
                if (enter[next] >= UNBREAKABLE) continue;
                int cost = dist[index] + WALK_TICKS_PER_BLOCK + enter[next];
                if (cost < dist[next]) {
                    dist[next] = cost;
                    previous[next] = index;
                    queue.add(new long[]{cost, next});
                }
            }
        }
        if (reached < 0) return new PathResult(List.of(), -1, -1);

        List<BlockPos> blocks = new ArrayList<>();
        int breaking = 0;
        for (int index = previous[reached]; index >= 0; index = previous[index]) {
            if (enter[index] <= 0) continue;
            breaking += enter[index];
            blocks.add(new BlockPos(minX + index % sizeX, minY + (index / sizeX) % sizeY, minZ + index / (sizeX * sizeY)));
        }
        return new PathResult(List.copyOf(blocks), breaking, dist[reached]);
    }

    private static boolean passable(ClientLevel level, BlockState state, BlockPos pos) {
        return state.isAir() || state.canBeReplaced() || state.getCollisionShape(level, pos).isEmpty();
    }

    /** Mining time in ticks with the best hotbar item (enchantments and effects ignored). */
    private int breakTicks(ClientLevel level, BlockState state, BlockPos pos, Map<BlockState, Integer> cache) {
        Integer cached = cache.get(state);
        if (cached != null) return cached;
        float hardness = state.getDestroySpeed(level, pos);
        int result;
        if (hardness < 0.0f) {
            result = UNBREAKABLE;
        } else if (hardness == 0.0f) {
            result = 1;
        } else {
            double best = Double.MAX_VALUE;
            Inventory inventory = MC.player.getInventory();
            for (int slot = -1; slot < 9; slot++) {
                ItemStack stack = slot < 0 ? ItemStack.EMPTY : inventory.getItem(slot);
                float speed = stack.isEmpty() ? 1.0f : Math.max(1.0f, stack.getDestroySpeed(state));
                boolean harvest = !state.requiresCorrectToolForDrops() || (!stack.isEmpty() && stack.isCorrectToolForDrops(state));
                best = Math.min(best, Math.ceil(hardness * (harvest ? 30.0 : 100.0) / speed));
            }
            result = (int) Math.max(1, Math.min(UNBREAKABLE - 1, best));
        }
        cache.put(state, result);
        return result;
    }

    private static int taxicab(int x, int y, int z, BlockPos bed) {
        return Math.abs(x - bed.getX()) + Math.abs(y - bed.getY()) + Math.abs(z - bed.getZ());
    }

    private static String summarize(List<Map<String, Integer>> materials) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < materials.size(); i++) {
            Map<String, Integer> layer = materials.get(i);
            if (layer.isEmpty()) continue;
            Map.Entry<String, Integer> top = null;
            int total = 0;
            for (Map.Entry<String, Integer> entry : layer.entrySet()) {
                total += entry.getValue();
                if (top == null || entry.getValue() > top.getValue()) top = entry;
            }
            String part = "L" + (i + 1) + " " + top.getKey() + " ×" + top.getValue();
            if (total > top.getValue()) part += " +" + (total - top.getValue());
            parts.add(part);
        }
        return parts.isEmpty() ? "No defence" : String.join("  ·  ", parts);
    }

    private static synchronized void installRenderHook() {
        if (renderHookInstalled) return;
        renderHookInstalled = true;
        dihclient.platform.DihPlatform.onCollectSubmits(context -> {
            try {
                BedEspModule module = instance();
                if (module == null || !module.isEnabled()) return;
                if (PackHideState.isActive()) return;
                if (MC == null || MC.level == null || MC.player == null || MC.gui.hud.isHidden()) return;
                if (ModuleRenderUtil.shouldSuppressEspForUi()) return;
                module.refreshSettings();
                List<Bed> current = module.beds;
                if (current.isEmpty()) return;
                Vec3 camera = context.levelState().cameraRenderState.pos;
                if (module.fill) {
                    context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                        DihRenderTypes.storageEspFillSeeThrough(),
                        (pose, buffer) -> module.emitFill(pose, buffer, current, camera));
                }
                if (module.outline) {
                    context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                        DihRenderTypes.storageEspLinesSeeThrough(),
                        (pose, buffer) -> module.emitOutline(pose, buffer, current, camera));
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private void emitFill(PoseStack.Pose pose, VertexConsumer buffer, List<Bed> current, Vec3 camera) {
        for (Bed bed : current) {
            EspGeometry.fillBox(pose, buffer, box(bed.foot(), camera), EspGeometry.withAlpha(bedColor, 0x50));
            EspGeometry.fillBox(pose, buffer, box(bed.head(), camera), EspGeometry.withAlpha(bedColor, 0x50));
            if (showLayers) {
                for (LayerBlock block : bed.layers()) {
                    EspGeometry.fillBox(pose, buffer, box(block.pos(), camera), EspGeometry.withAlpha(layerColor(block.layer()), 0x2C));
                }
            }
            if (showPath) {
                for (BlockPos pos : bed.path()) {
                    EspGeometry.fillBox(pose, buffer, box(pos, camera), EspGeometry.withAlpha(pathColor, 0x55));
                }
            }
        }
    }

    private void emitOutline(PoseStack.Pose pose, VertexConsumer buffer, List<Bed> current, Vec3 camera) {
        for (Bed bed : current) {
            EspGeometry.edges(pose, buffer, box(bed.foot(), camera), EspGeometry.withAlpha(bedColor, 0xD0), LINE_WIDTH);
            EspGeometry.edges(pose, buffer, box(bed.head(), camera), EspGeometry.withAlpha(bedColor, 0xD0), LINE_WIDTH);
            if (showLayers) {
                for (LayerBlock block : bed.layers()) {
                    EspGeometry.edges(pose, buffer, box(block.pos(), camera), EspGeometry.withAlpha(layerColor(block.layer()), 0x80), LINE_WIDTH);
                }
            }
            if (showPath) {
                for (BlockPos pos : bed.path()) {
                    EspGeometry.edges(pose, buffer, box(pos, camera), EspGeometry.withAlpha(pathColor, 0xE0), PATH_LINE_WIDTH);
                }
            }
        }
    }

    private int layerColor(int layer) {
        return layerColors[Math.max(0, Math.min(layerColors.length - 1, layer - 1))];
    }

    private static AABB box(BlockPos pos, Vec3 camera) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)
            .move(-camera.x, -camera.y, -camera.z).inflate(INFLATE);
    }

    private record Bed(BlockPos foot, BlockPos head, double distance, List<LayerBlock> layers, List<BlockPos> path,
                       int breakTicks, int totalTicks, String summary) {
    }

    private record LayerBlock(BlockPos pos, int layer) {
    }

    private record PathResult(List<BlockPos> blocks, int breakTicks, int totalTicks) {
    }
}
