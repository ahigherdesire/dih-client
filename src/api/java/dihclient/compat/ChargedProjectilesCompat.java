package dihclient.compat;

//? if <26.1 {
/*import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;

// Minecraft 1.21.11 only: 26.1 members of ChargedProjectiles, injected by Loom at compile time
// (src/compat1211/resources/fabric.mod.json) and added at runtime by DihChargedProjectilesCompatMixin.
public interface ChargedProjectilesCompat {
    default List<ItemStack> items() {
        return ((ChargedProjectiles) (Object) this).getItems();
    }
}
*///?} else {
/** Minecraft 1.21.11 only (see the other branch). */
public interface ChargedProjectilesCompat {
}
//?}
