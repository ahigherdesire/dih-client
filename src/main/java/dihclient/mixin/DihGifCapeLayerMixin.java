package dihclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dihclient.modules.GifModule;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * GIF module cape: swaps the local player's cape for the current GIF frame. Both reads of
 * {@code skin.cape()} are replaced, so the null check also passes for a player without a cape.
 */
@Mixin(CapeLayer.class)
public abstract class DihGifCapeLayerMixin {
    @ModifyExpressionValue(
        method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/PlayerSkin;cape()Lnet/minecraft/core/ClientAsset$Texture;"),
        require = 0)
    private ClientAsset.Texture dih$gifCape(ClientAsset.Texture original, @Local(argsOnly = true) AvatarRenderState state) {
        ClientAsset.Texture gif = GifModule.capeTexture(state.id);
        return gif != null ? gif : original;
    }
}
