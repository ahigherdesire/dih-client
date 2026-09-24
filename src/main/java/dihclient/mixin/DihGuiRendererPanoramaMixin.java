package dihclient.mixin;

import dihclient.modules.PackHideState;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class DihGuiRendererPanoramaMixin {
    @Unique
    private final CubeMap dih$customCubeMap = new CubeMap(
        Identifier.fromNamespaceAndPath("dihclient", "textures/gui/title/background/panorama"));

    @Inject(method = "registerPanoramaTextures", at = @At("TAIL"))
    private void dih$registerCustomPanorama(TextureManager textureManager, CallbackInfo ci) {

        dihclient.util.DihThemeTextures.registerPanorama(textureManager);
    }

    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CubeMap;render(FF)V")
    )
    private void dih$renderPanorama(CubeMap vanillaCubeMap, float rotX, float rotY) {
        boolean custom = !dihclient.util.DihMenuPrefs.vanillaMenuVisuals()
            && dihclient.util.DihThemeTextures.isPanoramaAvailable();
        CubeMap target = custom ? dih$customCubeMap : vanillaCubeMap;
        target.render(rotX, rotY);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void dih$closeCustomPanorama(CallbackInfo ci) {
        dih$customCubeMap.close();
    }
}
