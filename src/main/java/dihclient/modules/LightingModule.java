package dihclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dihclient.api.module.BoolSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.IntSetting;
import dihclient.render.mc.DihRenderTypes;
import dihclient.util.DihPerf;
import dihclient.util.DihSpawnLight;
import dihclient.util.DihSpawnLightScanner;
import dihclient.util.DihWorldGeometry;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Marks the floor of every nearby spot where hostile mobs can spawn because it is too dark, so
 * you know where to put torches. Red spots are dark all day; yellow ones only at night (open sky,
 * no block light). Markers are depth-tested, so only the spots you can actually see show up.
 */
public final class LightingModule extends Module {
    private static final int DEFAULT_ALWAYS_COLOR = 0xFFFF3B3B;
    private static final int DEFAULT_NIGHT_COLOR = 0xFFFFD23B;
    /** Cells checked per tick; a 16/8 box (9.2k cells) finishes in under half a second. */
    private static final int CELLS_PER_TICK = 4096;
    /** Lifts the marker off the floor so it doesn't z-fight with the block's top face. */
    private static final double LIFT = 0.01;
    /** Insets the marker so neighbouring markers keep a visible gap between them. */
    private static final double INSET = 0.06;
    private static final float LINE_WIDTH = 2.0F;
    private static final float FILL_ALPHA = 0.22F;

    private static volatile boolean renderHookInstalled;
    private static LightingModule cachedInstance;

    private final DihSpawnLightScanner scanner = new DihSpawnLightScanner();
    private int cachedRevision = -1;
    private int horizontal = 16;
    private int vertical = 8;
    private int maxMarkers = 1024;
    private int interval = 10;
    private boolean showNight = true;
    private boolean fill = true;
    private int alwaysColor = DEFAULT_ALWAYS_COLOR;
    private int nightColor = DEFAULT_NIGHT_COLOR;

    public LightingModule() {
        super("lighting", "Lighting", ModuleCategory.RENDER, "Shows dark spots where mobs can spawn.");
        add(new IntSetting("horizontal-distance", "Horizontal Distance", 16, 4, 48, 1)
            .group("General").description("Scan radius on X/Z.").build());
        add(new IntSetting("vertical-distance", "Vertical Distance", 8, 2, 24, 1)
            .group("General").description("Scan radius on Y.").build());
        add(new IntSetting("max-markers", "Max Markers", 1024, 64, 4096, 64)
            .group("General").description("Most spots drawn at once; the nearest are kept.").build());
        add(new IntSetting("rescan-interval", "Rescan Interval", 10, 0, 100, 1)
            .group("General").description("Ticks between scans of the area.").build());
        add(new BoolSetting("show-night", "Night-Only Spots", true)
            .group("General").description("Also mark open-sky spots that are only dark at night.").build());

        add(new BoolSetting("fill", "Fill", true)
            .group("Render").description("Tint the marked floor, not just its outline.").build());
        add(new ColorSetting("color-always", "Always Dark", DEFAULT_ALWAYS_COLOR)
            .group("Colors").description("Dark at any time of day.").build());
        add(new ColorSetting("color-night", "Night Only", DEFAULT_NIGHT_COLOR)
            .group("Colors").description("Lit by the sky by day, dark at night.").build());
    }

    public static void initialize() {
        installRenderHook();
    }

    @Override
    public void onEnable() {
        cachedRevision = -1;
        scanner.clear();
        installRenderHook();
    }

    @Override
    public void onDisable() {
        scanner.clear();
    }

    @Override
    public void onGameLeft() {
        scanner.clear();
    }

    @Override
    public void tick() {
        if (MC.level == null || MC.player == null) {
            scanner.clear();
            return;
        }
        refreshSettings();
        long perf = DihPerf.beginSampled();
        scanner.tick(MC.level, MC.player.position(), MC.level.getGameTime(), horizontal, vertical,
            CELLS_PER_TICK, interval, maxMarkers);
        DihPerf.end("tick.lighting", perf);
    }

    @Override
    public String info() {
        return Integer.toString(scanner.spots().size());
    }

    private void refreshSettings() {
        int revision = ModuleRegistry.revision();
        if (revision == cachedRevision) return;
        cachedRevision = revision;
        horizontal = integer("horizontal-distance");
        vertical = integer("vertical-distance");
        maxMarkers = integer("max-markers");
        interval = integer("rescan-interval");
        showNight = bool("show-night");
        fill = bool("fill");
        alwaysColor = ModuleRenderUtil.color(this, "color-always", DEFAULT_ALWAYS_COLOR);
        nightColor = ModuleRenderUtil.color(this, "color-night", DEFAULT_NIGHT_COLOR);
    }

    private static LightingModule instance() {
        LightingModule cached = cachedInstance;
        if (cached != null) return cached;
        Module module = ModuleRegistry.get("lighting");
        return module instanceof LightingModule lighting ? (cachedInstance = lighting) : null;
    }

    private static synchronized void installRenderHook() {
        if (renderHookInstalled) return;
        renderHookInstalled = true;
        dihclient.platform.DihPlatform.onCollectSubmits(context -> {
            try {
                LightingModule module = instance();
                if (module == null || !module.isEnabled()) return;
                if (PackHideState.isActive()) return;
                if (MC.level == null || MC.player == null || MC.gui.hud.isHidden()) return;
                if (ModuleRenderUtil.shouldSuppressEspForUi()) return;
                module.refreshSettings();
                List<DihSpawnLight.Spot> spots = module.scanner.spots();
                if (spots.isEmpty()) return;
                Vec3 camera = context.levelState().cameraRenderState.pos;
                // Depth-tested like .showregion's bright edges: markers behind walls stay hidden.
                if (module.fill) {
                    context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.schematicFill(),
                        (pose, buffer) -> module.emitFill(pose, buffer, spots, camera));
                }
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.schematicLines(),
                    (pose, buffer) -> module.emitOutline(pose, buffer, spots, camera));
            } catch (Throwable error) {
                dihclient.DihClientAddon.LOG.debug("Lighting draw failed", error);
            }
        });
    }

    private int colorFor(DihSpawnLight.Darkness darkness) {
        return darkness == DihSpawnLight.Darkness.ALWAYS ? alwaysColor : nightColor;
    }

    private boolean shows(DihSpawnLight.Spot spot) {
        return showNight || spot.darkness() == DihSpawnLight.Darkness.ALWAYS;
    }

    private void emitFill(PoseStack.Pose pose, VertexConsumer buffer, List<DihSpawnLight.Spot> spots, Vec3 cam) {
        long perf = DihPerf.beginSampled();
        for (DihSpawnLight.Spot spot : spots) {
            if (!shows(spot)) continue;
            int color = RegionHighlightRenderer.withAlpha(colorFor(spot.darkness()), FILL_ALPHA);
            double x1 = spot.x() + INSET - cam.x, x2 = spot.x() + 1 - INSET - cam.x;
            double z1 = spot.z() + INSET - cam.z, z2 = spot.z() + 1 - INSET - cam.z;
            float y = (float) (spot.y() + LIFT - cam.y);
            buffer.addVertex(pose, (float) x1, y, (float) z2).setColor(color);
            buffer.addVertex(pose, (float) x2, y, (float) z2).setColor(color);
            buffer.addVertex(pose, (float) x2, y, (float) z1).setColor(color);
            buffer.addVertex(pose, (float) x1, y, (float) z1).setColor(color);
        }
        DihPerf.end("frame.lightingFill", perf);
    }

    private void emitOutline(PoseStack.Pose pose, VertexConsumer buffer, List<DihSpawnLight.Spot> spots, Vec3 cam) {
        long perf = DihPerf.beginSampled();
        for (DihSpawnLight.Spot spot : spots) {
            if (!shows(spot)) continue;
            int color = colorFor(spot.darkness());
            double x1 = spot.x() + INSET - cam.x, x2 = spot.x() + 1 - INSET - cam.x;
            double z1 = spot.z() + INSET - cam.z, z2 = spot.z() + 1 - INSET - cam.z;
            double y = spot.y() + LIFT - cam.y;
            DihWorldGeometry.line(pose, buffer, x1, y, z1, x2, y, z1, color, LINE_WIDTH);
            DihWorldGeometry.line(pose, buffer, x2, y, z1, x2, y, z2, color, LINE_WIDTH);
            DihWorldGeometry.line(pose, buffer, x2, y, z2, x1, y, z2, color, LINE_WIDTH);
            DihWorldGeometry.line(pose, buffer, x1, y, z2, x1, y, z1, color, LINE_WIDTH);
            if (spot.darkness() == DihSpawnLight.Darkness.ALWAYS) {
                // A cross marks the always-dark spots, so the two kinds differ by shape, not only colour.
                DihWorldGeometry.line(pose, buffer, x1, y, z1, x2, y, z2, color, LINE_WIDTH);
                DihWorldGeometry.line(pose, buffer, x2, y, z1, x1, y, z2, color, LINE_WIDTH);
            }
        }
        DihPerf.end("frame.lightingOutline", perf);
    }
}
