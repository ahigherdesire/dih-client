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
import net.minecraft.ChatFormatting;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #esp} — toggle the block/player ESP outlines drawn by
 * {@link baritone.behavior.EspBehavior}.
 *
 * <pre>
 *   #esp                toggle both block + player ESP together
 *   #esp blocks         toggle block ESP only
 *   #esp players        toggle player ESP only
 *   #esp on / off       both on / both off
 *   #esp range &lt;N&gt;      set the block-scan half-extent (blocks)
 *   #esp list a,b,c     set the highlighted-block list (registry-path substrings)
 *   #esp status         show current state
 * </pre>
 */
public class EspCommand extends Command {

    public EspCommand(IBaritone baritone) {
        super(baritone, "esp");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            // Toggle both together, following whichever is currently off.
            boolean anyOn = Baritone.settings().espBlocks.value || Baritone.settings().espPlayers.value;
            boolean target = !anyOn;
            Baritone.settings().espBlocks.value = target;
            Baritone.settings().espPlayers.value = target;
            logDirect("ESP " + (target ? "ON" : "OFF") + " (blocks + players).", ChatFormatting.AQUA);
            return;
        }

        String sub = args.getString().toLowerCase();
        switch (sub) {
            case "blocks", "block", "b" -> {
                boolean v = !Baritone.settings().espBlocks.value;
                Baritone.settings().espBlocks.value = v;
                logDirect("Block ESP " + (v ? "ON" : "OFF")
                        + (v ? " — highlighting: " + Baritone.settings().espBlockList.value : ""), ChatFormatting.AQUA);
            }
            case "players", "player", "p" -> {
                boolean v = !Baritone.settings().espPlayers.value;
                Baritone.settings().espPlayers.value = v;
                logDirect("Player ESP " + (v ? "ON" : "OFF"), ChatFormatting.AQUA);
            }
            case "items", "item", "i" -> {
                boolean v = !Baritone.settings().espItems.value;
                Baritone.settings().espItems.value = v;
                logDirect("Item ESP " + (v ? "ON" : "OFF"), ChatFormatting.AQUA);
            }
            case "mobs", "mob", "m" -> {
                boolean v = !Baritone.settings().espMobs.value;
                Baritone.settings().espMobs.value = v;
                logDirect("Mob ESP " + (v ? "ON" : "OFF"), ChatFormatting.AQUA);
            }
            case "labels", "label", "l" -> {
                boolean v = !Baritone.settings().espLabels.value;
                Baritone.settings().espLabels.value = v;
                logDirect("ESP labels " + (v ? "ON" : "OFF"), ChatFormatting.AQUA);
            }
            case "tracers", "tracer", "t" -> {
                boolean v = !Baritone.settings().espTracers.value;
                Baritone.settings().espTracers.value = v;
                logDirect("ESP tracers " + (v ? "ON" : "OFF"), ChatFormatting.AQUA);
            }
            case "on", "all", "true" -> {
                Baritone.settings().espBlocks.value = true;
                Baritone.settings().espPlayers.value = true;
                Baritone.settings().espItems.value = true;
                Baritone.settings().espMobs.value = true;
                logDirect("ESP ON (blocks + players + items + mobs).", ChatFormatting.AQUA);
            }
            case "off", "none", "false" -> {
                Baritone.settings().espBlocks.value = false;
                Baritone.settings().espPlayers.value = false;
                Baritone.settings().espItems.value = false;
                Baritone.settings().espMobs.value = false;
                logDirect("ESP OFF.", ChatFormatting.AQUA);
            }
            case "range" -> {
                if (!args.hasAny()) {
                    throw new CommandInvalidStateException("Usage: #esp range <blocks>");
                }
                int r = args.getAs(Integer.class);
                if (r < 1 || r > 128) {
                    logDirect("Range must be between 1 and 128 (big values lag — the scan is a cube).");
                    return;
                }
                Baritone.settings().espBlockRange.value = r;
                logDirect("Block ESP scan range set to " + r + " blocks.", ChatFormatting.AQUA);
            }
            case "list" -> {
                if (!args.hasAny()) {
                    throw new CommandInvalidStateException("Usage: #esp list <block,substrings>");
                }
                String rest = args.rawRest().trim();
                Baritone.settings().espBlockList.value = rest;
                logDirect("Block ESP now highlighting: " + rest, ChatFormatting.AQUA);
            }
            case "status", "s" -> printStatus();
            default -> throw new CommandInvalidStateException(
                    "Unknown argument '" + sub + "'. Try blocks/players/items/mobs/labels/tracers/on/off/range/list/status.");
        }
    }

    private void printStatus() {
        logDirect("Block ESP:  " + (Baritone.settings().espBlocks.value ? "ON" : "off")
                + "  (range " + Baritone.settings().espBlockRange.value
                + ", limit " + Baritone.settings().espBlockLimit.value + ")");
        logDirect("  list: " + Baritone.settings().espBlockList.value);
        logDirect("Player ESP: " + (Baritone.settings().espPlayers.value ? "ON" : "off")
                + "  (range " + Baritone.settings().espPlayerRange.value + ")");
        logDirect("Item ESP:   " + (Baritone.settings().espItems.value ? "ON" : "off")
                + "  (range " + Baritone.settings().espItemRange.value + ")");
        logDirect("Mob ESP:    " + (Baritone.settings().espMobs.value ? "ON" : "off")
                + "  (range " + Baritone.settings().espMobRange.value + ")");
        logDirect("Labels: " + Baritone.settings().espLabels.value
                + " | Tracers: " + Baritone.settings().espTracers.value
                + " | See-through: " + Baritone.settings().espIgnoreDepth.value);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String p = "";
            try { p = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String pf = p;
            return Stream.of("blocks", "players", "items", "mobs", "labels", "tracers", "on", "off", "range", "list", "status").filter(s -> s.startsWith(pf));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Toggle block/player ESP outlines";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Draws wireframe boxes around nearby blocks and/or other players.",
                "",
                "Block ESP scans a cube around you every espBlockRescanTicks ticks and",
                "outlines any block whose registry path matches an entry in espBlockList",
                "(substring match — 'ore' matches every ore, 'diamond_ore' just diamond).",
                "",
                "Player ESP draws the vanilla glowing outline (a see-through silhouette,",
                "not a box) on every other player within espPlayerRange. Item and mob ESP",
                "draw boxes around dropped items and hostile mobs. Labels (name + distance,",
                "item counts) and view tracers apply across the entity categories.",
                "",
                "Usage:",
                "> #esp              - toggle blocks + players together",
                "> #esp blocks       - toggle block ESP",
                "> #esp players      - toggle player ESP (glow outline)",
                "> #esp items        - toggle dropped-item ESP",
                "> #esp mobs         - toggle hostile-mob ESP",
                "> #esp labels       - toggle name/distance labels",
                "> #esp tracers      - toggle view tracers to entities",
                "> #esp on / off     - all categories on / off",
                "> #esp range <N>    - block-scan half-extent (default 24)",
                "> #esp list a,b,c   - set which blocks to highlight",
                "> #esp status       - show current state",
                "",
                "Colors/width/ranges via #set: colorEspBlock, colorEspPlayer, colorEspItem,",
                "colorEspMob, espLineWidthPixels, espItemRange, espMobRange, espIgnoreDepth."
        );
    }
}
