package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.mixin.accessor.DihMultiPlayerGameModeAccessor;
import dihclient.util.DihAutoTool;
import dihclient.util.DihBreakMath;
import dihclient.util.DihKeyMappingBridge;
import dihclient.util.DihPackets;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.player.LocalPlayer;
import dihclient.render.mc.DihRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Click a block once and it is mined by packets in the background while you do anything else: one START is sent,
 * progress is counted here tick by tick, and STOP goes out once the block would be broken. Ported from LiquidBounce's
 * PacketMine (GPL-3.0, CCBlueX) Normal and Immediate modes and its tool switching. The server judges a STOP by the
 * item held at that moment, so the default switches to the best tool only for the STOP packet. Changed here: a break
 * the server ignores is retried instead of leaving the target stuck.
 */
public final class PacketMineModule extends Module {
    /** Ticks a finished target waits for the server to remove the block before mining it again. */
    static final int CONFIRM_TICKS = 10;
    private static final float LINE_WIDTH = 2.0F;

    private static PacketMineModule instance;

    private BlockPos target;
    private Direction face = Direction.UP;
    private float progress;
    private boolean started;
    private boolean finished;
    private int finishedTicks;
    private int cooldown;
    private int previousSlot = -1;
    private boolean attackWasDown;

    public PacketMineModule() {
        super("packet-mine", "PacketMine", ModuleCategory.PLAYER, "Click a block to mine it in the background.");
        add(new ChoiceSetting("mode", "Mode", "Normal", "Normal", "Immediate")
            .description("Normal: STOP when the block is done. Immediate: START and STOP at once, for servers that"
                + " only check the time between them."));
        add(new DoubleSetting("range", "Range", 4.5D, 1.0D, 6.0D, 0.1D).unit("blocks"));
        add(new DoubleSetting("keep-range", "Keep Range", 8.0D, 1.0D, 64.0D, 0.5D).unit("blocks")
            .description("Drop the target past this distance."));
        add(new ChoiceSetting("switch", "Switch", "On Stop", "On Stop", "Always", "Never")
            .description("When to hold the best tool: just for the STOP packet, the whole time, or never."));
        add(new BoolSetting("rotate", "Rotate", false).description("Face the block for the START and STOP packets."));
        add(new DoubleSetting("break-damage", "Break Damage", 1.0D, 0.5D, 1.0D, 0.05D)
            .description("Progress at which STOP is sent; vanilla servers accept from 0.7."));
        add(new IntSetting("post-break-delay", "Post Break Delay", 6, 0, 20, 1).unit("ticks")
            .description("Wait before starting the next block (vanilla waits 5)."));
        add(new ColorSetting("color", "Color", 0x90FFFF00).group("Render"));
        add(new ColorSetting("done-color", "Done Color", 0x9000FF00).group("Render"));
        instance = this;
        ModuleWorldHooks.install(this, this::draw);
    }

    @Override
    public void onDisable() {
        clear(true);
    }

    @Override
    public void onGameLeft() {
        target = null;
        started = false;
    }

    /** Whether PacketMine is on to take blocks from Nuker. */
    static boolean running() {
        PacketMineModule mine = instance;
        return mine != null && mine.isEnabled() && !PackHideState.isActive();
    }

    /** Nuker's next block: taken when nothing is being mined or the last block is done. */
    static void offer(BlockPos pos) {
        PacketMineModule mine = instance;
        if (mine == null || pos == null || pos.equals(mine.target)) return;
        if (mine.target != null && !mine.finished) return;
        mine.clear(false);
        mine.target = pos.immutable();
    }

    /** Whether PacketMine has a block it is still working on. */
    static boolean busy() {
        PacketMineModule mine = instance;
        return mine != null && mine.target != null && !mine.finished;
    }

    @Override
    public boolean onStartDestroyBlock(BlockPos pos, Direction direction) {
        if (MC.player == null || pos == null || MC.player.isCreative()) return false;
        // Holding the button keeps calling this; only a fresh click picks or drops a target.
        if (attackWasDown) return true;
        attackWasDown = true;
        if (pos.equals(target)) {
            clear(true);
        } else if (!MC.level.getBlockState(pos).isAir() && MC.level.getBlockState(pos).getDestroySpeed(MC.level, pos) >= 0.0F) {
            clear(true);
            target = pos.immutable();
            face = direction == null ? Direction.UP : direction;
        }
        return true;
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.getConnection() == null) return;
        attackWasDown = attackWasDown && MC.options != null
            && DihKeyMappingBridge.of(MC.options.keyAttack).dih$isActuallyDown();
        if (cooldown > 0) cooldown--;
        if (target == null) return;

        BlockState state = MC.level.getBlockState(target);
        Vec3 eyes = player.getEyePosition();
        double keep = decimal("keep-range");
        if (state.isAir() || state.getDestroySpeed(MC.level, target) < 0.0F
            || DihBreakMath.distanceSq(MC, eyes, target, state) > keep * keep) {
            clear(!state.isAir());
            return;
        }
        double range = decimal("range");
        if (DihBreakMath.distanceSq(MC, eyes, target, state) > range * range) return;
        face = DihBreakMath.faceToward(eyes, target);

        int bestSlot = "Never".equals(choice("switch")) ? -1 : DihAutoTool.bestToolSlot(MC, state, 9, false);
        ItemStack tool = bestSlot >= 0 ? player.getInventory().getItem(bestSlot) : player.getMainHandItem();

        if (finished) {
            // The server should remove the block shortly; if it does not, mine it again.
            if (++finishedTicks > CONFIRM_TICKS) {
                started = false;
                finished = false;
            }
            return;
        }
        if (!started) {
            if (cooldown > 0) return;
            if ("Always".equals(choice("switch")) && bestSlot >= 0) holdSlot(bestSlot);
            rotate(eyes);
            action(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
            swing();
            started = true;
            progress = 0.0F;
            if ("Immediate".equals(choice("mode"))) action(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK);
            return;
        }
        progress += DihBreakMath.progressPerTick(MC, player, state, target, tool);
        if (progress < decimal("break-damage")) return;
        if ("Immediate".equals(choice("mode"))) {
            // Already sent; count the time out before allowing the next block.
            finish();
            return;
        }
        rotate(eyes);
        boolean swap = "On Stop".equals(choice("switch")) && bestSlot >= 0
            && bestSlot != player.getInventory().getSelectedSlot();
        if (swap) MC.getConnection().send(new ServerboundSetCarriedItemPacket(bestSlot));
        action(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK);
        swing();
        if (swap) MC.getConnection().send(new ServerboundSetCarriedItemPacket(player.getInventory().getSelectedSlot()));
        finish();
    }

    private void finish() {
        finished = true;
        finishedTicks = 0;
        cooldown = integer("post-break-delay");
        restoreSlot();
    }

    private void action(ServerboundPlayerActionPacket.Action action) {
        BlockPos pos = target;
        Direction dir = face;
        if (MC.gameMode instanceof DihMultiPlayerGameModeAccessor accessor) {
            accessor.dih$startPrediction(MC.level, sequence -> new ServerboundPlayerActionPacket(action, pos, dir, sequence));
        } else {
            MC.getConnection().send(new ServerboundPlayerActionPacket(action, pos, dir));
        }
    }

    private void swing() {
        MC.getConnection().send(DihPackets.mainHandSwing());
    }

    private void rotate(Vec3 eyes) {
        if (!bool("rotate")) return;
        float[] rotation = DihBreakMath.rotationTo(eyes, target);
        MC.getConnection().send(new ServerboundMovePlayerPacket.Rot(rotation[0], rotation[1], MC.player.onGround(),
            MC.player.horizontalCollision));
    }

    private void holdSlot(int slot) {
        if (previousSlot < 0) previousSlot = MC.player.getInventory().getSelectedSlot();
        MC.player.getInventory().setSelectedSlot(slot);
    }

    private void restoreSlot() {
        if (previousSlot >= 0 && MC.player != null) MC.player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1;
    }

    /** Forgets the target, telling the server to stop when mining was under way. */
    private void clear(boolean abort) {
        if (abort && started && !finished && target != null && MC.getConnection() != null) {
            MC.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, target, face));
        }
        restoreSlot();
        target = null;
        started = false;
        finished = false;
        progress = 0.0F;
    }

    private void draw(LevelRenderContext context, Vec3 camera) {
        BlockPos pos = target;
        if (pos == null) return;
        double done = Math.min(1.0D, progress / Math.max(0.05D, decimal("break-damage")));
        if (finished) done = 1.0D;
        int color = ModuleWorldHooks.blend(ModuleRenderUtil.color(this, "color", 0x90FFFF00),
            ModuleRenderUtil.color(this, "done-color", 0x9000FF00), done);
        // The box grows from the centre as the block breaks.
        double inset = 0.5D * (1.0D - (started ? done : 0.0D));
        AABB box = new AABB(pos).deflate(Math.min(0.499D, inset)).move(camera.scale(-1.0D));
        AABB outline = new AABB(pos).move(camera.scale(-1.0D));
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.storageEspFillSeeThrough(),
            (pose, buffer) -> ModuleWorldHooks.fillBox(pose, buffer, box, ModuleWorldHooks.withAlpha(color, 0.6D)));
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), DihRenderTypes.storageEspLinesSeeThrough(),
            (pose, buffer) -> ModuleWorldHooks.edges(pose, buffer, outline, color, LINE_WIDTH));
    }
}
