/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.process.MenuClickProcess;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #menu} — inspect and click server plugin GUIs.
 *
 * <p>Server "GUIs" (shops, warp pickers, random-teleport menus) are ordinary
 * chest-style container menus. This command reads them and clicks a slot,
 * optionally running a command first to make the server open the menu.
 *
 * <h2>Subcommands</h2>
 * <pre>
 *   #menu dump                       print every non-empty slot of the open menu
 *   #menu click &lt;matcher&gt;            click a slot in the already-open menu
 *   #menu run &lt;command&gt; &lt;matcher&gt;    send /command, wait for the menu, click
 * </pre>
 *
 * <h2>Matchers</h2>
 * <pre>
 *   item:&lt;id&gt;      registry id, e.g. item:compass   (most reliable)
 *   slot:&lt;n&gt;       literal container-slot index, 0-based
 *   &lt;text&gt;         case-insensitive substring of display name or lore
 * </pre>
 *
 * <p>Prefer {@code item:} where the icon is unique — it survives the plugin
 * reordering or renaming its entries.
 */
public class MenuCommand extends Command {

    public MenuCommand(IBaritone baritone) {
        super(baritone, "menu", "gui");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            throw new CommandInvalidStateException(
                    "Usage: #menu dump | #menu click <matcher> | #menu run <command> <matcher>");
        }

        String first = args.getString().toLowerCase();

        switch (first) {
            case "dump", "d", "ls" -> {
                args.requireMax(0);
                dump();
            }
            case "stop", "cancel" -> {
                args.requireMax(0);
                process().cancel();
                logDirect("Cancelled.");
            }
            case "click", "c" -> {
                if (!args.hasAny()) {
                    throw new CommandInvalidStateException("Usage: #menu click <matcher>");
                }
                if (!menuOpen()) {
                    throw new CommandInvalidStateException("No container menu is open.");
                }
                startWith(null, args.rawRest().trim());
            }
            case "run", "r" -> {
                if (!args.hasAny()) {
                    throw new CommandInvalidStateException("Usage: #menu run <command> <matcher>");
                }
                String command = args.getString();
                if (!args.hasAny()) {
                    throw new CommandInvalidStateException("Usage: #menu run <command> <matcher>");
                }
                startWith(command.startsWith("/") ? command.substring(1) : command, args.rawRest().trim());
            }
            default -> throw new CommandInvalidStateException(
                    "Unknown subcommand \"" + first + "\". Try: dump, click, run, stop");
        }
    }

    /** Parse a {@code >}-separated matcher chain and hand it to the process. */
    private void startWith(String command, String spec) throws CommandInvalidStateException {
        List<MenuClickProcess.Step> steps;
        try {
            steps = MenuClickProcess.parseChain(spec);
        } catch (NumberFormatException e) {
            throw new CommandInvalidStateException("slot: needs a number in \"" + spec + "\"");
        }
        if (steps.isEmpty()) {
            throw new CommandInvalidStateException("No matcher given.");
        }
        process().start(command, steps);
        logDirect(command == null
                ? "Looking for " + steps + " in the open menu..."
                : "Running /" + command + " then clicking " + steps + "...");
    }

    private void dump() throws CommandInvalidStateException {
        if (!menuOpen()) {
            throw new CommandInvalidStateException(
                    "No container menu is open. Open the GUI first, then run #menu dump.");
        }
        AbstractContainerMenu menu = ctx.player().containerMenu;
        int n = MenuClickProcess.foreignSize(menu);

        // MC 26.2: the current screen moved from Minecraft.screen to Gui.screen().
        var screen = net.minecraft.client.Minecraft.getInstance().gui.screen();
        logDirect("Menu: " + n + " container slots"
                + (screen == null ? "" : ", title=\"" + screen.getTitle().getString() + "\""), ChatFormatting.AQUA);

        int shown = 0;
        for (int i = 0; i < n; i++) {
            ItemStack s = menu.slots.get(i).getItem();
            if (s.isEmpty()) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getResourceKey(s.getItem())
                    .map(rk -> rk.identifier().toString())
                    .orElse("?");
            StringBuilder sb = new StringBuilder();
            sb.append(i).append(": ").append(id)
                    .append(" \"").append(s.getHoverName().getString()).append("\"");
            List<Component> lore = s.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
            if (!lore.isEmpty()) {
                sb.append(" lore=");
                for (Component line : lore) {
                    sb.append("[").append(line.getString()).append("]");
                }
            }
            logDirect(sb.toString());
            shown++;
        }
        if (shown == 0) {
            logDirect("(all slots empty — the server may not have sent contents yet)", ChatFormatting.YELLOW);
        }
    }

    private boolean menuOpen() {
        return ctx.player() != null && ctx.player().containerMenu != ctx.player().inventoryMenu;
    }

    private MenuClickProcess process() {
        return ((Baritone) baritone).getMenuClickProcess();
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String prefix = "";
            try {
                prefix = args.peekString().toLowerCase();
            } catch (Exception ignored) {
            }
            final String pf = prefix;
            return Stream.of("dump", "click", "run", "stop").filter(s -> s.startsWith(pf));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Inspect and click server plugin GUIs";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Server GUIs (shops, warp pickers, random-teleport menus) are ordinary chest menus.",
                "This command reads them and clicks a slot for you.",
                "",
                "Usage:",
                "> #menu dump                    - print every non-empty slot of the open menu",
                "> #menu click <matcher>         - click a slot in the already-open menu",
                "> #menu run <command> <matcher> - send /command, wait for the menu, then click",
                "> #menu stop                    - cancel a pending click",
                "",
                "Matchers:",
                "> item:<id>   registry id, e.g. item:compass  (most reliable)",
                "> slot:<n>    literal container-slot index, 0-based",
                "> <text>      substring of the icon's display name or lore",
                "",
                "Examples:",
                "> #menu run warps item:compass",
                "> #menu run warps ocean",
                "> #menu click slot:18",
                "",
                "Always run #menu dump once with the GUI open to see the real names first.",
                "",
                "Alias: #gui"
        );
    }
}
