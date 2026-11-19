package dihclient.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link dihclient.compat.ChatCompat} to ChatComponent at runtime (1.21.11 only; skipped elsewhere). */
@Mixin(net.minecraft.client.gui.components.ChatComponent.class)
public abstract class DihChatCompatMixin implements dihclient.compat.ChatCompat {
}
