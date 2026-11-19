package dihclient.compat;

//? if <26.1 {
/*import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

// Minecraft 1.21.11 only: 26.1 members of BlockStateBase, injected by Loom at compile time
// (src/compat1211/resources/fabric.mod.json) and added at runtime by DihBlockStateCompatMixin.
public interface BlockStateCompat {
    default Holder<Block> typeHolder() {
        return ((BlockBehaviour.BlockStateBase) (Object) this).getBlockHolder();
    }

    default boolean isSingletonState() {
        return ((BlockBehaviour.BlockStateBase) (Object) this).getValues().isEmpty();
    }
}
*///?} else {
/** Minecraft 1.21.11 only (see the other branch). */
public interface BlockStateCompat {
}
//?}
