package dihclient.mixin;

import dihclient.modules.NameCensorModule;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class DihChatComponentCensorMixin {
    @ModifyVariable(method = "addClientSystemMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component dih$censorClientSystemChat(Component component) {
        return NameCensorModule.censorComponent(component);
    }

    @ModifyVariable(method = "addServerSystemMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component dih$censorServerSystemChat(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }

    @ModifyVariable(method = "addPlayerMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component dih$censorPlayerChat(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private Component dih$censorPrivateChatFallback(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }
}
