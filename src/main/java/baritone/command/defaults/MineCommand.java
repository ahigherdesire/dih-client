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

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.ForBlockOptionalMeta;
import baritone.api.command.exception.CommandException;
import baritone.api.utils.BlockOptionalMeta;
import com.google.common.collect.ImmutableMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class MineCommand extends Command {

    /**
     * Since MC 1.17 every overworld ore generates in two forms: a stone variant and a
     * deepslate variant (y < 0). BlockOptionalMeta only wraps a single Block, so asking
     * Baritone to mine "diamond_ore" previously missed every deepslate_diamond_ore block —
     * exactly the layer where most diamonds actually spawn. This map lets us transparently
     * expand a single ore name to both variants before passing the filter to MineProcess.
     * The mapping is symmetric: specifying either variant includes the other automatically.
     */
    private static final Map<Block, Block> ORE_COUNTERPARTS = ImmutableMap.<Block, Block>builder()
            .put(Blocks.COAL_ORE,               Blocks.DEEPSLATE_COAL_ORE)
            .put(Blocks.DEEPSLATE_COAL_ORE,     Blocks.COAL_ORE)
            .put(Blocks.IRON_ORE,               Blocks.DEEPSLATE_IRON_ORE)
            .put(Blocks.DEEPSLATE_IRON_ORE,     Blocks.IRON_ORE)
            .put(Blocks.COPPER_ORE,             Blocks.DEEPSLATE_COPPER_ORE)
            .put(Blocks.DEEPSLATE_COPPER_ORE,   Blocks.COPPER_ORE)
            .put(Blocks.GOLD_ORE,               Blocks.DEEPSLATE_GOLD_ORE)
            .put(Blocks.DEEPSLATE_GOLD_ORE,     Blocks.GOLD_ORE)
            .put(Blocks.LAPIS_ORE,              Blocks.DEEPSLATE_LAPIS_ORE)
            .put(Blocks.DEEPSLATE_LAPIS_ORE,    Blocks.LAPIS_ORE)
            .put(Blocks.REDSTONE_ORE,           Blocks.DEEPSLATE_REDSTONE_ORE)
            .put(Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.REDSTONE_ORE)
            .put(Blocks.EMERALD_ORE,            Blocks.DEEPSLATE_EMERALD_ORE)
            .put(Blocks.DEEPSLATE_EMERALD_ORE,  Blocks.EMERALD_ORE)
            .put(Blocks.DIAMOND_ORE,            Blocks.DEEPSLATE_DIAMOND_ORE)
            .put(Blocks.DEEPSLATE_DIAMOND_ORE,  Blocks.DIAMOND_ORE)
            .build();

    public MineCommand(IBaritone baritone) {
        super(baritone, "mine");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        int quantity = args.getAsOrDefault(Integer.class, 0);
        args.requireMin(1);
        List<BlockOptionalMeta> boms = new ArrayList<>();
        while (args.hasAny()) {
            boms.add(args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE));
        }

        // Expand each ore BOM to include its stone↔deepslate counterpart so that
        // "#mine diamond_ore" also finds deepslate_diamond_ore (and vice-versa).
        // Without this, the whole deepslate layer (y < 0) is silently skipped.
        List<BlockOptionalMeta> expanded = new ArrayList<>(boms);
        for (BlockOptionalMeta bom : boms) {
            Block counterpart = ORE_COUNTERPARTS.get(bom.getBlock());
            if (counterpart != null) {
                boolean alreadyPresent = expanded.stream().anyMatch(b -> b.getBlock() == counterpart);
                if (!alreadyPresent) {
                    expanded.add(new BlockOptionalMeta(counterpart));
                }
            }
        }

        BaritoneAPI.getProvider().getWorldScanner().repack(ctx);
        logDirect(String.format("Mining %s", expanded.toString()));
        baritone.getMineProcess().mine(quantity, expanded.toArray(new BlockOptionalMeta[0]));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        args.getAsOrDefault(Integer.class, 0);
        while (args.has(2)) {
            args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
        }
        return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
    }

    @Override
    public String getShortDesc() {
        return "Mine some blocks";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The mine command allows you to tell Baritone to search for and mine individual blocks.",
                "",
                "The specified blocks can be ores, or any other block.",
                "",
                "Stone and deepslate ore variants are automatically paired: specifying either",
                "form (e.g. diamond_ore or deepslate_diamond_ore) will find both.",
                "",
                "Also see the legitMine settings (see #set l legitMine).",
                "",
                "Usage:",
                "> mine diamond_ore - Mines all diamonds it can find (both stone and deepslate)."
        );
    }
}
