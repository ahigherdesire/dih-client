package dihclient.compat;

//? if <26.1 {
/*import net.minecraft.client.Minecraft;

// Minecraft 1.21.11 only: 26.1 members of Window, injected by Loom at compile time
// (src/compat1211/resources/fabric.mod.json) and added at runtime by DihWindowCompatMixin.
public interface WindowCompat {
    default boolean isFocused() {
        return Minecraft.getInstance().isWindowActive();
    }
}
*///?} else {
/** Minecraft 1.21.11 only (see the other branch). */
public interface WindowCompat {
}
//?}
