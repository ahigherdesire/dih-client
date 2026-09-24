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

package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.behavior.MineListenerBehavior;
import net.minecraft.ChatFormatting;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #listentomine} — toggle the chat-triggered auto-mine listener.
 *
 * <p>While enabled, {@link MineListenerBehavior} watches incoming chat for
 * {@link baritone.api.Settings#listenToMineWord} (default {@code legendary}) and,
 * on a match, warps, frees an inventory slot, and mines
 * {@link baritone.api.Settings#listenToMineTarget} (default {@code ancient_debris}).
 *
 * <p>Aliases: {@code #ltm}
 */
public class ListenToMineCommand extends Command {

    public ListenToMineCommand(IBaritone baritone) {
        super(baritone, "listentomine", "ltm");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            MineListenerBehavior.enabled = !MineListenerBehavior.enabled;
            announce();
            return;
        }
        String sub = args.getString().toLowerCase();
        switch (sub) {
            case "on", "enable", "true" -> {
                MineListenerBehavior.enabled = true;
                announce();
            }
            case "off", "disable", "false" -> {
                MineListenerBehavior.enabled = false;
                announce();
            }
            case "status", "s" -> printStatus();
            default -> throw new CommandInvalidStateException(
                    "Unknown argument '" + sub + "'. Try on/off/status.");
        }
    }

    private void announce() {
        if (MineListenerBehavior.enabled) {
            logDirect("Listen-to-mine ON — will warp + mine "
                    + Baritone.settings().listenToMineTarget.value + " when \""
                    + Baritone.settings().listenToMineWord.value + "\" is seen in chat.",
                    ChatFormatting.GREEN);
        } else {
            logDirect("Listen-to-mine OFF.", ChatFormatting.YELLOW);
        }
    }

    private void printStatus() {
        logDirect("Listen-to-mine: " + (MineListenerBehavior.enabled ? "ON" : "off"));
        logDirect("  word:   \"" + Baritone.settings().listenToMineWord.value + "\"");
        logDirect("  warp:   " + Baritone.settings().listenToMineWarp.value
                + "  (delay " + Baritone.settings().listenToMineWarpDelay.value + " ticks)");
        logDirect("  mine:   " + Baritone.settings().listenToMineTarget.value);
        logDirect("  redo cooldown: " + Baritone.settings().listenToMineCooldown.value + " ticks");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String p = "";
            try { p = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String pf = p;
            return Stream.of("on", "off", "status").filter(s -> s.startsWith(pf));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Watch chat for a word, then warp + mine";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Watches incoming chat while enabled. When the trigger word (default",
                "\"legendary\") appears in any message, DIH Client:",
                "  1. runs the warp command (default /warp mine)",
                "  2. waits for the teleport to land",
                "  3. drops one non-tool stack if the inventory is full, to free a slot",
                "  4. #mines the target block (default ancient_debris)",
                "",
                "Nothing happens unless this is enabled.",
                "",
                "Usage:",
                "> #listentomine          - toggle on/off",
                "> #listentomine on/off   - explicit",
                "> #listentomine status   - show current settings",
                "",
                "Configure with #set:",
                "> listenToMineWord       trigger word            (default legendary)",
                "> listenToMineWarp       command to run          (default /warp mine)",
                "> listenToMineTarget     block(s) to mine        (default ancient_debris)",
                "> listenToMineWarpDelay  ticks to wait post-warp (default 40)",
                "> listenToMineCooldown   ticks before re-firing  (default 200)",
                "",
                "Aliases: #ltm"
        );
    }
}
