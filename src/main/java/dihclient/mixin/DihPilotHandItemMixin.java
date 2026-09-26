package dihclient.mixin;

import dihclient.util.multi.MultiPilot;
import dihclient.util.DihRemoteView;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemInHandRenderer.class)
/** POV pilot: the piloted bot's arm (owner, skin, sleeves, swing) in first person. */
public class DihPilotHandItemMixin {

    // The arm's owner, skin and swing come from the player on 26.2. 26.3 draws the arm from a PlayerRenderState
    // extracted elsewhere, so there the bot's items and swing are swapped in DihPilotHandStateMixin only.
    //? if <26.3 {
    private static AbstractClientPlayer dih$armOwner(AbstractClientPlayer fallback) {
        Player bot = DihRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot instanceof AbstractClientPlayer clientBot ? clientBot : fallback;
    }

    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private AbstractClientPlayer dih$botArmOwner(AbstractClientPlayer player) {
        return dih$armOwner(player);
    }

    @Redirect(method = "renderPlayerArm",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPlayerRenderer(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;"),
        require = 0)
    private AvatarRenderer<AbstractClientPlayer> dih$botArmRenderer(EntityRenderDispatcher dispatcher,
                                                                        AbstractClientPlayer player) {
        return dispatcher.getPlayerRenderer(dih$armOwner(player));
    }

    @Redirect(method = "renderPlayerArm",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;"),
        require = 0)
    private PlayerSkin dih$botArmSkin(AbstractClientPlayer player) {
        return dih$armOwner(player).getSkin();
    }

    @Redirect(method = "renderPlayerArm",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z"),
        require = 0)
    private boolean dih$botArmSleeve(AbstractClientPlayer player, PlayerModelPart part) {
        return dih$armOwner(player).isModelPartShown(part);
    }

    @Redirect(method = "renderMapHand",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPlayerRenderer(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;"),
        require = 0)
    private AvatarRenderer<AbstractClientPlayer> dih$botMapArmRenderer(EntityRenderDispatcher dispatcher,
                                                                           AbstractClientPlayer player) {
        return dispatcher.getPlayerRenderer(dih$armOwner(player));
    }

    @Redirect(method = "renderMapHand",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;"),
        require = 0)
    private PlayerSkin dih$botMapArmSkin(LocalPlayer player) {
        return dih$armOwner(player).getSkin();
    }

    @Redirect(method = "renderMapHand",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z"),
        require = 0)
    private boolean dih$botMapArmSleeve(LocalPlayer player, PlayerModelPart part) {
        return dih$armOwner(player).isModelPartShown(part);
    }

    @Redirect(method = {"renderOneHandedMap", "renderTwoHandedMap"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isInvisible()Z"),
        require = 0)
    private boolean dih$botMapArmInvisible(LocalPlayer player) {
        return dih$armOwner(player).isInvisible();
    }

    @Redirect(method = "submitHandsWithItems",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackAnim(F)F"),
        require = 0)
    private float dih$botSwing(LocalPlayer player, float partialTicks) {
        Player bot = DihRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getAttackAnim(partialTicks) : player.getAttackAnim(partialTicks);
    }
    //?}
}
