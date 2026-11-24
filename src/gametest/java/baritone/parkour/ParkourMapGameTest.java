package baritone.parkour;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.parkour.record.CourseRecorder;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.impl.client.gametest.context.TestSingleplayerContextImpl;
import net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Downloaded parkour maps, run as they are: every world folder in the repository's {@code run/parkour-maps} (or
 * {@code DIH_PARKOUR_MAPS}) is copied into the client's {@code saves} (the game test wipes its run folder on every start)
 * and opened (old worlds are upgraded; the backup prompt is skipped, the world being a copy, and other prompts answered
 * yes), and {@code #parkour} is started where the map puts the player. Each run is reported: jumps made, falls,
 * how far it got from the start, the distinct blocks it stood on, and why it stopped.
 *
 * <p>Maps have no machine-readable finish, so this reports rather than passes or fails a map.
 * {@code -Ddih.parkourMapSeconds} sets how long the bot gets per map (default 120).
 */
@SuppressWarnings("UnstableApiUsage")
public final class ParkourMapGameTest implements FabricClientGameTest {
    private static final String TAG = "[ParkourMapGameTest] ";
    private static final int SECONDS = Integer.getInteger("dih.parkourMapSeconds", 120);

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        List<String> maps = context.computeOnClient(ParkourMapGameTest::maps);
        System.out.println(TAG + maps.size() + " maps: " + maps);
        Path saves = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("saves"));
        for (String id : maps) {
            String line;
            // Opened here (answering every prompt an old map brings up), then handed to Fabric's singleplayer context so
            // it is closed in step with the test's server; leaving any other way deadlocks the test.
            try (TestSingleplayerContext world = open(context, saves, id)) {
                line = runMap(context, world, id);
                context.runOnClient(client -> {
                    if (process().running()) process().stop();
                });
            } catch (RuntimeException | AssertionError e) {
                line = "couldn't run: " + e;
            }
            System.out.println(TAG + id + ": " + line);
        }
        context.setScreen(TitleScreen::new);
    }

    /** Copies the maps into the client's saves; their world ids. */
    private static List<String> maps(Minecraft client) {
        Path saves = client.gameDirectory.toPath().resolve("saves");
        List<String> out = new ArrayList<>();
        Path source = mapsFolder(client.gameDirectory.toPath());
        if (source == null) return out;
        try (Stream<Path> dirs = Files.list(source)) {
            String only = System.getenv("DIH_PARKOUR_MAP_FILTER");
            for (Path dir : dirs.filter(d -> Files.isRegularFile(d.resolve("level.dat")))
                .filter(d -> only == null || only.isBlank() || d.getFileName().toString().contains(only)).sorted().toList()) {
                String id = dir.getFileName().toString();
                copyTree(dir, saves.resolve(id));
                out.add(id);
            }
        } catch (IOException e) {
            System.out.println(TAG + "couldn't copy the maps: " + e);
        }
        return out;
    }

    /** {@code DIH_PARKOUR_MAPS}, else {@code run/parkour-maps} in the repository the game test runs from. */
    static Path mapsFolder(Path gameDir) {
        String env = System.getenv("DIH_PARKOUR_MAPS");
        if (env != null && !env.isBlank() && Files.isDirectory(Path.of(env))) return Path.of(env);
        Path repo = repoRoot(gameDir);
        Path folder = repo == null ? null : repo.resolve("run").resolve("parkour-maps");
        return folder != null && Files.isDirectory(folder) ? folder : null;
    }

    /** The repository root above the game directory: the first folder up with a settings.gradle.kts. */
    static Path repoRoot(Path gameDir) {
        for (Path p = gameDir.toAbsolutePath(); p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("settings.gradle.kts"))) return p;
        }
        return null;
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path f : files.toList()) {
                if (f.getFileName().toString().equals("session.lock")) continue;
                Path target = to.resolve(from.relativize(f).toString());
                if (Files.isDirectory(f)) Files.createDirectories(target);
                else Files.copy(f, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Buttons that go on into a world, best first: skip the backup (the world is a throwaway copy), accept old or
     * experimental data.
     */
    private static final List<String> GO_ON = List.of("i know what i'm doing!", "load", "proceed", "yes", "continue", "ok", "play");

    private static TestSingleplayerContext open(ClientGameTestContext context, Path saves, String id) {
        context.runOnClient(client -> client.createWorldOpenFlows().openWorld(id, () -> {
        }));
        int unknownFor = 0;
        for (int t = 0; t < 20 * 180; t++) {
            String state = context.computeOnClient(ParkourMapGameTest::step);
            if (state.equals("in")) break;
            unknownFor = state.startsWith("unknown ") ? unknownFor + 1 : 0;
            if (unknownFor > 20 * 15) throw new AssertionError("stuck on the screen " + state.substring(8));
            context.waitTick();
        }
        net.minecraft.server.MinecraftServer server = context.computeOnClient(Minecraft::getSingleplayerServer);
        if (server == null) throw new AssertionError("never got into the world");
        return new TestSingleplayerContextImpl(context, new TestWorldSaveImpl(context, saves.resolve(id)), server);
    }

    /**
     * A map that starts with a button (a "Start" sign beside it, say): presses the nearest button within reach that has
     * a sign saying "start" within two blocks, the way a player right-clicks it. "" when there is none.
     */
    private static String pressStartButton(Minecraft client) {
        BlockPos at = client.player.blockPosition();
        BlockPos best = null;
        for (BlockPos p : BlockPos.withinManhattan(at, 6, 4, 6)) {
            if (!(client.level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.ButtonBlock)) continue;
            boolean startSign = false;
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -2, -2), p.offset(2, 2, 2))) {
                if (client.level.getBlockEntity(q) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                    for (net.minecraft.network.chat.Component line : sign.getFrontText().getMessages(false)) {
                        if (line.getString().toLowerCase(Locale.ROOT).contains("start")) startSign = true;
                    }
                }
            }
            if (startSign) {
                best = p.immutable();
                break;
            }
        }
        if (best == null) return "";
        net.minecraft.world.phys.Vec3 centre = net.minecraft.world.phys.Vec3.atCenterOf(best);
        client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, centre);
        var hit = new net.minecraft.world.phys.BlockHitResult(centre, net.minecraft.core.Direction.getApproximateNearest(
            client.player.getEyePosition().subtract(centre)), best, false);
        var result = client.gameMode.useItemOn(client.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        return "pressed the start button at " + best.subtract(at).toShortString() + " (" + result + ")";
    }

    /** One tick of getting into a world: "in", "pressed <button>", "waiting" or "unknown <screen>". */
    private static String step(Minecraft client) {
        Screen screen = client.gui.screen();
        if (client.player != null && client.level != null && screen == null) return "in";
        if (screen == null) return "waiting";
        List<AbstractButton> buttons = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractButton button && button.active && button.visible) buttons.add(button);
        }
        if (buttons.isEmpty()) return "waiting";
        for (String want : GO_ON) {
            for (AbstractButton button : buttons) {
                if (button.getMessage().getString().toLowerCase(Locale.ROOT).equals(want)) {
                    button.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    return "pressed " + button.getMessage().getString();
                }
            }
        }
        // Buttons, but none that goes on (a loading screen's Cancel, or a question we don't know): the caller gives up
        // only if this lasts.
        return "unknown " + screen.getClass().getSimpleName() + " " + buttons.stream().map(b -> b.getMessage().getString()).toList();
    }

    private static String runMap(ClientGameTestContext context, TestSingleplayerContext world, String id) {
        context.waitFor(client -> client.player != null && client.level != null, 20 * 60);
        context.waitTicks(60);
        if (id.contains("endless")) return endless(context, world);
        float spawnYaw = context.computeOnClient(client -> client.player.getYRot());
        System.out.println(TAG + id + " signs near the start: " + context.computeOnClient(ParkourMapGameTest::signs));
        for (int yaw = 0; yaw < 360; yaw += 90) {
            float y = yaw;
            context.runOnClient(client -> {
                client.player.setYRot(y);
                client.player.setXRot(15.0F);
            });
            context.waitTicks(10);
            context.takeScreenshot(id + "-start-" + yaw);
        }
        context.runOnClient(client -> {
            client.player.setYRot(spawnYaw);
            client.player.setXRot(0.0F);
        });
        String pressed = context.computeOnClient(ParkourMapGameTest::pressStartButton);
        if (!pressed.isEmpty()) {
            System.out.println(TAG + id + ": " + pressed);
            // Pressing it turned the player to the button; the map means the course to start the way it spawned facing.
            context.runOnClient(client -> {
                client.player.setYRot(spawnYaw);
                client.player.setXRot(0.0F);
            });
            context.waitTicks(60);
            context.takeScreenshot(id + "-after-start-button");
        }
        Vec3 start = context.computeOnClient(client -> client.player.position());
        String where = context.computeOnClient(client -> client.player.blockPosition().toShortString() + " in " + client.gameMode.getPlayerMode().getName());
        context.runOnClient(client -> CourseRecorder.start(client.player, client.level));
        long t0 = System.currentTimeMillis();
        String started = context.computeOnClient(client -> {
            try {
                return process().follow();
            } catch (RuntimeException e) {
                return "couldn't start: " + e.getMessage();
            }
        });
        double[] furthest = {0.0};
        int ticks = 0;
        while (ticks < 20 * SECONDS) {
            context.waitTicks(10);
            ticks += 10;
            Double d = context.computeOnClient(client -> client.player == null ? null : client.player.position().distanceTo(start));
            if (d == null) break;
            furthest[0] = Math.max(furthest[0], d);
            if (!context.computeOnClient(client -> process().running())) break;
        }
        double seconds = (System.currentTimeMillis() - t0) / 1000.0;
        context.takeScreenshot(id + "-end");
        System.out.println(TAG + id + " where it stopped: " + context.computeOnClient(client -> client.player.blockPosition().toShortString())
            + "; signs there: " + context.computeOnClient(ParkourMapGameTest::signs));
        System.out.println(TAG + id + " blocks to stand on round there (dx dy dz from the feet): " + context.computeOnClient(ParkourMapGameTest::standable));
        return context.computeOnClient(client -> {
            ParkourProcess p = process();
            boolean stillGoing = p.running();
            if (stillGoing) p.stop();
            List<BlockPos> route = CourseRecorder.snapshot().route;
            CourseRecorder.stop();
            int distinct = new HashSet<>(route).size();
            return String.format(Locale.ROOT,
                "started at %s; %s %.0fs: %d jumps, %d falls, %d blocks stood on, got %.1f blocks from the start; %s",
                where, stillGoing ? "still going after" : "stopped after",
                seconds, p.jumps(), p.falls(), distinct, furthest[0], stillGoing ? started : p.status());
        });
    }

    /** The blocks round the Endless start strip (x 5..9, y -1..2, z -2..6), as rows, for working out what's in the way. */
    private static String startStrip(Minecraft client) {
        StringBuilder out = new StringBuilder("player at " + client.player.position() + " on " + client.player.getOnPos().toShortString());
        for (int y = 2; y >= -1; y--) {
            out.append(" | y=").append(y).append(":");
            for (int z = -2; z <= 6; z++) {
                out.append(" z").append(z).append("[");
                for (int x = 5; x <= 9; x++) {
                    var state = client.level.getBlockState(new BlockPos(x, y, z));
                    String name = state.isAir() ? "." : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    out.append(x > 5 ? "," : "").append(name.length() > 10 ? name.substring(0, 10) : name);
                }
                out.append("]");
            }
        }
        return out.toString();
    }

    /** The blocks a player could stand on within 7 blocks across and -5..+3 up of the feet, relative, nearest first. */
    private static String standable(Minecraft client) {
        BlockPos feet = client.player.blockPosition();
        List<String> out = new ArrayList<>();
        for (BlockPos p : BlockPos.withinManhattan(feet.below(), 7, 5, 7)) {
            var state = client.level.getBlockState(p);
            if (state.getCollisionShape(client.level, p).isEmpty()) continue;
            if (!client.level.getBlockState(p.above()).getCollisionShape(client.level, p.above()).isEmpty()) continue;
            if (!client.level.getBlockState(p.above(2)).getCollisionShape(client.level, p.above(2)).isEmpty()) continue;
            BlockPos r = p.subtract(feet.below());
            if (r.getY() > 3) continue;
            out.add(r.getX() + " " + r.getY() + " " + r.getZ() + " " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
            if (out.size() >= 60) break;
        }
        return String.join(" | ", out);
    }

    /** The text of the signs within 24 blocks of the player, nearest first. */
    private static String signs(Minecraft client) {
        BlockPos at = client.player.blockPosition();
        List<String> out = new ArrayList<>();
        for (BlockPos p : BlockPos.withinManhattan(at, 24, 12, 24)) {
            if (client.level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                StringBuilder text = new StringBuilder();
                for (net.minecraft.network.chat.Component line : sign.getFrontText().getMessages(false)) {
                    String l = line.getString().trim();
                    if (!l.isEmpty()) text.append(text.isEmpty() ? "" : " / ").append(l);
                }
                if (!text.isEmpty()) out.add(p.subtract(at).toShortString() + ": " + text);
            }
            if (out.size() >= 20) break;
        }
        return out.isEmpty() ? "none" : String.join(" | ", out);
    }


    /**
     * Endless Procedural Parkour: one run per difficulty. A run starts when the player is on the strip at z = 0 in
     * front of the spawn; the course is built ahead along +z as the player goes, and the run ends with a fall below
     * y = -10. Scored like the map scores it: how far along z the player got.
     */
    private static String endless(ClientGameTestContext context, TestSingleplayerContext world) {
        String[] names = {"Easy", "Normal", "Hard", "Nightmare"};
        List<String> runs = new ArrayList<>();
        for (int d = 0; d < names.length; d++) {
            world.getServer().runCommand("/scoreboard players set @e[tag=scores] difficulty " + d);
            world.getServer().runCommand("/tp @a 7.5 1 0.5 0 0");
            context.waitTicks(40);
            if (d == 0) {
                context.takeScreenshot("endless-start");
                System.out.println(TAG + "endless start: " + context.computeOnClient(ParkourMapGameTest::startStrip));
            }
            context.runOnClient(client -> CourseRecorder.start(client.player, client.level));
            context.runOnClient(client -> process().follow());
            double best = 0.0;
            boolean died = false;
            long t0 = System.currentTimeMillis();
            for (int ticks = 0; ticks < 20 * SECONDS; ticks += 5) {
                context.waitTicks(5);
                Double z = context.computeOnClient(client -> client.player == null || client.player.isDeadOrDying() ? null : client.player.getZ());
                if (z == null) {
                    died = true;
                    break;
                }
                best = Math.max(best, z);
                if (context.computeOnClient(client -> client.player.getY() < -6.0)) {
                    died = true;
                    break;
                }
                if (!context.computeOnClient(client -> process().running())) break;
            }
            double seconds = (System.currentTimeMillis() - t0) / 1000.0;
            boolean fell = died;
            double reached = best;
            String run = context.computeOnClient(client -> {
                ParkourProcess p = process();
                boolean going = p.running();
                if (going) p.stop();
                CourseRecorder.stop();
                return String.format(Locale.ROOT, ": %.0fm in %.0fs, %d jumps, %s", reached, seconds, p.jumps(),
                    fell ? "fell" : going ? "still going" : p.status());
            });
            runs.add(names[d] + run);
            context.takeScreenshot("endless-" + names[d] + "-end");
            // Back to the start for the next difficulty: respawn if dead, end the run as the map does.
            context.runOnClient(client -> {
                if (client.player != null && client.player.isDeadOrDying()) client.player.respawn();
            });
            context.waitTicks(20);
            world.getServer().runCommand("/function system:game_over");
            world.getServer().runCommand("/tp @a 7.5 1 -5.5 0 0");
            context.waitTicks(40);
        }
        return String.join("; ", runs);
    }

    private static ParkourProcess process() {
        return ((Baritone) BaritoneAPI.getProvider().getPrimaryBaritone()).getParkourProcess();
    }
}
