package dihclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dihclient.modules.ZoomModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class DihZoomCameraMixin {
    @Shadow private boolean isPanoramicMode;

    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float dih$zoom(float fov) {
        return isPanoramicMode ? fov : ZoomModule.applyFov(fov);
    }
}
