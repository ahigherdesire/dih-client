package dihclient.schematic;

import baritone.api.BaritoneAPI;
import dihclient.modules.Module;
import dihclient.modules.ModuleRegistry;
import dihclient.modules.SchematicOverlayRenderer;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Schematics end to end in a superflat world: a hut file in the schematics folder is placed through
 * {@link Schematics}, the world is set up with one right block, one wrong block, one wrong-state stair and one extra
 * block, and both the verifier and the drawn overlay must classify all 27 positions correctly. Then the placement is
 * turned a quarter and handed to Baritone's builder: every block must end up where the turned placement wants it.
 * Stair facing is logged rather than required, because Baritone's builder (as for {@code #build}) places
 * directional blocks only with {@code buildIgnoreDirection}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SchematicsGameTest implements FabricClientGameTest {
    private static final String TAG = "[SchematicsGameTest] ";
    private static final String FILE = "dih-gametest-hut.schem";
    private static final int BUILD_TIMEOUT_TICKS = 4 * 60 * 20;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        File file = context.computeOnClient(client -> new File(new File(client.gameDirectory, "schematics"), FILE));
        List<String> log = new ArrayList<>();
        try {
            writeHut(file);
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                context.waitFor(client -> client.player != null && client.level != null);
                world.getServer().runCommand("/difficulty peaceful");
                world.getServer().runCommand("/time set day");
                world.getServer().runCommand("/gamerule doDaylightCycle false");
                world.getServer().runCommand("/gamemode survival @p");
                world.getServer().runCommand("/clear @p");
                context.waitTicks(20);

                BlockPos origin = context.computeOnClient(client -> client.player.blockPosition().offset(3, 0, 3));
                SchematicPlacement placement = context.computeOnClient(client -> {
                    Module module = ModuleRegistry.get("schematics");
                    if (module != null && !module.isEnabled()) module.setEnabled(true);
                    return SchematicActions.place(client, FILE, origin);
                });
                context.waitFor(client -> placement.isLoaded() || !placement.error().isEmpty(), 20 * 20);
                if (!placement.isLoaded()) throw new AssertionError("the hut did not load: " + placement.error());
                log.add("placed: " + SchematicActions.describe(placement));

                // the world: one right block, one wrong block, one wrong state, one extra; the rest missing
                setBlock(world, origin.offset(0, 0, 0), "minecraft:cobblestone");
                setBlock(world, origin.offset(1, 0, 0), "minecraft:dirt");
                setBlock(world, origin.offset(1, 2, 0), "minecraft:oak_stairs[facing=west]");
                setBlock(world, origin.offset(1, 1, 1), "minecraft:glass");
                context.waitTicks(10);

                Map<SchematicVerifier.Mismatch, Integer> expected = Map.of(
                    SchematicVerifier.Mismatch.NONE, 2,
                    SchematicVerifier.Mismatch.MISSING, 22,
                    SchematicVerifier.Mismatch.WRONG_BLOCK, 1,
                    SchematicVerifier.Mismatch.WRONG_STATE, 1,
                    SchematicVerifier.Mismatch.EXTRA, 1);
                SchematicVerifier.Counts counts = context.computeOnClient(client -> SchematicActions.verify(client, placement));
                log.add("verify: " + counts);
                for (Map.Entry<SchematicVerifier.Mismatch, Integer> e : expected.entrySet()) {
                    if (counts.of(e.getKey()) != e.getValue()) {
                        throw new AssertionError("verifier: " + e.getKey() + " is " + counts.of(e.getKey()) + ", not " + e.getValue()
                            + "\n  " + String.join("\n  ", log));
                    }
                }
                // the overlay rebuilds the sections it draws within a few frames; it must agree with the verifier
                context.waitFor(client -> SchematicOverlayRenderer.visibleCounts(placement)
                    .getOrDefault(SchematicVerifier.Mismatch.MISSING, 0) == 22, 10 * 20);
                Map<SchematicVerifier.Mismatch, Integer> drawn = context.computeOnClient(client -> SchematicOverlayRenderer.visibleCounts(placement));
                log.add("overlay: " + drawn);
                for (Map.Entry<SchematicVerifier.Mismatch, Integer> e : expected.entrySet()) {
                    if (!e.getValue().equals(drawn.getOrDefault(e.getKey(), 0))) {
                        throw new AssertionError("overlay: " + e.getKey() + " is " + drawn.get(e.getKey()) + ", not " + e.getValue()
                            + "\n  " + String.join("\n  ", log));
                    }
                }
                context.takeScreenshot("schematic-overlay");

                // a block placed later moves from missing to right once the server's update arrives
                setBlock(world, origin.offset(2, 0, 0), "minecraft:cobblestone");
                context.waitFor(client -> SchematicOverlayRenderer.visibleCounts(placement)
                    .getOrDefault(SchematicVerifier.Mismatch.MISSING, 0) == 21, 10 * 20);

                // saved for the next session
                context.waitTicks(30);
                String saved = Files.readString(new File(dihclient.DihClientAddon.FOLDER, "schematic-placements.json").toPath(), StandardCharsets.UTF_8);
                if (!saved.contains(placement.id()) || !saved.contains(FILE)) {
                    throw new AssertionError("the placement was not saved:\n" + saved);
                }

                // turned a quarter and built by Baritone
                context.runOnClient(client -> {
                    placement.rotate(1);
                    Schematics.get().changed();
                });
                BoundingBox box = context.computeOnClient(client -> placement.bounds());
                world.getServer().runCommand("/fill " + (box.minX() - 2) + " " + box.minY() + " " + (box.minZ() - 2) + " "
                    + (box.maxX() + 2) + " " + (box.maxY() + 2) + " " + (box.maxZ() + 2) + " minecraft:air");
                world.getServer().runCommand("/give @p minecraft:cobblestone 16");
                world.getServer().runCommand("/give @p minecraft:oak_planks 32");
                world.getServer().runCommand("/give @p minecraft:oak_stairs 4");
                context.waitTicks(20);
                log.add("materials: " + context.computeOnClient(client -> SchematicActions.materials(client, placement, 8)));
                // Baritone's builder only counts a stair as placeable in the direction the player happens to face, so
                // like #build it needs buildIgnoreDirection to place directional blocks; their facing is logged, not required
                boolean ignoreDirectionBefore = baritone.api.BaritoneAPI.getSettings().buildIgnoreDirection.value;
                baritone.api.BaritoneAPI.getSettings().buildIgnoreDirection.value = true;
                context.runOnClient(client -> log.add("build: " + SchematicActions.build(placement)));
                try {
                    context.waitTicks(20);
                    context.waitFor(client -> !BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().isActive(),
                        BUILD_TIMEOUT_TICKS);
                } catch (RuntimeException | AssertionError e) {
                    log.add("build wait ended with: " + e);
                } finally {
                    context.runOnClient(client -> BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything());
                    baritone.api.BaritoneAPI.getSettings().buildIgnoreDirection.value = ignoreDirectionBefore;
                    context.takeScreenshot("schematic-built");
                }
                String wrong = world.getServer().computeOnServer(server -> {
                    Level level = server.getLevel(Level.OVERWORLD);
                    List<String> off = new ArrayList<>();
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        for (int y = box.minY(); y <= box.maxY(); y++) {
                            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                                BlockState want = placement.expected(x, y, z);
                                BlockState have = level.getBlockState(new BlockPos(x, y, z));
                                SchematicVerifier.Mismatch m = want == null ? SchematicVerifier.Mismatch.NONE : SchematicVerifier.classify(want, have);
                                if (m == SchematicVerifier.Mismatch.WRONG_STATE) {
                                    log.add("state differs at " + x + "," + y + "," + z + ": want " + BlockStateParser.serialize(want)
                                        + " have " + BlockStateParser.serialize(have));
                                } else if (m != SchematicVerifier.Mismatch.NONE) {
                                    off.add(x + "," + y + "," + z + " want " + BlockStateParser.serialize(want)
                                        + " have " + BlockStateParser.serialize(have));
                                }
                            }
                        }
                    }
                    return String.join("\n    ", off);
                });
                System.out.println(TAG + "\n  " + String.join("\n  ", log));
                if (!wrong.isEmpty()) throw new AssertionError("the turned hut isn't built right:\n    " + wrong);
                context.runOnClient(client -> Schematics.get().remove(placement));
            }
        } catch (java.io.IOException e) {
            throw new AssertionError("could not write the hut file", e);
        } finally {
            file.delete();
        }
        context.setScreen(TitleScreen::new);
    }

    private static void setBlock(TestSingleplayerContext world, BlockPos pos, String state) {
        world.getServer().runCommand("/setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + state);
    }

    /**
     * A 3x3x3 Sponge v2 hut: cobblestone floor, two rings of oak planks with an east-facing oak stair at the top of
     * the north wall, air inside.
     */
    private static void writeHut(File file) throws java.io.IOException {
        file.getParentFile().mkdirs();
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("Version", 2);
        nbt.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        nbt.putShort("Width", (short) 3);
        nbt.putShort("Height", (short) 3);
        nbt.putShort("Length", (short) 3);
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", 0);
        palette.putInt("minecraft:cobblestone", 1);
        palette.putInt("minecraft:oak_planks", 2);
        palette.putInt("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", 3);
        nbt.put("Palette", palette);
        nbt.putInt("PaletteMax", 4);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int y = 0; y < 3; y++) {
            for (int z = 0; z < 3; z++) {
                for (int x = 0; x < 3; x++) {
                    int value;
                    if (y == 0) value = 1;
                    else if (x == 1 && z == 1) value = 0;
                    else if (y == 2 && x == 1 && z == 0) value = 3;
                    else value = 2;
                    data.write(value);
                }
            }
        }
        nbt.putByteArray("BlockData", data.toByteArray());
        NbtIo.writeCompressed(nbt, file.toPath());
    }
}
