package dihclient.mixin;

import dihclient.modules.GifModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GIF module shield: first-person hands always hold the local player's items. */
@Mixin(ItemInHandRenderer.class)
public abstract class DihGifOwnHandsMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), require = 0)
    private void dih$gifOwnHandsStart(CallbackInfo ci) {
        GifModule.setRenderingOwnItems(true);
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"), require = 0)
    private void dih$gifOwnHandsEnd(CallbackInfo ci) {
        GifModule.setRenderingOwnItems(false);
    }
}
