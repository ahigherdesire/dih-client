package dihclient.mixin.accessor;

import net.minecraft.client.CommandHistory;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface DihChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> dih$getAllMessages();

    @Accessor("commandHistory")
    CommandHistory dih$getCommandHistory();

    @Invoker("refreshTrimmedMessages")
    void dih$refreshTrimmedMessages();
}
