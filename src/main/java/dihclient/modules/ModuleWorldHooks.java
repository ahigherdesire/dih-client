package dihclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dihclient.util.DihWorldGeometry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * World drawing shared by the render modules that keep their own geometry (Breadcrumbs, NewChunks, LogoffSpot and the
 * Nuker and PacketMine targets): one level-render hook per module, run only while it is on and the world is shown,
 * plus box and quad helpers in camera-relative coordinates.
 */
final class ModuleWorldHooks {
    interface Drawer {
        void draw(LevelRenderContext context, Vec3 camera);
    }

    private ModuleWorldHooks() {
    }

    static void install(Module module, Drawer drawer) {
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            try {
                if (!module.isEnabled() || PackHideState.isActive()) return;
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null || mc.player == null || mc.gui.hud.isHidden()) return;
                if (ModuleRenderUtil.shouldSuppressEspForUi()) return;
                drawer.draw(context, context.levelState().cameraRenderState.pos);
            } catch (Throwable ignored) {
            }
        });
    }

    static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                     double x1, double y1, double z1, double x2, double y2, double z2,
                     double x3, double y3, double z3, double x4, double y4, double z4, int color) {
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color);
        buffer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(color);
        buffer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(color);
    }

    /** A horizontal sheet seen from above and below. */
    static void sheet(PoseStack.Pose pose, VertexConsumer buffer,
                      double minX, double maxX, double minZ, double maxZ, double y, int color) {
        quad(pose, buffer, minX, y, minZ, maxX, y, minZ, maxX, y, maxZ, minX, y, maxZ, color);
        quad(pose, buffer, minX, y, maxZ, maxX, y, maxZ, maxX, y, minZ, minX, y, minZ, color);
    }

    static void fillBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ, x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        quad(pose, buffer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, color);
        quad(pose, buffer, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, color);
        quad(pose, buffer, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, color);
        quad(pose, buffer, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, color);
        quad(pose, buffer, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, color);
        quad(pose, buffer, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, color);
    }

    static void edges(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, float width) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ, x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
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

    /** {@code argb} with its alpha scaled by {@code multiplier}; 0 once nothing would show. */
    static int withAlpha(int argb, double multiplier) {
        int alpha = (int) Math.round(((argb >>> 24) & 0xFF) * multiplier);
        if (alpha <= 0) return 0;
        return (Math.min(255, alpha) << 24) | (argb & 0x00FFFFFF);
    }

    /** Linear blend of two ARGB colours, {@code t} from 0 (a) to 1 (b). */
    static int blend(int a, int b, double t) {
        double k = Math.max(0.0D, Math.min(1.0D, t));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            out |= ((int) Math.round(ca + (cb - ca) * k) & 0xFF) << shift;
        }
        return out;
    }
}
