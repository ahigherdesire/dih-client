package dihclient.mixin;

import dihclient.modules.ModuleRenderUtil;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class DihLevelRendererEntityMixin {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void dih$espEntityOutline(Entity entity, float partialTickTime, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState state = cir.getReturnValue();
        if (state == null) return;
        dihclient.util.DihServerRotationView.applyLocalPlayerPose(entity, state, partialTickTime);
        dihclient.util.multi.MultiPilot.applyEntityRenderTruth(entity, state, partialTickTime);
        if (ModuleRenderUtil.hasChamsWork()) ModuleRenderUtil.applyChams(entity, state);
        if (!ModuleRenderUtil.hasAnyOutlineWork()) return;

        int itemOutline = ModuleRenderUtil.itemOutlineColorOrZero(entity);
        if (itemOutline != 0) {
            state.outlineColor = itemOutline;
            return;
        }
        int entityOutline = ModuleRenderUtil.entityOutlineColorOrZero(entity);
        if (entityOutline != 0) {
            state.outlineColor = entityOutline;
        }
    }

}
