package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class DihNoRenderEntityRendererMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$hideEntity(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                   //? if >=26.3 {
                                   /*float partialTick,
                                   *///?}
                                   CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) return;
        if (NoRenderState.noFallingBlocks() && entity instanceof FallingBlockEntity) {
            cir.setReturnValue(false);
            return;
        }
        if (NoRenderState.noEntity(entity)) cir.setReturnValue(false);
    }

    @Inject(method = "getNameTag", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noNametag(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (NoRenderState.noNametags()) cir.setReturnValue(null);
    }
}
