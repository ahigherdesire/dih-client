package dihclient.mixin.accessor;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface DihItemStackRenderStateAccessor {
    @Accessor("activeLayerCount")
    int dih$getActiveLayerCount();

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] dih$getLayers();
}
