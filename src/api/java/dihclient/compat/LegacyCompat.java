package dihclient.compat;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** Small helpers the 1.21.11 build's rewritten sources call where 26.x had a direct API. */
public final class LegacyCompat {
    private LegacyCompat() {
    }

    /** The direction a camera with {@code orientation} looks along (vanilla cameras look down -Z). */
    public static Vec3 forward(Quaternionfc orientation) {
        if (orientation == null) return new Vec3(0.0, 0.0, -1.0);
        Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F).rotate(orientation);
        return new Vec3(forward.x, forward.y, forward.z);
    }

    //? if <26.1 {
    /*// The level projection (1.21.11's CameraRenderState doesn't carry it); ignores FOV effects such as sprinting.
    public static org.joml.Matrix4f projectionMatrix() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc.gameRenderer.getProjectionMatrix(mc.options.fov().get().floatValue());
    }
    *///?}
}
