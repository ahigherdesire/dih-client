package dihclient.compat;

//? if <26.1 {
/*import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

// Minecraft 1.21.11 only: 26.1 members of ChatComponent, injected by Loom at compile time
// (src/compat1211/resources/fabric.mod.json) and added at runtime by DihChatCompatMixin.
public interface ChatCompat {
    default void addServerSystemMessage(Component message) {
        ((ChatComponent) (Object) this).addMessage(message);
    }

    default void addClientSystemMessage(Component message) {
        ((ChatComponent) (Object) this).addMessage(message);
    }

    default void addPlayerMessage(Component message, MessageSignature signature, GuiMessageTag tag) {
        ((ChatComponent) (Object) this).addMessage(message, signature, tag);
    }
}
*///?} else {
/** Minecraft 1.21.11 only (see the other branch). */
public interface ChatCompat {
}
//?}
