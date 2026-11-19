package dihclient.mixin;

//? if <26.1 {
/*import dihclient.util.DihPickHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Minecraft 1.21.11: forwards GameRenderer.pick to the pick hooks DihMinecraftClientMixin has on 26.1+ Minecraft.pick.
@Mixin(GameRenderer.class)
public abstract class DihGameRendererPickMixin {
    @Inject(method = "pick", at = @At("HEAD"))
    private void dih$pickHead(float partialTicks, CallbackInfo ci) {
        ((DihPickHooks) Minecraft.getInstance()).dih$pickHead(partialTicks);
    }

    @Inject(method = "pick", at = @At("RETURN"))
    private void dih$pickReturn(float partialTicks, CallbackInfo ci) {
        ((DihPickHooks) Minecraft.getInstance()).dih$pickReturn(partialTicks);
    }
}
*///?} else {
import org.spongepowered.asm.mixin.Mixin;

/** Minecraft 1.21.11 only (see the other branch); empty and skipped on 26.1+. */
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class DihGameRendererPickMixin {
}
//?}
