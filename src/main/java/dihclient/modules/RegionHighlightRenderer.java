package dihclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dihclient.render.mc.DihRenderTypes;
import dihclient.util.DihRegionHighlight;
import dihclient.util.DihWorldGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Depth-tested half of the {@code .showregion} highlight: the tinted faces, bright edges and the
 * two corner markers, all hidden behind real blocks. The faint see-through copy of the edges is
 * drawn by {@link ModuleWorldRenderer}, so hidden edges still read as hidden instead of looking
 * as close as the visible ones.
 */
public final class RegionHighlightRenderer {
    private static final float EDGE_WIDTH = 2.5F;
    private static final float CORNER_WIDTH = 2.0F;
    /** Pushes corner markers just outside the region edges they share, so the two don't fight. */
    private static final double CORNER_INFLATE = 0.04;
    private static final float FILL_ALPHA = 0.14F;
    private static final float CORNER_FILL_ALPHA = 0.30F;

    private static boolean hookInstalled;

    private RegionHighlightRenderer() {}

    public static synchronized void installRenderHook() {
        if (hookInstalled) return;
        hookInstalled = true;
        dihclient.platform.DihPlatform.onCollectSubmits(context -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                DihRegionHighlight.Region region = DihRegionHighlight.region(mc.level);
                if (region == null || mc.player == null || mc.gui.hud.isHidden() || PackHideState.isActive()) return;
                Vec3 camera = context.levelState().cameraRenderState.pos;
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.schematicFill(),
                    (pose, buffer) -> emitFill(pose, buffer, region, camera));
                context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.schematicLines(),
                    (pose, buffer) -> emitEdges(pose, buffer, region, camera));
            } catch (Throwable error) {
                dihclient.DihClientAddon.LOG.debug("Region highlight draw failed", error);
            }
        });
    }

    /** The marker box around one corner block, slightly larger than the block. */
    static AABB cornerBox(BlockPos corner) {
        return new AABB(corner).inflate(CORNER_INFLATE);
    }

    private static void emitFill(PoseStack.Pose pose, VertexConsumer buffer, DihRegionHighlight.Region region, Vec3 cam) {
        fillBox(pose, buffer, region.box().move(-cam.x, -cam.y, -cam.z), withAlpha(DihRegionHighlight.COLOR, FILL_ALPHA));
        fillBox(pose, buffer, cornerBox(region.first()).move(-cam.x, -cam.y, -cam.z),
            withAlpha(DihRegionHighlight.FIRST_CORNER_COLOR, CORNER_FILL_ALPHA));
        if (!region.second().equals(region.first())) {
            fillBox(pose, buffer, cornerBox(region.second()).move(-cam.x, -cam.y, -cam.z),
                withAlpha(DihRegionHighlight.SECOND_CORNER_COLOR, CORNER_FILL_ALPHA));
        }
    }

    private static void emitEdges(PoseStack.Pose pose, VertexConsumer buffer, DihRegionHighlight.Region region, Vec3 cam) {
        boxEdges(pose, buffer, region.box().move(-cam.x, -cam.y, -cam.z), DihRegionHighlight.COLOR, EDGE_WIDTH);
        boxEdges(pose, buffer, cornerBox(region.first()).move(-cam.x, -cam.y, -cam.z),
            DihRegionHighlight.FIRST_CORNER_COLOR, CORNER_WIDTH);
        if (!region.second().equals(region.first())) {
            boxEdges(pose, buffer, cornerBox(region.second()).move(-cam.x, -cam.y, -cam.z),
                DihRegionHighlight.SECOND_CORNER_COLOR, CORNER_WIDTH);
        }
    }

    static void boxEdges(PoseStack.Pose pose, VertexConsumer buffer, AABB b, int color, float width) {
        double x1 = b.minX, y1 = b.minY, z1 = b.minZ, x2 = b.maxX, y2 = b.maxY, z2 = b.maxZ;
        DihWorldGeometry.line(pose, buffer, x1, y1, z1, x2, y1, z1, color, width);
        DihWorldGeometry.line(pose, buffer, x2, y1, z1, x2, y1, z2, color, width);
        DihWorldGeometry.line(pose, buffer, x2, y1, z2, x1, y1, z2, color, width);
        DihWorldGeometry.line(pose, buffer, x1, y1, z2, x1, y1, z1, color, width);
        DihWorldGeometry.line(pose, buffer, x1, y2, z1, x2, y2, z1, color, width);
        DihWorldGeometry.line(pose, buffer, x2, y2, z1, x2, y2, z2, color, width);
        DihWorldGeometry.line(pose, buffer, x2, y2, z2, x1, y2, z2, color, width);
        DihWorldGeometry.line(pose, buffer, x1, y2, z2, x1, y2, z1, color, width);
        DihWorldGeometry.line(pose, buffer, x1, y1, z1, x1, y2, z1, color, width);
        DihWorldGeometry.line(pose, buffer, x2, y1, z1, x2, y2, z1, color, width);
        DihWorldGeometry.line(pose, buffer, x2, y1, z2, x2, y2, z2, color, width);
        DihWorldGeometry.line(pose, buffer, x1, y1, z2, x1, y2, z2, color, width);
    }

    private static void fillBox(PoseStack.Pose pose, VertexConsumer buffer, AABB b, int color) {
        quad(pose, buffer, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ, color);
        quad(pose, buffer, b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ, b.minX, b.maxY, b.minZ, color);
        quad(pose, buffer, b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, color);
        quad(pose, buffer, b.maxX, b.minY, b.minZ, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, color);
        quad(pose, buffer, b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ, color);
        quad(pose, buffer, b.maxX, b.minY, b.maxZ, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4, int color) {
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color);
        buffer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(color);
        buffer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(color);
    }

    static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
