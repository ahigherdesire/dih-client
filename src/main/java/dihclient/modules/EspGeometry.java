package dihclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dihclient.util.DihWorldGeometry;
import net.minecraft.world.phys.AABB;

/** Translucent box faces and box edges for the see-through ESP render types (BedESP, Danger). */
final class EspGeometry {
    private EspGeometry() {
    }

    static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    static void fillBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ,
            box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color);
        quad(pose, buffer, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ,
            box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ,
            box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ,
            box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ,
            box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ,
            box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
    }

    static void edges(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, float width) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
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

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4, int color) {
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color);
        buffer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(color);
        buffer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(color);
    }
}
