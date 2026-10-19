package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.RangeSetting;
import dihclient.api.module.ValueRange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Random;

public final class SafeWalkModule extends Module {
    private static final Minecraft MC = Minecraft.getInstance();

    private static final double DROP_PROBE_DEPTH = 0.55D;

    private static final double PROBE_INSET = 0.05D;

    static final double MIN_EDGE_DISTANCE = 0.15D;

    static double safeEdgeDistance(double configured) {
        return Math.max(MIN_EDGE_DISTANCE, configured);
    }

    private static final int RELEASE_CLEAR_TICKS = 3;

    private final Random random = new Random();
    private long holdUntilMs;
    private boolean holding;
    private int clearTicks;

    public SafeWalkModule() {
        super("safe-walk", "SafeWalk", ModuleCategory.MOVEMENT, "Sneaks at ledges.");
        add(new IntSetting("look-down", "Look Down", 25, 0, 90, 1)
            .group("General")
            .unit("deg")
            .description("Minimum downward look")
            .build());
        add(new DoubleSetting("edge-distance", "Edge Distance", 0.30D, MIN_EDGE_DISTANCE, 1.50D, 0.05D)
            .group("General")
            .unit("blocks")
            .description("Ledge probe distance")
            .build());
        add(new IntSetting("min-drop", "Min Drop", 1, 1, 6, 1)
            .group("General")
            .unit("blocks")
            .description("Only stops you at drops at least this deep, so you can walk down steps")
            .build());
        add(new RangeSetting("hold-time", "Hold Time", new ValueRange(100, 200), 0, 500, 5)
            .group("General")
            .unit("ms")
            .description("Random sneak hold time")
            .build());

        add(new BoolSetting("pause-when-jumping", "Pause When Jumping", false)
            .group("Conditions")
            .description("Lets go while you hold jump, so you can leap off the edge")
            .build());
        add(new BoolSetting("only-backwards", "Only Backwards", false)
            .group("Conditions")
            .description("Only while you walk backwards, as when bridging")
            .build());
        add(new BoolSetting("only-with-blocks", "Only With Blocks", false)
            .group("Conditions")
            .description("Only while you hold a block in either hand")
            .build());
    }

    @Override
    public void onDisable() {
        holding = false;
        holdUntilMs = 0L;
        clearTicks = 0;
    }

    public static Input modifyMovementInput(ClientInput source, Input original) {
        if (original == null || MC == null || MC.player == null || MC.player.input != source) return original;
        Module module = ModuleRegistry.get("safe-walk");
        if (!(module instanceof SafeWalkModule safeWalk)) return original;
        if (!safeWalk.isEnabled()) {
            safeWalk.holding = false;
            return original;
        }
        if (!safeWalk.sneakRequested(original)) return original;
        if (original.shift()) return original;
        return new Input(original.forward(), original.backward(), original.left(), original.right(),
            original.jump(), true, original.sprint());
    }

    private boolean sneakRequested(Input input) {
        if (scaffoldOwnsTheEdge() || pausedFor(input)) {
            holding = false;
            return false;
        }
        boolean atEdge = conditionsMet();
        long now = System.currentTimeMillis();
        clearTicks = atEdge ? 0 : clearTicks + 1;
        if (holding && now < holdUntilMs) return true;
        if (atEdge) {

            holding = true;
            holdUntilMs = now + holdMillis();
            return true;
        }
        if (holdsThroughFlicker(holding, clearTicks, RELEASE_CLEAR_TICKS)) return true;
        holding = false;
        return false;
    }

    private boolean pausedFor(Input input) {
        if (bool("pause-when-jumping") && input.jump()) return true;
        if (bool("only-backwards") && !(input.backward() && !input.forward())) return true;
        if (bool("only-with-blocks")) {
            LocalPlayer player = MC.player;
            return !(player.getMainHandItem().getItem() instanceof BlockItem)
                && !(player.getOffhandItem().getItem() instanceof BlockItem);
        }
        return false;
    }

    static double dropProbeDepth(int minDrop) {
        return DROP_PROBE_DEPTH + Math.max(0, minDrop - 1);
    }

    static boolean holdsThroughFlicker(boolean holding, int clearTicks, int releaseTicks) {
        return holding && clearTicks < releaseTicks;
    }

    private long holdMillis() {
        ValueRange band = ValueRange.parse(value("hold-time"), new ValueRange(100, 200));
        return Math.round(band.random(random));
    }

    private boolean conditionsMet() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null) return false;
        if (!player.onGround() || player.isPassenger() || player.getAbilities().flying) return false;

        if (player.getXRot() < integer("look-down")) return false;
        return dropAhead(safeEdgeDistance(decimal("edge-distance")));
    }

    private boolean dropAhead(double distance) {
        LocalPlayer player = MC.player;
        Vec3 velocity = player.getDeltaMovement();
        Vec3 direction = velocity.horizontalDistanceSqr() > 1.0E-6D
            ? new Vec3(velocity.x, 0.0D, velocity.z).normalize()
            : Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 probe = player.position().add(direction.scale(distance));

        BlockPos underProbe = BlockPos.containing(probe).below();
        if (!MC.level.isOutsideBuildHeight(underProbe)
            && ScaffoldModule.standableSupportState(
                MC.level.getBlockState(underProbe), MC.level, underProbe, CollisionContext.of(player))) {
            return false;
        }
        double half = Math.max(0.05D, player.getBbWidth() / 2.0D - PROBE_INSET);
        AABB below = new AABB(
            probe.x - half, probe.y - dropProbeDepth(integer("min-drop")), probe.z - half,
            probe.x + half, probe.y, probe.z + half);
        return MC.level.noCollision(player, below);
    }

    private static boolean scaffoldOwnsTheEdge() {
        Module scaffold = ModuleRegistry.get("scaffold");
        return scaffold != null && scaffold.isEnabled();
    }
}
