package dihclient.mixin;

import dihclient.util.multi.MultiPilot;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public class DihPilotLocalPlayerRenderMixin {
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void dih$useBotObservedEntityPosition(Entity entity, Frustum frustum,
                                                     double cameraX, double cameraY, double cameraZ,
                                                     //? if >=26.3 {
                                                     /*float partialTick, long frame,
                                                     *///?}
                                                     CallbackInfoReturnable<Boolean> cir) {
        Boolean visible = MultiPilot.isEntityVisibleFromTruth(entity, frustum, cameraX, cameraY, cameraZ);
        if (visible != null) cir.setReturnValue(visible);
    }

    @Redirect(
        method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;entity()Lnet/minecraft/world/entity/Entity;", ordinal = 3))
    private Entity dih$renderOwnBodyWhilePiloting(Camera camera) {
        if (MultiPilot.isActive() || dihclient.util.DihRemoteView.isActive()) {
            LocalPlayer self = Minecraft.getInstance().player;
            if (self != null) return self;
        }
        return camera.entity();
    }
}
