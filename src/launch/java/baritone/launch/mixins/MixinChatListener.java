/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.launch.mixins;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.ChatReceivedEvent;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads every message that arrives from the server and republishes it on Baritone's event bus
 * as a {@link ChatReceivedEvent}.
 *
 * <p>Three entry points are needed for full coverage: signed player chat, "disguised" chat
 * (what most plugin-driven servers send), and system messages. All three are observe-only —
 * nothing here cancels or rewrites a message.
 */
@Mixin(ChatListener.class)
public class MixinChatListener {

    @Inject(method = "handlePlayerChatMessage", at = @At("HEAD"))
    private void baritone$onPlayerChat(PlayerChatMessage message, GameProfile profile, ChatType.Bound bound, CallbackInfo ci) {
        baritone$dispatch(ChatReceivedEvent.Type.PLAYER, profile == null ? null : profile.name(), message.signedContent());
    }

    @Inject(method = "handleDisguisedChatMessage", at = @At("HEAD"))
    private void baritone$onDisguisedChat(Component message, ChatType.Bound bound, CallbackInfo ci) {
        String sender = null;
        try {
            if (bound != null && bound.name() != null) {
                sender = bound.name().getString();
            }
        } catch (Exception ignored) {
            // Server sent something odd in the name field; treat it as unattributed.
        }
        baritone$dispatch(ChatReceivedEvent.Type.DISGUISED, sender, message == null ? "" : message.getString());
    }

    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void baritone$onSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
        if (overlay) {
            return; // action bar spam, not chat
        }
        baritone$dispatch(ChatReceivedEvent.Type.SYSTEM, null, message == null ? "" : message.getString());
    }

    private static void baritone$dispatch(ChatReceivedEvent.Type type, String sender, String text) {
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone == null) {
                return;
            }
            baritone.getGameEventHandler().onReceiveChatMessage(new ChatReceivedEvent(type, sender, baritone$strip(text)));
        } catch (Exception e) {
            // A listener blowing up must never take the chat HUD down with it.
            e.printStackTrace();
        }
    }

    /** Drops legacy section-sign colour codes so the model sees words, not escape soup. */
    private static String baritone$strip(String text) {
        return text == null ? "" : text.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }
}
