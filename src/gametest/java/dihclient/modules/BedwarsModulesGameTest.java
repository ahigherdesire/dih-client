package dihclient.modules;

import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Beta 7d bedwars kit in a real client: BedESP reads a wool + end stone defence, Danger
 * warns about a fireball and with Deflect hits one back, Clutch saves a fall into a hole dug
 * through the bedrock, and GIF animates the HUD image, cape and shield from a generated GIF.
 * Screenshots of each land in the run's screenshots folder.
 */
@SuppressWarnings("UnstableApiUsage")
public final class BedwarsModulesGameTest implements FabricClientGameTest {
    private static final String TAG = "[BedwarsModulesGameTest] ";

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            world.getServer().runCommand("/time set day");
            world.getServer().runCommand("/effect give @a minecraft:resistance infinite 255 true");
            context.waitTicks(40);
            try {
                bedEsp(context, world);
                System.out.println(TAG + "bed-esp ok");
                danger(context, world);
                System.out.println(TAG + "danger ok");
                clutch(context, world);
                System.out.println(TAG + "clutch ok");
                gif(context, world);
                System.out.println(TAG + "gif ok");
            } catch (Throwable t) {
                System.out.println(TAG + "FAILED: " + t);
                throw t;
            } finally {
                context.runOnClient(client -> {
                    for (String id : List.of("bed-esp", "danger", "clutch", "gif")) ModuleRegistry.get(id).setEnabled(false);
                    client.options.setCameraType(CameraType.FIRST_PERSON);
                });
            }
        }
        context.setScreen(TitleScreen::new);
    }

    /** A red bed six blocks east, wrapped in wool and then end stone. */
    private static void bedEsp(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("/gamemode creative @a");
        world.getServer().runCommand("/setblock 6 -60 0 minecraft:red_bed[facing=east,part=foot]");
        world.getServer().runCommand("/setblock 7 -60 0 minecraft:red_bed[facing=east,part=head]");
        world.getServer().runCommand("/fill 5 -60 -1 8 -59 1 minecraft:white_wool replace minecraft:air");
        world.getServer().runCommand("/fill 4 -60 -2 9 -58 2 minecraft:end_stone replace minecraft:air");
        world.getServer().runCommand("/tp @a 0.5 -60 0.5 -90 20");
        context.waitTicks(20);
        context.runOnClient(client -> ModuleRegistry.get("bed-esp").setEnabled(true));
        context.waitTicks(30);

        Object bed = context.computeOnClient(client -> {
            List<?> beds = (List<?>) field(ModuleRegistry.get("bed-esp"), "beds");
            return beds.isEmpty() ? null : beds.get(0);
        });
        if (bed == null) throw new AssertionError("BedESP found no bed");
        String summary = (String) call(bed, "summary");
        int breakTicks = (int) call(bed, "breakTicks");
        int totalTicks = (int) call(bed, "totalTicks");
        int pathBlocks = ((List<?>) call(bed, "path")).size();
        int layerBlocks = ((List<?>) call(bed, "layers")).size();
        System.out.println(TAG + "bed summary '" + summary + "' layers=" + layerBlocks + " path=" + pathBlocks
            + " break=" + breakTicks + " total=" + totalTicks);
        if (!summary.contains("L1 White Wool")) throw new AssertionError("layer 1 should be white wool: " + summary);
        if (!summary.contains("End Stone")) throw new AssertionError("an outer layer should be end stone: " + summary);
        if (pathBlocks < 2 || breakTicks <= 0 || totalTicks <= breakTicks) {
            throw new AssertionError("the way in should break at least end stone + wool, got path=" + pathBlocks
                + " break=" + breakTicks + " total=" + totalTicks);
        }
        context.takeScreenshot("bedwars-bed-esp");
        context.runOnClient(client -> ModuleRegistry.get("bed-esp").setEnabled(false));
    }

    /** A fireball flying straight at the player: warned about first, then hit back with Deflect. */
    private static void danger(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("/tp @a 0.5 -60 30.5 -90 0");
        context.waitTicks(20);
        context.runOnClient(client -> {
            Module danger = ModuleRegistry.get("danger");
            danger.setValue("deflect", "false");
            danger.setEnabled(true);
        });
        summonFireball(world);
        boolean warned = false;
        for (int i = 0; i < 40 && !warned; i++) {
            context.waitTicks(1);
            warned = context.computeOnClient(client -> !ModuleRegistry.get("danger").info().isEmpty());
        }
        if (!warned) throw new AssertionError("Danger never flagged a fireball flying at the player");
        context.takeScreenshot("bedwars-danger-warning");
        context.waitTicks(40);
        world.getServer().runCommand("/kill @e[type=minecraft:fireball]");
        context.waitTicks(5);

        context.runOnClient(client -> {
            ModuleRegistry.get("danger").setValue("deflect", "true");
            client.player.setYRot(0.0F);
        });
        summonFireball(world);
        boolean deflected = false;
        for (int i = 0; i < 80 && !deflected; i++) {
            context.waitTicks(1);
            deflected = world.getServer().computeOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                for (Entity entity : server.getLevel(Level.OVERWORLD).getAllEntities()) {
                    if (entity instanceof AbstractHurtingProjectile fireball
                        && (fireball.getOwner() == player || fireball.getDeltaMovement().x > 0.0D)) return true;
                }
                return false;
            });
        }
        if (!deflected) throw new AssertionError("Deflect Fireballs never hit the fireball back");
        context.takeScreenshot("bedwars-danger-deflect");
        context.waitTicks(20);
        world.getServer().runCommand("/kill @e[type=minecraft:fireball]");
        context.runOnClient(client -> ModuleRegistry.get("danger").setEnabled(false));
    }

    private static void summonFireball(TestSingleplayerContext world) {
        world.getServer().runCommand("/summon minecraft:fireball 10.5 -59.2 30.5 "
            + "{ExplosionPower:0b,acceleration_power:0.02d,Motion:[-0.4d,0.0d,0.0d]}");
    }

    /** A 1x3 hole dug through the bedrock to the void; Clutch has to catch the player in it. */
    private static void clutch(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("/gamemode survival @a");
        world.getServer().runCommand("/fill 41 -64 -1 43 -61 1 minecraft:air");
        world.getServer().runCommand("/clear @a");
        world.getServer().runCommand("/item replace entity @a hotbar.0 with minecraft:wooden_sword");
        world.getServer().runCommand("/item replace entity @a hotbar.3 with minecraft:white_wool 64");
        world.getServer().runCommand("/tp @a 38.5 -60 0.5 -90 30");
        context.waitTicks(20);
        context.runOnClient(client -> {
            dihclient.util.DihInventoryHelper.selectHotbarSlot(client, 0);
            Module clutch = ModuleRegistry.get("clutch");
            clutch.setValue("mode", "Void");
            clutch.setEnabled(true);
        });
        context.waitTicks(5);
        world.getServer().runCommand("/tp @a 41.5 -60 0.5 -90 30");
        context.waitTicks(40);

        double y = context.computeOnClient(client -> client.player.getY());
        int selected = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
        int placed = world.getServer().computeOnServer(server -> {
            int count = 0;
            for (int py = -64; py <= -61; py++) {
                for (int px = 41; px <= 43; px++) {
                    for (int pz = -1; pz <= 1; pz++) {
                        if (server.getLevel(Level.OVERWORLD).getBlockState(new BlockPos(px, py, pz)).getBlock().getDescriptionId().endsWith("white_wool")) count++;
                    }
                }
            }
            return count;
        });
        System.out.println(TAG + "clutch y=" + y + " placed=" + placed + " selected=" + selected);
        context.takeScreenshot("bedwars-clutch");
        if (placed < 1) throw new AssertionError("Clutch placed no wool in the hole");
        if (y < -64.0D) throw new AssertionError("Clutch let the player fall through to y " + y);
        if (selected != 0) throw new AssertionError("Switch Back should return to slot 0, still on " + selected);
        context.runOnClient(client -> ModuleRegistry.get("clutch").setEnabled(false));
    }

    /** A 4-frame 32x16 GIF on the HUD, the cape and the shield. */
    private static void gif(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("/gamemode creative @a");
        world.getServer().runCommand("/tp @a 0.5 -60 -20.5 180 0");
        world.getServer().runCommand("/item replace entity @a weapon.offhand with minecraft:shield");
        Path file = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("dih-test.gif"));
        writeGif(file);
        context.runOnClient(client -> {
            Module gif = ModuleRegistry.get("gif");
            gif.setValue("image", "dih-test.gif");
            gif.setValue("hud", "true");
            gif.setValue("width", "128");
            gif.setValue("anchor", "Top Right");
            gif.setValue("cape", "true");
            gif.setValue("shield", "true");
            gif.setEnabled(true);
        });
        context.waitTicks(10);
        boolean hud = context.computeOnClient(client -> GifModule.shouldRender());
        if (!hud) throw new AssertionError("GIF HUD should render with a valid image");

        Set<Object> capeFrames = new HashSet<>();
        Set<Object> shieldFrames = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            Object cape = context.computeOnClient(client -> GifModule.capeTexture(client.player.getId()));
            Object shield = context.computeOnClient(client -> {
                GifModule.setRenderingOwnItems(true);
                try {
                    return GifModule.shieldFrame();
                } finally {
                    GifModule.setRenderingOwnItems(false);
                }
            });
            if (cape == null) throw new AssertionError("GIF cape returned no texture");
            if (shield == null) throw new AssertionError("GIF shield returned no frame");
            capeFrames.add(cape);
            shieldFrames.add(shield);
            context.waitTicks(3);
        }
        System.out.println(TAG + "gif cape frames=" + capeFrames.size() + " shield frames=" + shieldFrames.size());
        if (capeFrames.size() < 2) throw new AssertionError("the GIF cape should animate, saw " + capeFrames.size() + " frame(s)");
        if (shieldFrames.size() < 2) throw new AssertionError("the GIF shield should animate, saw " + shieldFrames.size() + " frame(s)");

        context.takeScreenshot("bedwars-gif-hud");
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(10);
        context.takeScreenshot("bedwars-gif-cape");
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(10);
        context.takeScreenshot("bedwars-gif-shield");
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
    }

    private static void writeGif(Path file) {
        Color[] colors = {new Color(0xFF3B3B), new Color(0xFFD166), new Color(0x06D6A0), new Color(0x4CC9F0)};
        try {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            ImageTypeSpecifier type = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
            IIOMetadata metadata = writer.getDefaultImageMetadata(type, param);
            String format = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
            IIOMetadataNode control = new IIOMetadataNode("GraphicControlExtension");
            control.setAttribute("disposalMethod", "none");
            control.setAttribute("userInputFlag", "FALSE");
            control.setAttribute("transparentColorFlag", "FALSE");
            control.setAttribute("delayTime", "10");
            control.setAttribute("transparentColorIndex", "0");
            root.appendChild(control);
            metadata.setFromTree(format, root);

            Files.deleteIfExists(file);
            try (ImageOutputStream out = ImageIO.createImageOutputStream(file.toFile())) {
                writer.setOutput(out);
                writer.prepareWriteSequence(null);
                for (int i = 0; i < colors.length; i++) {
                    BufferedImage frame = new BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = frame.createGraphics();
                    g.setColor(colors[i]);
                    g.fillRect(0, 0, 32, 16);
                    g.setColor(Color.WHITE);
                    g.fillRect(i * 8, 0, 8, 16);
                    g.dispose();
                    writer.writeToSequence(new IIOImage(frame, null, metadata), param);
                }
                writer.endWriteSequence();
            }
            writer.dispose();
        } catch (Exception e) {
            throw new AssertionError("could not write the test GIF", e);
        }
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("no field " + name, e);
        }
    }

    private static Object call(Object owner, String name) {
        try {
            Method method = owner.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(owner);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("no method " + name, e);
        }
    }
}
