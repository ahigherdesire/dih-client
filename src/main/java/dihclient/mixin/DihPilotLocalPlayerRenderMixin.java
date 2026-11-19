package dihclient.mixin;

import dihclient.util.multi.MultiPilot;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
//? if >=26.2 {
import net.minecraft.client.renderer.extract.LevelExtractor;
//?} else {
/*import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
*///?}
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >=26.2 {
@Mixin(LevelExtractor.class)
//?} else {
/*@Mixin(LevelRenderer.class)
*///?}
public class DihPilotLocalPlayerRenderMixin {
    //? if >=26.2 {
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
    //?} else {
    /*// 26.1: entity visibility is EntityRenderDispatcher.shouldRender, called from LevelRenderer.
    @Redirect(
        method = "extractVisibleEntities(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
    private boolean dih$useBotObservedEntityPosition(EntityRenderDispatcher dispatcher, Entity entity, Frustum frustum,
                                                        double cameraX, double cameraY, double cameraZ) {
        Boolean visible = MultiPilot.isEntityVisibleFromTruth(entity, frustum, cameraX, cameraY, cameraZ);
        return visible != null ? visible : dispatcher.shouldRender(entity, frustum, cameraX, cameraY, cameraZ);
    }
    *///?}

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
