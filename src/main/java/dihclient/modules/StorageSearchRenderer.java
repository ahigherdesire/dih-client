package dihclient.modules;

import baritone.cache.ChestMemory;
import baritone.cache.ChestMemory.SearchResult;
import dihclient.platform.DihPlatform;
import dihclient.render.mc.DihRenderTypes;
import dihclient.util.DihStorageSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Only draws precomputed boxes. Searches and block-shape reads run on client ticks. */
public final class StorageSearchRenderer {
    private static final int COLOR = 0xFF55FFAA;
    private static final DihStorageSearch SEARCH = new DihStorageSearch();
    private static List<AABB> boxes = List.of();
    private static List<SearchResult> results = List.of();
    private static boolean installed;
    private static int ticks;

    private StorageSearchRenderer() {}

    public static List<SearchResult> search(Minecraft mc, String query) {
        return show(mc, query, null);
    }

    /** Selecting an old #chest result highlights it, never starts a path. */
    public static List<SearchResult> show(Minecraft mc, String query, BlockPos selected) {
        install();
        SEARCH.start(mc.level, query, selected);
        refresh(mc);
        ticks = 0;
        return results;
    }

    public static void clear() {
        SEARCH.clear();
        boxes = List.of();
        results = List.of();
    }

    private static synchronized void install() {
        if (installed) return;
        installed = true;
        DihPlatform.onPlayDisconnect(() -> {
            clear();
            ChestMemory.cancelCapture();
        });
        DihPlatform.onEndClientTick(mc -> {
            if (SEARCH.query(mc.level) == null || mc.player == null) {
                boxes = List.of();
                results = List.of();
            } else if (++ticks >= 10) {
                ticks = 0;
                refresh(mc);
            }
        });
        DihPlatform.onCollectSubmits(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (SEARCH.query(mc.level) == null || boxes.isEmpty() || mc.player == null
                || mc.gui.hud.isHidden() || PackHideState.isActive() || ModuleRenderUtil.shouldSuppressEspForUi()) return;
            var snapshot = boxes;
            var camera = context.levelState().cameraRenderState.pos;
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.schematicLines(),
                (pose, buffer) -> {
                    for (AABB box : snapshot) {
                        RegionHighlightRenderer.boxEdges(pose, buffer, box.move(-camera.x, -camera.y, -camera.z), COLOR, 2.5F);
                    }
                });
        });
    }

    private static void refresh(Minecraft mc) {
        boxes = List.of();
        results = List.of();
        String query = SEARCH.query(mc.level);
        if (query == null || mc.player == null) return;
        var candidates = ChestMemory.search(query);
        BlockPos selected = SEARCH.selected(mc.level);
        results = DihStorageSearch.select(candidates, ChestMemory.currentWorldKey(),
            mc.level.dimension().identifier().toString(), mc.player.position(), DihStorageSearch.RADIUS,
            DihStorageSearch.MAX_MARKERS, selected);
        var geometry = new ArrayList<AABB>();
        for (SearchResult result : results) {
            BlockPos pos = result.record.pos;
            if (!mc.level.isLoaded(pos) || !(mc.level.getBlockEntity(pos) instanceof BaseContainerBlockEntity)) continue;
            var state = mc.level.getBlockState(pos);
            var shape = state.getShape(mc.level, pos);
            if (!shape.isEmpty()) geometry.add(shape.bounds().move(pos).inflate(0.015));
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
                if (mc.level.isLoaded(other) && mc.level.getBlockState(other).is(state.getBlock())) {
                    var otherShape = mc.level.getBlockState(other).getShape(mc.level, other);
                    if (!otherShape.isEmpty()) geometry.add(otherShape.bounds().move(other).inflate(0.015));
                }
            }
        }
        boxes = List.copyOf(geometry);
    }
}
