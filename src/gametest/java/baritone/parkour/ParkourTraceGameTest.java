package baritone.parkour;

import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.LevelSnapshot;
import baritone.parkour.sim.PlayerPhysics;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.sim.TickInput;
import baritone.parkour.sim.TraceFixture;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fidelity of the movement simulator against the real client. Each script builds a small course in the air, stands
 * the player on it, and plays per-tick inputs through a scripted {@link ClientInput} with the yaw set every tick. After
 * every tick the real state is compared with {@link PlayerPhysics} stepped from the same start; the run fails at the
 * first tick that drifts more than 1e-6 blocks. Each trace is also written as a fixture
 * ({@code run/parkour-fixtures/<name>.json}) for the unit test to replay without a client.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ParkourTraceGameTest implements FabricClientGameTest {
    private static final String TAG = "[ParkourTraceGameTest] ";
    private static final double TOLERANCE = 1.0E-6;
    private static final int BASE_Y = 120;

    /** One scripted trace: commands that build it (relative to its origin), the start offset and yaw, the inputs. */
    record Script(String name, List<String> build, double sx, double sy, double sz, float yaw, List<TickInput> inputs) {
    }

    private static volatile ScriptedInput scripted;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        List<String> failures = new ArrayList<>();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            world.getServer().runCommand("/gamemode survival @a");
            world.getServer().runCommand("/gamerule doDaylightCycle false");
            world.getServer().runCommand("/effect give @a minecraft:saturation infinite 1 true");
            context.waitTicks(20);
            List<Script> scripts = scripts();
            for (int i = 0; i < scripts.size(); i++) {
                Script script = scripts.get(i);
                String failure = play(context, world, script, new BlockPos(2000 + i * 64, BASE_Y, 0));
                System.out.println(TAG + script.name() + ": " + (failure == null ? "matches" : failure));
                if (failure != null) failures.add(script.name() + ": " + failure);
            }
        }
        context.setScreen(TitleScreen::new);
        if (!failures.isEmpty()) throw new AssertionError(failures.size() + " traces drift:\n  " + String.join("\n  ", failures));
    }

    private static String play(ClientGameTestContext context, TestSingleplayerContext world, Script script, BlockPos origin) {
        world.getServer().runCommand("/forceload add " + (origin.getX() - 32) + " " + (origin.getZ() - 32) + " " + (origin.getX() + 32) + " " + (origin.getZ() + 32));
        for (String command : script.build()) {
            world.getServer().runCommand("/execute positioned " + origin.getX() + " " + origin.getY() + " " + origin.getZ() + " run " + command);
        }
        double sx = origin.getX() + script.sx(), sy = origin.getY() + script.sy(), sz = origin.getZ() + script.sz();
        world.getServer().runCommand("/tp @a " + sx + " " + sy + " " + sz + " " + script.yaw() + " 0");
        context.waitTicks(10);
        world.getServer().runCommand("/tp @a " + sx + " " + sy + " " + sz + " " + script.yaw() + " 0");
        context.waitFor(client -> client.player.onGround() && client.level.getBlockState(client.player.blockPosition()) != null, 100);
        context.waitTicks(25);

        List<double[]> real = new ArrayList<>();
        PlayerState[] start = new PlayerState[1];
        BlockSource[] snapshot = new BlockSource[1];
        context.runOnClient(client -> {
            LocalPlayer player = client.player;
            snapshot[0] = LevelSnapshot.around(client.level, origin, 30, 8, 12);
            start[0] = stateOf(player);
            ScriptedInput input = new ScriptedInput(script.inputs());
            input.original = player.input;
            player.input = input;
            scripted = input;
        });
        context.waitTicks(script.inputs().size() + 1);
        context.runOnClient(client -> {
            ScriptedInput input = scripted;
            client.player.input = input.original;
            scripted = null;
            real.addAll(input.recorded);
        });

        // Replay through the simulator and compare tick by tick.
        PlayerState sim = start[0];
        String failure = null;
        List<double[]> expected = new ArrayList<>();
        for (int tick = 0; tick < script.inputs().size() && tick < real.size(); tick++) {
            sim = PlayerPhysics.step(sim, script.inputs().get(tick), snapshot[0]);
            double[] r = real.get(tick);
            expected.add(r);
            double drift = Math.max(Math.abs(r[0] - sim.x), Math.max(Math.abs(r[1] - sim.y), Math.abs(r[2] - sim.z)));
            if (failure == null && (drift > TOLERANCE || (r[6] == 1) != sim.onGround)) {
                failure = String.format(java.util.Locale.ROOT, "tick %d drift %.3g: real (%.6f %.6f %.6f v %.6f %.6f %.6f%s) sim %s",
                    tick + 1, drift, r[0], r[1], r[2], r[3], r[4], r[5], r[6] == 1 ? " ground" : "", sim);
            }
        }
        if (real.size() < script.inputs().size()) failure = "only " + real.size() + " of " + script.inputs().size() + " ticks recorded";
        writeFixture(script, snapshot[0], origin, start[0], expected);
        return failure;
    }

    private static void writeFixture(Script script, BlockSource snapshot, BlockPos origin, PlayerState start, List<double[]> expected) {
        try {
            List<Map.Entry<BlockPos, BlockState>> blocks = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-30, -8, -30), origin.offset(30, 12, 30))) {
                BlockState state = snapshot.get(pos);
                if (!state.isAir()) blocks.add(Map.entry(pos.immutable(), state));
            }
            TraceFixture fixture = new TraceFixture(script.name(), blocks, start, script.inputs(), expected);
            Path dir = Path.of("parkour-fixtures");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(script.name() + ".json"), fixture.toJson());
        } catch (Exception e) {
            System.out.println(TAG + "couldn't write the fixture for " + script.name() + ": " + e);
        }
    }

    /** The simulator's view of the live player before the first scripted tick. */
    static PlayerState stateOf(LocalPlayer player) {
        PlayerState s = new PlayerState();
        s.x = player.getX();
        s.y = player.getY();
        s.z = player.getZ();
        s.vx = player.getDeltaMovement().x;
        s.vy = player.getDeltaMovement().y;
        s.vz = player.getDeltaMovement().z;
        s.yaw = player.getYRot();
        s.onGround = player.onGround();
        s.horizontalCollision = player.horizontalCollision;
        s.minorHorizontalCollision = player.minorHorizontalCollision;
        s.verticalCollision = player.verticalCollision;
        s.sprinting = player.isSprinting();
        s.crouching = player.isCrouching();
        s.crouchPose = player.getPose() == net.minecraft.world.entity.Pose.CROUCHING;
        s.fallDistance = player.fallDistance;
        s.noJumpDelay = readInt(LivingEntity.class, player, "noJumpDelay");
        s.support = readSupport(player);
        s.onGroundNoBlocks = s.onGround && s.support == null;
        s.prevJump = player.input.keyPresses.jump();
        s.prevSneak = player.input.keyPresses.shift();
        s.prevForwardImpulse = player.input.getMoveVector().y;
        return s;
    }

    @SuppressWarnings("unchecked")
    private static BlockPos readSupport(Entity entity) {
        try {
            Field field = Entity.class.getDeclaredField("mainSupportingBlockPos");
            field.setAccessible(true);
            return ((Optional<BlockPos>) field.get(entity)).orElse(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static int readInt(Class<?> owner, Object target, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Feeds one scripted input per tick and records the player's state after each tick. */
    static final class ScriptedInput extends ClientInput {
        final List<TickInput> inputs;
        final List<double[]> recorded = new ArrayList<>();
        ClientInput original;
        int index;
        boolean pending;

        ScriptedInput(List<TickInput> inputs) {
            this.inputs = inputs;
        }

        @Override
        public void tick() {
            if (index >= inputs.size()) {
                keyPresses = Input.EMPTY;
                moveVector = Vec2.ZERO;
                return;
            }
            TickInput in = inputs.get(index);
            keyPresses = new Input(in.forward(), in.back(), in.left(), in.right(), in.jump(), in.sneak(), in.sprint());
            float forward = in.forward() == in.back() ? 0.0F : in.forward() ? 1.0F : -1.0F;
            float left = in.left() == in.right() ? 0.0F : in.left() ? 1.0F : -1.0F;
            moveVector = new Vec2(left, forward).normalized();
            pending = true;
        }
    }

    static {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            ScriptedInput input = scripted;
            if (input == null || client.player == null || input.index >= input.inputs.size()) return;
            float yaw = input.inputs.get(input.index).yaw();
            client.player.setYRot(yaw);
            client.player.yRotO = yaw;
            client.player.setXRot(0.0F);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ScriptedInput input = scripted;
            if (input == null || client.player == null || !input.pending) return;
            input.pending = false;
            input.index++;
            LocalPlayer p = client.player;
            input.recorded.add(new double[]{p.getX(), p.getY(), p.getZ(), p.getDeltaMovement().x, p.getDeltaMovement().y,
                p.getDeltaMovement().z, p.onGround() ? 1 : 0});
        });
    }

    // ---------------------------------------------------------------- the scripts

    private static List<TickInput> repeat(TickInput input, int times) {
        List<TickInput> out = new ArrayList<>();
        for (int i = 0; i < times; i++) out.add(input);
        return out;
    }

    @SafeVarargs
    private static List<TickInput> seq(List<TickInput>... parts) {
        List<TickInput> out = new ArrayList<>();
        for (List<TickInput> part : parts) out.addAll(part);
        return out;
    }

    private static TickInput keys(String keys, float yaw) {
        return TickInput.parse(keys + " @" + yaw);
    }

    static List<Script> scripts() {
        String floor = "fill ~-20 ~-1 ~-20 ~20 ~-1 ~20 minecraft:stone";
        List<Script> out = new ArrayList<>();
        // yaw 0 faces +Z.
        out.add(new Script("walk", List.of(floor), 0.5, 0, -10.5, 0.0F, repeat(keys("W", 0.0F), 40)));
        out.add(new Script("sprint", List.of(floor), 0.5, 0, -10.5, 0.0F, repeat(keys("W R", 0.0F), 40)));
        out.add(new Script("standing-jump", List.of(floor), 0.5, 0, 0.5, 0.0F,
            seq(repeat(keys("J", 0.0F), 1), repeat(keys("", 0.0F), 20))));
        out.add(new Script("sprint-jumps", List.of(floor), 0.5, 0, -15.5, 0.0F,
            seq(repeat(keys("W R", 0.0F), 6), repeat(keys("W J R", 0.0F), 30), repeat(keys("W R", 0.0F), 6))));
        out.add(new Script("strafe-45-jump", List.of(floor), 0.5, 0, -15.5, 45.0F,
            seq(repeat(keys("WD R", 45.0F), 8), repeat(keys("WD J R", 45.0F), 14), repeat(keys("", 45.0F), 10))));
        out.add(new Script("turning-in-air", List.of(floor), 0.5, 0, -10.5, 0.0F,
            seq(repeat(keys("W R", 0.0F), 6), repeat(keys("W J R", 30.0F), 4), repeat(keys("W R", 70.0F), 8), repeat(keys("W R", -40.0F), 8))));
        out.add(new Script("wall-slide", List.of(floor, "fill ~3 ~ ~-10 ~3 ~3 ~10 minecraft:stone"), 0.5, 0, -8.5, 20.0F,
            seq(repeat(keys("WD R", 20.0F), 30), repeat(keys("WD J R", 20.0F), 12))));
        out.add(new Script("head-hitter", List.of(floor, "fill ~-3 ~2 ~2 ~3 ~2 ~6 minecraft:stone"), 0.5, 0, -2.5, 0.0F,
            seq(repeat(keys("W R", 4.0F), 4), repeat(keys("W J R", 0.0F), 20))));
        out.add(new Script("gap-3-jump", List.of(floor, "fill ~-2 ~-1 ~2 ~2 ~-1 ~4 minecraft:air"), 0.5, 0, -4.5, 0.0F,
            seq(repeat(keys("W R", 0.0F), 6), repeat(keys("W J R", 0.0F), 3), repeat(keys("W R", 0.0F), 14))));
        out.add(new Script("up-one", List.of(floor, "fill ~-2 ~ ~3 ~2 ~ ~8 minecraft:stone"), 0.5, 0, -1.5, 0.0F,
            seq(repeat(keys("W R", 0.0F), 4), repeat(keys("W J R", 0.0F), 4), repeat(keys("W R", 0.0F), 14))));
        out.add(new Script("drop-and-sneak", List.of("fill ~-3 ~-1 ~-3 ~3 ~-1 ~3 minecraft:stone", "fill ~-10 ~-4 ~-10 ~10 ~-4 ~10 minecraft:stone"),
            0.5, 0, 0.5, 0.0F, seq(repeat(keys("W C", 0.0F), 30), repeat(keys("W", 0.0F), 20))));
        out.add(new Script("ladder", List.of(floor, "fill ~-1 ~ ~1 ~1 ~8 ~1 minecraft:stone", "fill ~0 ~ ~0 ~0 ~8 ~0 minecraft:ladder[facing=north]"),
            0.5, 0, -1.5, 0.0F, seq(repeat(keys("W", 0.0F), 30), repeat(keys("S J", 0.0F), 6), repeat(keys("", 0.0F), 20))));
        out.add(new Script("ice", List.of("fill ~-20 ~-1 ~-20 ~20 ~-1 ~20 minecraft:packed_ice"), 0.5, 0, -15.5, 0.0F,
            seq(repeat(keys("W R", 0.0F), 10), repeat(keys("W J R", 0.0F), 20), repeat(keys("", 0.0F), 20))));
        out.add(new Script("slime", List.of(floor, "fill ~-3 ~-1 ~2 ~3 ~-1 ~6 minecraft:slime_block", "fill ~-3 ~ ~-3 ~3 ~2 ~-1 minecraft:stone"),
            0.5, 3, -1.5, 0.0F, seq(repeat(keys("W", 0.0F), 12), repeat(keys("", 0.0F), 40))));
        out.add(new Script("soul-sand-honey", List.of(floor, "fill ~-2 ~-1 ~0 ~2 ~-1 ~6 minecraft:soul_sand", "fill ~-2 ~-1 ~7 ~2 ~-1 ~12 minecraft:honey_block"),
            0.5, 0, -2.5, 0.0F, seq(repeat(keys("W R", 0.0F), 30), repeat(keys("W J R", 0.0F), 20))));
        out.add(new Script("fences-and-slabs", List.of(floor, "fill ~-2 ~ ~3 ~2 ~ ~3 minecraft:oak_slab", "fill ~-2 ~ ~6 ~2 ~ ~6 minecraft:oak_fence",
            "fill ~-2 ~ ~9 ~2 ~ ~9 minecraft:oak_stairs[facing=south]"), 0.5, 0, 0.5, 0.0F,
            seq(repeat(keys("W", 0.0F), 12), repeat(keys("W J", 0.0F), 30))));
        return out;
    }
}
