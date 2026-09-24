package dihclient.mixin;

import dihclient.modules.PackHideState;
import net.minecraft.client.renderer.Panorama;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Panorama.class)
public abstract class DihPanoramaOverlayMixin {
    @Unique
    private static final Identifier DIH_PANORAMA_OVERLAY =
        Identifier.fromNamespaceAndPath("dihclient", "textures/gui/title/background/panorama_overlay.png");

    @ModifyArg(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIII)V"
        ),
        index = 1
    )
    private Identifier dih$swapOverlay(Identifier original) {
        if (dihclient.util.DihMenuPrefs.vanillaMenuVisuals()) return original;

        return dihclient.util.DihThemeTextures.panoramaOverlay(DIH_PANORAMA_OVERLAY);
    }
}
