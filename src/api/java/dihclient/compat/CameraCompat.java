package dihclient.compat;

//? if <26.1 {
/*import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

// Minecraft 1.21.11 only: 26.1 members of Camera, injected by Loom at compile time
// (src/compat1211/resources/fabric.mod.json) and added at runtime by DihCameraCompatMixin.
public interface CameraCompat {
    default float getFov() {
        return Minecraft.getInstance().options.fov().get().floatValue();
    }

    default Matrix4f getViewRotationProjectionMatrix(Matrix4f out) {
        Camera self = (Camera) (Object) this;
        Matrix4f projection = Minecraft.getInstance().gameRenderer.getProjectionMatrix(getFov());
        Matrix4f viewRotation = new Matrix4f().rotation(new Quaternionf(self.rotation()).conjugate());
        return out.set(projection).mul(viewRotation);
    }
}
*///?} else {
/** Minecraft 1.21.11 only (see the other branch). */
public interface CameraCompat {
}
//?}
