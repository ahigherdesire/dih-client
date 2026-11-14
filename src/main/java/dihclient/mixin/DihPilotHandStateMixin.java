package dihclient.mixin;

import dihclient.util.DihRemoteView;
import dihclient.util.multi.MultiPilot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * POV pilot: the piloted bot's held items (and on 26.3 its swing) in first person. These live in the hand
 * renderer on 26.2 and in {@code FirstPersonHandsAndItems} on 26.3.
 */
//? if >=26.3 {
/*@Mixin(net.minecraft.client.player.FirstPersonHandsAndItems.class)
*///?} else {
@Mixin(ItemInHandRenderer.class)
//?}
public class DihPilotHandStateMixin {

    @Redirect(method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"),
        require = 0)
    private ItemStack dih$tickMain(LocalPlayer player) {
        Player bot = DihRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getMainHandItem() : player.getMainHandItem();
    }

    @Redirect(method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"),
        require = 0)
    private ItemStack dih$tickOff(LocalPlayer player) {
        Player bot = DihRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getOffhandItem() : player.getOffhandItem();
    }

    @Redirect(method = "evaluateWhichHandsToRender",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"),
        require = 0)
    private static ItemStack dih$evalMain(LocalPlayer player) {
        Player bot = DihRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getMainHandItem() : player.getMainHandItem();
    }

    @Redirect(method = "evaluateWhichHandsToRender",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"),
        require = 0)
    private static ItemStack dih$evalOff(LocalPlayer player) {
        Player bot = DihRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getOffhandItem() : player.getOffhandItem();
    }

    @Redirect(method = "evaluateWhichHandsToRender",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"),
        require = 0)
    private static boolean dih$evalUsing(LocalPlayer player) {
        Player bot = DihRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.isUsingItem() : player.isUsingItem();
    }

    //? if >=26.3 {
    /*@Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getCurrentSwing()Lnet/minecraft/world/entity/LivingEntity$SwingDescription;"),
        require = 0)
    private net.minecraft.world.entity.LivingEntity.SwingDescription dih$botSwing(LocalPlayer player) {
        Player bot = DihRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getCurrentSwing() : player.getCurrentSwing();
    }

    @Redirect(method = "evaluateWhichHandsToRender",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"),
        require = 0)
    private static InteractionHand dih$evalUsedHand(LocalPlayer player) {
        Player bot = DihRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getUsedItemHand() : player.getUsedItemHand();
    }
    *///?} else {
    @Redirect(method = "selectionUsingItemWhileHoldingBowLike",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"),
        require = 0)
    private static InteractionHand dih$bowHand(LocalPlayer player) {
        Player bot = DihRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getUsedItemHand() : player.getUsedItemHand();
    }

    @Redirect(method = "selectionUsingItemWhileHoldingBowLike",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"),
        require = 0)
    private static ItemStack dih$bowOff(LocalPlayer player) {
        Player bot = DihRemoteView.firstPersonPlayer(MultiPilot.pilotedBot());
        return bot != null ? bot.getOffhandItem() : player.getOffhandItem();
    }
    //?}
}
