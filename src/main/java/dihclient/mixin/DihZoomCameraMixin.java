package dihclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dihclient.modules.ZoomModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

//? if >=26.1 {
@Mixin(Camera.class)
public abstract class DihZoomCameraMixin {
    @Shadow private boolean isPanoramicMode;

    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float dih$zoom(float fov) {
        return isPanoramicMode ? fov : ZoomModule.applyFov(fov);
    }
}
//?} else {
/*// 1.21.11: the field of view comes from GameRenderer.getFov.
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class DihZoomCameraMixin {
    @Shadow public abstract boolean isPanoramicMode();

    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private float dih$zoom(float fov) {
        return isPanoramicMode() ? fov : ZoomModule.applyFov(fov);
    }
}
*///?}
