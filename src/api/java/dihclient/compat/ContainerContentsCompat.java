package dihclient.compat;

//? if <26.1 {
/*import java.util.stream.Stream;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

// Minecraft 1.21.11 only: 26.1 members of ItemContainerContents, injected by Loom at compile time
// (src/compat1211/resources/fabric.mod.json) and added at runtime by DihContainerContentsCompatMixin.
public interface ContainerContentsCompat {
    default Stream<ItemStack> nonEmptyItemCopyStream() {
        return ((ItemContainerContents) (Object) this).nonEmptyStream().map(ItemStack::copy);
    }
}
*///?} else {
/** Minecraft 1.21.11 only (see the other branch). */
public interface ContainerContentsCompat {
}
//?}
