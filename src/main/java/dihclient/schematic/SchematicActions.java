package dihclient.schematic;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.utils.SettingsUtil;
import baritone.utils.schematic.LoadedSchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the {@code .schem} command and the Schematics screens do with a placement, in one place.
 */
public final class SchematicActions {

    private SchematicActions() {}

    /**
     * @return where a new placement goes: four blocks ahead of the player, as Autism's placement wizard starts it
     */
    public static BlockPos defaultOrigin(Player player) {
        return player.blockPosition().relative(player.getDirection(), 4);
    }

    public static String dimension(Minecraft mc) {
        return mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }

    /**
     * Places a file at the player, or at {@code origin} when given.
     *
     * @throws IllegalArgumentException with a message for the player
     */
    public static SchematicPlacement place(Minecraft mc, String file, BlockPos origin) {
        if (mc.player == null || mc.level == null) {
            throw new IllegalArgumentException("Join a world first");
        }
        return Schematics.get().place(file, dimension(mc), origin == null ? defaultOrigin(mc.player) : origin);
    }

    /**
     * @return one line about a placement: name, where, how it is turned, and its load state
     */
    public static String describe(SchematicPlacement p) {
        BlockPos o = p.origin();
        String turn = p.transform().rotation() + "°" + (p.transform().mirror() == SchematicTransform.Mirroring.NONE
            ? "" : " mirror " + p.transform().mirror().id());
        String state;
        if (!p.error().isEmpty()) {
            state = "failed: " + p.error();
        } else if (!p.isLoaded()) {
            state = "loading";
        } else {
            LoadedSchematic s = p.schematic();
            state = p.size().getX() + "x" + p.size().getY() + "x" + p.size().getZ() + ", " + s.blockCount() + " blocks";
        }
        return p.name() + " at " + o.getX() + " " + o.getY() + " " + o.getZ() + ", " + turn + ", " + state
            + (p.visible() ? "" : ", hidden");
    }

    /**
     * @return how many of each item id the player holds
     */
    public static Map<String, Integer> held(Player player) {
        Map<String, Integer> out = new HashMap<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                out.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
        }
        return out;
    }

    /**
     * @return what the placement still needs, against the loaded world and the player's inventory
     */
    public static SchematicMaterials.Report materialsReport(Minecraft mc, SchematicPlacement p) {
        Map<String, Integer> held = held(mc.player);
        return SchematicMaterials.report(p, mc.level, id -> held.getOrDefault(id, 0));
    }

    /**
     * @return the material list against the world and the player's inventory, for chat or a screen
     */
    public static List<String> materials(Minecraft mc, SchematicPlacement p, int maxLines) {
        List<String> out = new ArrayList<>();
        if (!p.isLoaded()) {
            out.add(p.error().isEmpty() ? "Still loading " + p.name() : "Not loaded: " + p.error());
            return out;
        }
        SchematicMaterials.Report report = materialsReport(mc, p);
        if (report.remaining() == 0) {
            out.add(p.name() + " is complete.");
            return out;
        }
        out.add(report.ready()
            ? "Materials ready in inventory for " + report.remaining() + " blocks"
            : "Still needed for " + report.remaining() + " blocks (" + report.missingTotal() + " items short):");
        int shown = 0;
        for (SchematicMaterials.Line line : report.lines()) {
            if (shown == maxLines) {
                out.add("... and " + (report.lines().size() - maxLines) + " more items");
                break;
            }
            String id = line.item().startsWith("minecraft:") ? line.item().substring(10) : line.item();
            out.add(line.missing() > 0
                ? id + ": " + line.missing() + " more needed (" + line.needed() + " total, " + line.have() + " held)"
                : id + ": " + line.needed() + " (held)");
            shown++;
        }
        report.unplaceable().forEach((block, count) -> out.add(block + ": " + count + " (no item places it)"));
        return out;
    }

    /**
     * Saves the material list as a CSV in {@code <schematics folder>/materials}, with the same numbers as
     * {@link #materials}.
     *
     * @return what to tell the player
     * @throws IllegalArgumentException with a message for the player when there is nothing to save
     */
    public static String exportMaterials(Minecraft mc, SchematicPlacement p) {
        if (!p.isLoaded()) {
            throw new IllegalArgumentException(p.error().isEmpty() ? p.name() + " is still loading" : p.name() + " is not loaded: " + p.error());
        }
        SchematicMaterials.Report report = materialsReport(mc, p);
        if (report.remaining() == 0) {
            throw new IllegalArgumentException(p.name() + " is complete, so there is nothing left to list");
        }
        try {
            Path file = SchematicMaterialsCsv.write(Schematics.folder().toPath().resolve("materials"), p.name(),
                SchematicMaterialsCsv.csv(report, SchematicActions::displayName));
            int rows = report.lines().size() + report.unplaceable().size();
            return "Saved " + rows + " items to schematics/materials/" + file.getFileName();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not save the CSV: " + e.getMessage());
        }
    }

    /**
     * @return the in-game name of an item id, or of a block id when no item has it, or the id itself
     */
    public static String displayName(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) {
            return id;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        if (item != null && item != Items.AIR) {
            return new ItemStack(item).getHoverName().getString();
        }
        return BuiltInRegistries.BLOCK.getOptional(key).map(block -> block.getName().getString()).orElse(id);
    }

    /**
     * Drops the placement onto the ground at its column, if that column is loaded.
     *
     * @return whether it moved
     */
    public static boolean ground(Minecraft mc, SchematicPlacement p) {
        BlockPos o = p.origin();
        if (mc.level == null || !mc.level.isLoaded(o)) {
            return false;
        }
        p.moveTo(new BlockPos(o.getX(), mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.getX(), o.getZ()), o.getZ()));
        return true;
    }

    /**
     * @return the placement checked against the loaded world
     */
    public static SchematicVerifier.Counts verify(Minecraft mc, SchematicPlacement p) {
        return SchematicVerifier.verify(p, mc.level);
    }

    /**
     * Hands the placement to Baritone's builder, the one {@code #build} uses.
     *
     * @return what to tell the player
     */
    public static String build(SchematicPlacement p) {
        if (!p.isLoaded()) {
            return p.error().isEmpty() ? p.name() + " is still loading" : p.name() + " is not loaded: " + p.error();
        }
        // Without allowInventory Baritone only places what is already on the hotbar.
        Settings settings = BaritoneAPI.getSettings();
        String inventory = "";
        if (!settings.allowInventory.value) {
            settings.allowInventory.value = true;
            SettingsUtil.save(settings);
            inventory = "; turned on #allowInventory so it uses your whole inventory";
        }
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        PlacementBuildSchematic schematic = new PlacementBuildSchematic(p);
        baritone.getBuilderProcess().build(p.name(), schematic, schematic.origin());
        BlockPos o = schematic.origin();
        return "Building " + p.name() + " from " + o.getX() + " " + o.getY() + " " + o.getZ() + " (#stop to stop" + inventory
            + "; if Baritone pauses on stairs or other turned blocks, #buildIgnoreDirection true lets it place them)";
    }
}
