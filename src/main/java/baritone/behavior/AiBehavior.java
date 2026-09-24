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

package baritone.behavior;

import baritone.Baritone;
import baritone.ai.AiBrain;
import baritone.ai.AiConfig;
import baritone.ai.AiMemory;
import baritone.api.event.events.ChatReceivedEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import net.minecraft.client.player.LocalPlayer;

/**
 * Wires the chat firehose into {@link AiBrain} and gives the brain a heartbeat.
 *
 * <p>The interesting decision is made here: a message is an <i>instruction</i> only if it came
 * from a signed player chat packet sent by someone on the trust list. System messages and
 * plugin chat can be spoofed by any server or player, so they are forwarded as context only.
 */
public final class AiBehavior extends Behavior implements AbstractGameEventListener {

    private AiConfig config;
    private AiMemory memory;
    private AiBrain brain;

    public AiBehavior(Baritone baritone) {
        super(baritone);
    }

    public AiConfig getConfig() {
        ensureLoaded();
        return this.config;
    }

    public AiMemory getMemory() {
        ensureLoaded();
        return this.memory;
    }

    public AiBrain getBrain() {
        ensureLoaded();
        return this.brain;
    }

    private synchronized void ensureLoaded() {
        if (this.brain != null) {
            return;
        }
        this.config = AiConfig.load(this.baritone.getDirectory().resolve("ai.json"));
        this.memory = new AiMemory(this.baritone.getDirectory().resolve("ai_memory.json"));
        this.brain = new AiBrain(this.baritone, this.config, this.memory);
    }

    @Override
    public void onReceiveChatMessage(ChatReceivedEvent event) {
        ensureLoaded();
        String text = event.getText();
        if (text == null || text.isBlank()) {
            return;
        }

        String sender = event.getSender();
        if (sender != null && sender.equalsIgnoreCase(ownName())) {
            return; // our own words coming back to us
        }

        // Only a signed player message proves who sent it. Everything else is hearsay.
        boolean trusted = event.getType() == ChatReceivedEvent.Type.PLAYER
                && this.config.isTrusted(sender);

        this.brain.onChat(sender, text, trusted);
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            return;
        }
        ensureLoaded();
        this.brain.tick();
    }

    private String ownName() {
        LocalPlayer player = this.ctx.player();
        if (player == null) {
            return "";
        }
        try {
            return player.getGameProfile().name();
        } catch (Exception e) {
            return "";
        }
    }
}
