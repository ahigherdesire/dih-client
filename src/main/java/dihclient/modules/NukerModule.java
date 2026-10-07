package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.RegistryListSetting;
import dihclient.mixin.accessor.DihMultiPlayerGameModeAccessor;
import dihclient.util.DihBreakMath;
import dihclient.util.DihEntities;
import dihclient.util.RegistryListCodec;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import dihclient.render.mc.DihRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Breaks blocks around you. Ported from LiquidBounce's Nuker (GPL-3.0, CCBlueX): Legit mines one block at a time at
 * normal speed (handing it to PacketMine when that is on), Instant sends START and STOP for many blocks a tick. Changed
 * here: Instant only takes blocks that really break in one tick (or anything in creative), so it does not leave ghost
 * blocks; Flatten clears only what is at or above your feet; the block you stand on is left while others remain.
 */
public final class NukerModule extends Module {
    private static final float LINE_WIDTH = 2.0F;

    private static NukerModule instance;

    private BlockPos target;
    private final List<BlockPos> recent = new ArrayList<>();

    public NukerModule() {
        super("nuker", "Nuker", ModuleCategory.PLAYER, "Breaks blocks around you.");
        add(new ChoiceSetting("mode", "Mode", "Legit", "Legit", "Instant")
            .description("Legit: one block at a time at normal speed. Instant: every block that breaks in one tick."));
        add(new ChoiceSetting("area", "Area", "Sphere", "Sphere", "Flatten")
            .description("Sphere: all around you. Flatten: only at or above your feet."));
        add(new DoubleSetting("range", "Range", 4.5D, 1.0D, 6.0D, 0.1D).unit("blocks"));
        add(new IntSetting("per-tick", "Blocks Per Tick", 8, 1, 64, 1)
            .visibleWhen(() -> "Instant".equals(choice("mode"))));
        add(new ChoiceSetting("filter", "Filter", "Off", "Off", "Whitelist", "Blacklist"));
        add(RegistryListSetting.blocks("blocks", "Blocks").visibleWhen(() -> !"Off".equals(choice("filter"))));
        add(new BoolSetting("rotate", "Rotate", false).description("Face each block for the server."));
        add(new BoolSetting("pause-in-containers", "Pause In Containers", true));
        add(new ColorSetting("color", "Color", 0x90FF5050).group("Render"));
        instance = this;
        ModuleWorldHooks.install(this, this::draw);
    }

    @Override
    public void onDisable() {
        if (target != null && MC.gameMode != null && "Legit".equals(choice("mode"))) MC.gameMode.stopDestroyBlock();
        target = null;
        recent.clear();
    }

    @Override
    public void onGameLeft() {
        target = null;
        recent.clear();
    }

    /** Whether Nuker is mining with vanilla progress: then the attack key must not stop or redirect it. */
    public static boolean ownsBreaking() {
        NukerModule nuker = instance;
        return nuker != null && nuker.isEnabled() && nuker.target != null && "Legit".equals(nuker.choice("mode"))
            && !PacketMineModule.running() && !PackHideState.isActive();
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        recent.clear();
        if (player == null || MC.level == null || MC.gameMode == null || MC.getConnection() == null) return;
        if (bool("pause-in-containers") && MC.gui.screen() instanceof AbstractContainerScreen<?>) return;
        if ("Instant".equals(choice("mode"))) {
            tickInstant(player);
        } else {
            tickLegit(player);
        }
    }

    private void tickLegit(LocalPlayer player) {
        Vec3 eyes = player.getEyePosition();
        if (target == null || !valid(player, eyes, target, MC.level.getBlockState(target))) {
            List<BlockPos> found = candidates(player, eyes, false);
            target = found.isEmpty() ? null : found.getFirst();
            if (target != null) rotate(eyes, target);
        }
        if (target == null) return;
        if (PacketMineModule.running()) {
            PacketMineModule.offer(target);
            return;
        }
        Direction face = DihBreakMath.faceToward(eyes, target);
        MC.gameMode.continueDestroyBlock(target, face);
        DihEntities.swing(player, InteractionHand.MAIN_HAND);
    }

    private void tickInstant(LocalPlayer player) {
        Vec3 eyes = player.getEyePosition();
        target = null;
        int budget = integer("per-tick");
        for (BlockPos pos : candidates(player, eyes, true)) {
            if (budget-- <= 0) break;
            rotate(eyes, pos);
            Direction face = DihBreakMath.faceToward(eyes, pos);
            if (MC.gameMode instanceof DihMultiPlayerGameModeAccessor accessor) {
                accessor.dih$startPrediction(MC.level, sequence -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, face, sequence));
                if (!player.isCreative()) {
                    accessor.dih$startPrediction(MC.level, sequence -> new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, face, sequence));
                }
            }
            recent.add(pos);
            target = pos;
        }
        if (target != null) DihEntities.swing(player, InteractionHand.MAIN_HAND);
    }

    /** Breakable blocks in range, nearest first; Instant keeps only those that break in one tick. */
    private List<BlockPos> candidates(LocalPlayer player, Vec3 eyes, boolean instantOnly) {
        double range = decimal("range");
        int r = (int) Math.ceil(range);
        BlockPos center = BlockPos.containing(eyes);
        List<BlockPos> out = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = MC.level.getBlockState(pos);
                    if (!valid(player, eyes, pos, state)) continue;
                    if (instantOnly && !player.isCreative()
                        && DihBreakMath.progressPerTick(MC, player, state, pos, player.getMainHandItem()) < 1.0F) continue;
                    out.add(pos.immutable());
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(eyes)));
        return withoutFloor(out, player.getBoundingBox().move(0.0D, -1.0D, 0.0D));
    }

    /** Leaves out the blocks under your feet unless nothing else is left. */
    static List<BlockPos> withoutFloor(List<BlockPos> sorted, AABB below) {
        List<BlockPos> rest = new ArrayList<>();
        for (BlockPos pos : sorted) {
            if (!below.intersects(new AABB(pos))) rest.add(pos);
        }
        return rest.isEmpty() ? sorted : rest;
    }

    private boolean valid(LocalPlayer player, Vec3 eyes, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getShape(MC.level, pos).isEmpty()) return false;
        if (state.getDestroySpeed(MC.level, pos) < 0.0F && !player.isCreative()) return false;
        if ("Flatten".equals(choice("area")) && pos.getY() < player.getBlockY()) return false;
        double range = decimal("range");
        if (DihBreakMath.distanceSq(MC, eyes, pos, state) > range * range) return false;
        return passesFilter(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    private boolean passesFilter(String id) {
        String filter = choice("filter");
        if ("Off".equals(filter)) return true;
        boolean listed = false;
        for (String entry : list("blocks")) {
            if (RegistryListCodec.matches(id, entry)) {
                listed = true;
                break;
            }
        }
        return "Whitelist".equals(filter) == listed;
    }

    private void rotate(Vec3 eyes, BlockPos pos) {
        if (!bool("rotate")) return;
        float[] rotation = DihBreakMath.rotationTo(eyes, pos);
        MC.getConnection().send(new ServerboundMovePlayerPacket.Rot(rotation[0], rotation[1], MC.player.onGround(),
            MC.player.horizontalCollision));
    }

    private void draw(LevelRenderContext context, Vec3 camera) {
        List<BlockPos> boxes = new ArrayList<>(recent);
        if (target != null && !boxes.contains(target)) boxes.add(target);
        if (boxes.isEmpty()) return;
        int color = ModuleRenderUtil.color(this, "color", 0x90FF5050);
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.storageEspLinesSeeThrough(),
            (pose, buffer) -> {
                for (BlockPos pos : boxes) {
                    ModuleWorldHooks.edges(pose, buffer, new AABB(pos).move(camera.scale(-1.0D)), color, LINE_WIDTH);
                }
            });
    }
}
