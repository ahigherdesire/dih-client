package dihclient.mixin.accessor;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
//? if >=26.3 {
/*import net.minecraft.client.resources.model.geometry.ItemQuads;
import org.spongepowered.asm.mixin.gen.Accessor;
*///?}

/** 26.3 keeps an item layer's quads as a private ItemQuads (26.2 hands out a mutable list). Empty on 26.2. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface DihItemLayerQuadsAccessor {
    //? if >=26.3 {
    /*@Accessor("quads")
    ItemQuads dih$getQuads();
    *///?}
}
