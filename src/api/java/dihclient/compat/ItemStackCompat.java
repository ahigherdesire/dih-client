package dihclient.compat;

//? if <26.1 {
/*import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// Minecraft 1.21.11 only: 26.1 members of ItemStack, injected by Loom at compile time
// (src/compat1211/resources/fabric.mod.json) and added at runtime by DihItemStackCompatMixin.
public interface ItemStackCompat {
    default Holder<Item> typeHolder() {
        return ((ItemStack) (Object) this).getItemHolder();
    }
}
*///?} else {
/** Minecraft 1.21.11 only (see the other branch). */
public interface ItemStackCompat {
}
//?}
