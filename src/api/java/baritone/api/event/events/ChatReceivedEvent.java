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

package baritone.api.event.events;

/**
 * A message that arrived <i>from</i> the server, as opposed to {@link ChatEvent} which is a
 * message the local player is <i>sending</i>.
 *
 * <p>Fired for player chat, plugin-rewritten ("disguised") chat, and system messages. The AI
 * listens to these; the {@link #sender} is the only thing separating an instruction from a
 * stranger shouting at you, so it is kept separate from the text rather than parsed back out
 * of it.
 */
public final class ChatReceivedEvent {

    public enum Type {
        /** A signed message from a real player. {@link #getSender()} is trustworthy. */
        PLAYER,
        /** Chat rewritten by a server plugin. The sender name, if any, is guesswork. */
        DISGUISED,
        /** Server/system output: death messages, command feedback, join/leave, MOTD. */
        SYSTEM
    }

    private final Type type;
    private final String sender;
    private final String text;
    private final long timestamp;

    public ChatReceivedEvent(Type type, String sender, String text) {
        this.type = type;
        this.sender = sender;
        this.text = text == null ? "" : text;
        this.timestamp = System.currentTimeMillis();
    }

    public Type getType() {
        return this.type;
    }

    /**
     * @return The username that sent this, or {@code null} for system messages and for
     * disguised chat we could not attribute.
     */
    public String getSender() {
        return this.sender;
    }

    /**
     * @return The plain text of the message, formatting codes stripped.
     */
    public String getText() {
        return this.text;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    @Override
    public String toString() {
        return this.sender == null ? this.text : "<" + this.sender + "> " + this.text;
    }
}
