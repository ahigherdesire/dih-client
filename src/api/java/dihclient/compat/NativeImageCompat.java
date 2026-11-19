package dihclient.compat;

//? if <26.1 {
/*import com.mojang.blaze3d.platform.NativeImage;

// Minecraft 1.21.11 only: 26.1 members of NativeImage, injected by Loom at compile time
// (src/compat1211/resources/fabric.mod.json) and added at runtime by DihNativeImageCompatMixin.
public interface NativeImageCompat {
    default boolean isClosed() {
        return ((NativeImage) (Object) this).getPointer() == 0L;
    }
}
*///?} else {
/** Minecraft 1.21.11 only (see the other branch). */
public interface NativeImageCompat {
}
//?}
