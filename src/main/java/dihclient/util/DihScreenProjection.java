package dihclient.util;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Projects world points onto a GUI of the given size, from the camera as it is this frame. */
public final class DihScreenProjection {
    private final Vec3 camera;
    private final Matrix4f matrix;
    private final int width;
    private final int height;
    private final Vector4f scratch = new Vector4f();

    private DihScreenProjection(Vec3 camera, Matrix4f matrix, int width, int height) {
        this.camera = camera;
        this.matrix = matrix;
        this.width = width;
        this.height = height;
    }

    /** The projection for this frame, or null without a camera. */
    public static DihScreenProjection current(Minecraft mc, int guiWidth, int guiHeight) {
        if (mc == null || mc.gameRenderer == null) return null;
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null) return null;
        return new DihScreenProjection(camera.position(), camera.getViewRotationProjectionMatrix(new Matrix4f()),
            guiWidth, guiHeight);
    }

    public Vec3 camera() {
        return camera;
    }

    /** The GUI x and y of a world point, or null when it is behind the camera. */
    public float[] project(double x, double y, double z) {
        scratch.set((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), 1.0F);
        matrix.transform(scratch);
        if (scratch.w <= 0.001F) return null;
        float ndcX = scratch.x / scratch.w;
        float ndcY = scratch.y / scratch.w;
        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) return null;
        return new float[] {(ndcX * 0.5F + 0.5F) * width, (0.5F - ndcY * 0.5F) * height};
    }
}
