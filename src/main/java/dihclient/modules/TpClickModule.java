package dihclient.modules;

import com.mojang.blaze3d.platform.InputConstants;
import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.KeybindSetting;
import dihclient.util.DihBindUtil;
import dihclient.util.multi.PacketTeleportController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class TpClickModule extends Module {
    private static final int COLOR_VALID = 0xFF46D160;
    private static final int COLOR_ADJUSTED = 0xFFFFB03B;
    private static final int COLOR_INVALID = 0xFFFF3B3B;

    private static final int MAX_CLIMB = 4;

    private static final int SEARCH_HORIZONTAL = 8;
    private static final int SEARCH_DOWN = 2;
    private static final int SEARCH_UP = 8;

    private AABB highlightBox;
    private boolean targetValid;
    private boolean adjustedTarget;
    private Vec3 destination;
    private boolean keyWasDown;

    TpClickModule() {
        super("tp-click", "TpClick", ModuleCategory.PLAYER, "Teleport where you click.");

        add(new IntSetting("reach", "Reach", 50, 10, 250, 5)
            .group("General")
            .description("Maximum click teleport distance.").build());
        add(new KeybindSetting("click-key", "Click Key", DihBindUtil.encodeMouseButton(InputConstants.MOUSE_BUTTON_RIGHT))
            .group("General")
            .description("Button that teleports you.").build());
        add(new BoolSetting("sneak-cancels", "Sneak Cancels", true)
            .group("General")
            .description("Holding sneak turns click teleport off so you can click normally").build());
        add(new BoolSetting("notify-errors", "Notify Errors", true)
            .group("General")
            .description("Says in chat why a teleport could not start").build());

        add(new ChoiceSetting("fluids", "Hit Fluids", "When Riding", "When Riding", "Always", "Never")
            .group("Targeting")
            .description("Whether water and lava stop the aim ray").build());
        add(new IntSetting("max-climb", "Max Climb", MAX_CLIMB, 1, 16, 1).unit("blocks")
            .group("Targeting")
            .description("How far above the clicked block it looks for room to stand").build());
        add(new BoolSetting("adjust-target", "Adjust Target", true)
            .group("Targeting")
            .description("Picks the nearest safe spot when the clicked one has no room").build());
        add(new IntSetting("search-radius", "Search Radius", SEARCH_HORIZONTAL, 1, 16, 1).unit("blocks")
            .group("Targeting")
            .description("How far sideways it looks for a safe spot")
            .visibleWhen(() -> bool("adjust-target")).build());
        add(new IntSetting("search-up", "Search Up", SEARCH_UP, 0, 16, 1).unit("blocks")
            .group("Targeting")
            .description("How far up it looks for a safe spot")
            .visibleWhen(() -> bool("adjust-target")).build());
        add(new IntSetting("search-down", "Search Down", SEARCH_DOWN, 0, 16, 1).unit("blocks")
            .group("Targeting")
            .description("How far down it looks for a safe spot")
            .visibleWhen(() -> bool("adjust-target")).build());

        add(new BoolSetting("block-movement", "Block Movement", true)
            .group("Safety")
            .description("Freeze movement keys while teleporting.").build());
        add(new BoolSetting("no-fall", "No Fall", true)
            .group("Safety")
            .description("Prevent fall damage while teleporting.").build());
        add(new BoolSetting("anti-kick", "Anti Kick", true)
            .group("Safety")
            .description("Prevent floating kicks while airborne.").build());

        add(new BoolSetting("highlight", "Highlight", true)
            .group("Render")
            .description("Outlines where you would land").build());
        add(new ColorSetting("valid-color", "Valid Color", COLOR_VALID)
            .group("Render")
            .description("Box color when the clicked spot is safe")
            .visibleWhen(() -> bool("highlight")).build());
        add(new ColorSetting("adjusted-color", "Adjusted Color", COLOR_ADJUSTED)
            .group("Render")
            .description("Box color when a nearby spot was picked instead")
            .visibleWhen(() -> bool("highlight")).build());
        add(new ColorSetting("invalid-color", "Invalid Color", COLOR_INVALID)
            .group("Render")
            .description("Box color when there is nowhere to stand")
            .visibleWhen(() -> bool("highlight")).build());
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onGameLeft() {
        reset();
    }

    private void reset() {
        highlightBox = null;
        targetValid = false;
        adjustedTarget = false;
        destination = null;
        keyWasDown = false;
    }

    public static boolean blocksMovement() {
        Module module = ModuleRegistry.get("tp-click");
        return module instanceof TpClickModule tpClick && tpClick.isEnabled() && tpClick.bool("block-movement");
    }

    public static boolean noFallActive() {
        Module module = ModuleRegistry.get("tp-click");
        return module instanceof TpClickModule tpClick && tpClick.bool("no-fall");
    }

    public static boolean antiKickActive() {
        Module module = ModuleRegistry.get("tp-click");
        return module instanceof TpClickModule tpClick && tpClick.bool("anti-kick");
    }

    @Override
    public void tick() {
        if (!isEnabled()) {
            reset();
            return;
        }
        if (MC == null || MC.player == null || MC.level == null || PackHideState.isHardLocked()) {
            reset();
            return;
        }

        updateTarget();

        int bind = bindCode();
        boolean down = bind != -1 && DihBindUtil.isBindPressed(MC, bind);
        boolean pressed = down && !keyWasDown;
        keyWasDown = down;
        if (!pressed || !targetValid || destination == null) return;
        if (MC.gui.screen() != null) return;
        if (sneakCancelled()) return;

        String result = PacketTeleportController.executeMain(
            String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", destination.x, destination.y, destination.z));

        if (result != null && !result.startsWith("TP started") && bool("notify-errors")) {
            dihclient.util.DihClientMessaging.sendPrefixed("§e" + result);
        }
    }

    private void updateTarget() {
        highlightBox = null;
        targetValid = false;
        adjustedTarget = false;
        destination = null;

        if (sneakCancelled()) return;

        String fluids = choice("fluids");
        boolean hitFluids = "Always".equals(fluids) || "When Riding".equals(fluids) && MC.player.getVehicle() != null;
        HitResult picked = MC.player.pick(Math.max(10, integer("reach")), 0.0F, hitFluids);
        if (!(picked instanceof BlockHitResult blockHit) || picked.getType() != HitResult.Type.BLOCK) return;

        BlockPos base = blockHit.getBlockPos();
        int maxClimb = Math.max(1, integer("max-climb"));
        for (int climb = 1; climb <= maxClimb; climb++) {
            Vec3 spot = standingSpot(base.above(climb));
            if (spot != null) {
                accept(spot, false);
                return;
            }
        }

        Vec3 nearest = bool("adjust-target") ? nearestViable(base) : null;
        if (nearest != null) {
            accept(nearest, true);
            return;
        }

        highlightBox = new AABB(base).inflate(0.002D);
    }

    private void accept(Vec3 spot, boolean adjusted) {
        destination = spot;
        highlightBox = new AABB(BlockPos.containing(spot).below()).inflate(0.002D);
        targetValid = true;
        adjustedTarget = adjusted;
    }

    private Vec3 nearestViable(BlockPos base) {
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        int radius = Math.max(1, integer("search-radius"));
        int up = Math.max(0, integer("search-up"));
        int down = Math.max(0, integer("search-down"));
        for (int dy = -down; dy <= up; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Vec3 spot = standingSpot(base.offset(dx, dy, dz));
                    if (spot == null) continue;
                    double score = dx * dx + dz * dz + Math.abs(dy) * 4.0;
                    if (score < bestScore) {
                        bestScore = score;
                        best = spot;
                    }
                }
            }
        }
        return best;
    }

    private static Vec3 standingSpot(BlockPos feet) {
        if (!isFree(feet) || !isFree(feet.above()) || !isGroundBelow(feet)) return null;
        return Vec3.atBottomCenterOf(feet);
    }

    private static boolean isFree(BlockPos pos) {
        return MC.level.getBlockState(pos).getCollisionShape(MC.level, pos).isEmpty();
    }

    private static boolean isGroundBelow(BlockPos feet) {
        BlockPos below = feet.below();
        if (!MC.level.getBlockState(below).getCollisionShape(MC.level, below).isEmpty()) return true;

        return MC.player.getVehicle() != null && !MC.level.getFluidState(below).isEmpty();
    }

    private int bindCode() {
        try {
            return Integer.parseInt(value("click-key"));
        } catch (NumberFormatException ignored) {
            return DihBindUtil.encodeMouseButton(InputConstants.MOUSE_BUTTON_RIGHT);
        }
    }

    @Override
    public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
        if (MC != null && MC.options != null && sneakCancelled()) return false;
        return bindCode() == DihBindUtil.encodeMouseButton(InputConstants.MOUSE_BUTTON_RIGHT) && targetValid;
    }

    private boolean sneakCancelled() {
        return bool("sneak-cancels") && MC.options.keyShift.isDown();
    }

    public AABB highlightBox() {
        return bool("highlight") ? highlightBox : null;
    }

    public int highlightColor() {
        if (!targetValid) return ModuleRenderUtil.color(this, "invalid-color", COLOR_INVALID);
        return adjustedTarget ? ModuleRenderUtil.color(this, "adjusted-color", COLOR_ADJUSTED)
            : ModuleRenderUtil.color(this, "valid-color", COLOR_VALID);
    }
}
