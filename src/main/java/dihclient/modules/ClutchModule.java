package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.RegistryListSetting;
import dihclient.util.DihEntities;
import dihclient.util.DihFaceScan;
import dihclient.util.DihHandArbiter;
import dihclient.util.DihInventoryHelper;
import dihclient.util.DihPlacementTick;
import dihclient.util.DihRotationUtil;
import dihclient.util.RegistryListCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Saves you from falling into the void (or off a big drop) by placing blocks back under you
 * against a nearby solid face, one block per tick, through the vanilla use-item path.
 * NoFall covers fall damage; this covers falls you would not survive at all.
 */
public final class ClutchModule extends Module {
    private static final String MODE_VOID = "Void";
    private static final String MODE_BIG_FALLS = "Big Falls";
    private static final double FALLING_SPEED = 0.08;

    private int airTicks;
    private int placedThisFall;
    private boolean hitThisFall;
    private int originalSlot = -1;
    private String cachedFilterRaw;
    private Set<Block> cachedFilterBlocks = Set.of();

    ClutchModule() {
        super("clutch", "Clutch", ModuleCategory.MOVEMENT,
            "Places blocks under you when you fall toward the void.");

        add(new ChoiceSetting("mode", "Mode", MODE_VOID, MODE_VOID, MODE_BIG_FALLS)
            .group("General")
            .description("Void: only when nothing is below. Big Falls: also when the ground is too far down.").build());
        add(new IntSetting("fall-height", "Fall Height", 8, 3, 64, 1)
            .group("General")
            .visibleWhen(() -> MODE_BIG_FALLS.equals(choice("mode")))
            .description("Clutch when no ground is within this many blocks below.").build());

        add(new IntSetting("air-window", "Air Window", 20, 2, 100, 1)
            .group("Trigger").description("Only clutch within this many ticks of leaving the ground.").build());
        add(new BoolSetting("only-hit", "Only When Hit", false)
            .group("Trigger").description("Only clutch falls that start with you taking a hit (knockback).").build());
        add(new IntSetting("max-blocks", "Max Blocks", 4, 1, 16, 1)
            .group("Trigger").description("Most blocks placed in one fall.").build());
        add(new DoubleSetting("trigger-speed", "Trigger Speed", FALLING_SPEED, 0.0, 1.0, 0.01)
            .group("Trigger").description("Waits until you fall faster than this, in blocks per tick").build());

        add(new BoolSetting("rotate", "Rotate", true)
            .group("Placing").description("Turn to look at the face being clicked.").build());
        add(new BoolSetting("switch-back", "Switch Back", true)
            .group("Placing").description("Return to the held slot after landing.").build());
        add(new BoolSetting("prefer-held", "Prefer Held", true)
            .group("Placing").description("Uses the block in your hand before switching to a bigger stack").build());
        add(new IntSetting("search-radius", "Search Radius", 2, 1, 3, 1)
            .group("Placing").unit("blocks").description("How far sideways it looks for a cell to fill").build());

        add(new ChoiceSetting("filter-mode", "Filter", "Off", "Off", "Whitelist", "Blacklist")
            .group("Blocks").description("Limits which blocks it may use").build());
        add(RegistryListSetting.placeableBlocks("blocks", "Blocks")
            .group("Blocks").description("Blocks the filter allows or skips")
            .visibleWhen(() -> !"Off".equals(choice("filter-mode"))).build());
    }

    @Override
    protected void onOptionValueChanged(String settingId) {
        if ("blocks".equals(settingId)) cachedFilterRaw = null;
    }

    @Override
    public void onDisable() {
        switchBack();
        resetFall();
    }

    @Override
    public void onGameLeft() {
        originalSlot = -1;
        resetFall();
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        ClientLevel level = MC.level;
        if (player == null || level == null || MC.gameMode == null) return;

        if (player.onGround() || player.isInWater() || player.isInLava() || player.isPassenger()
            || player.getAbilities().flying || player.isFallFlying()) {
            switchBack();
            resetFall();
            return;
        }

        airTicks++;
        if (player.hurtTime > 0) hitThisFall = true;
        if (airTicks > integer("air-window")) {
            switchBack();
            return;
        }
        if (player.getDeltaMovement().y > -Math.max(0.0, decimal("trigger-speed"))) return;
        if (bool("only-hit") && !hitThisFall) return;
        if (placedThisFall >= integer("max-blocks")) return;
        if (!inDanger(player, level)) return;
        if (DihBlinkManager.holdsActionsWithoutMovement()) return;

        int slot = blockSlot(player.getInventory());
        if (slot < 0) return;
        Placement placement = findPlacement(player, level);
        if (placement == null) return;

        if (!DihHandArbiter.beginHandPacketGroup(id())) return;
        try {
            if (!DihPlacementTick.claim(id())) return;
            int selected = player.getInventory().getSelectedSlot();
            if (slot != selected) {
                if (originalSlot < 0) originalSlot = selected;
                DihInventoryHelper.selectHotbarSlot(MC, slot);
            }
            if (bool("rotate")) {
                DihRotationUtil.Rotation look = DihRotationUtil.lookingAt(placement.hit().getLocation(), player.getEyePosition());
                player.setYRot(look.yaw());
                player.setXRot(look.pitch());
            }
            InteractionResult result = MC.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, placement.hit());
            if (result instanceof InteractionResult.Success success) {
                if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
                    DihEntities.swing(player, InteractionHand.MAIN_HAND);
                }
                placedThisFall++;
            }
        } finally {
            DihHandArbiter.endHandPacketGroup(id());
        }
    }

    /** True when no part of the player's footprint has ground within the configured depth. */
    private boolean inDanger(LocalPlayer player, ClientLevel level) {
        int feetY = Mth.floor(player.getY());
        int bottom = MODE_VOID.equals(choice("mode"))
            ? level.getMinY()
            : Math.max(level.getMinY(), feetY - integer("fall-height"));
        AABB box = player.getBoundingBox();
        double[][] columns = {
            {player.getX(), player.getZ()},
            {box.minX + 0.05, box.minZ + 0.05}, {box.maxX - 0.05, box.minZ + 0.05},
            {box.minX + 0.05, box.maxZ - 0.05}, {box.maxX - 0.05, box.maxZ - 0.05}
        };
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (double[] column : columns) {
            int x = Mth.floor(column[0]);
            int z = Mth.floor(column[1]);
            for (int y = feetY - 1; y >= bottom; y--) {
                cursor.set(x, y, z);
                if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) return false;
            }
        }
        return true;
    }

    /** The free cell nearest the player's column, just below the feet, that has a clickable support face. */
    private Placement findPlacement(LocalPlayer player, ClientLevel level) {
        Vec3 eye = player.getEyePosition();
        double reachSq = player.blockInteractionRange() * player.blockInteractionRange();
        AABB body = player.getBoundingBox();
        int baseX = Mth.floor(player.getX());
        int baseZ = Mth.floor(player.getZ());
        int baseY = Mth.floor(player.getY()) - 1;
        boolean sneaking = player.isShiftKeyDown();
        int radius = Math.max(1, integer("search-radius"));

        Placement best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dy = 0; dy >= -1; dy--) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos cell = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    if (!level.getBlockState(cell).canBeReplaced()) continue;
                    if (new AABB(cell).intersects(body)) continue;
                    for (Direction toSupport : Direction.values()) {
                        BlockPos support = cell.relative(toSupport);
                        BlockState supportState = level.getBlockState(support);
                        if (!DihFaceScan.isPlaceableSupport(supportState, support, sneaking)) continue;
                        Direction face = toSupport.getOpposite();
                        Vec3 hitPoint = Vec3.atCenterOf(support).add(
                            face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                        if (eye.distanceToSqr(hitPoint) > reachSq) continue;
                        double cx = cell.getX() + 0.5 - player.getX();
                        double cz = cell.getZ() + 0.5 - player.getZ();
                        double score = cx * cx + cz * cz + (dy < 0 ? 0.75 : 0.0);
                        if (score < bestScore) {
                            bestScore = score;
                            best = new Placement(cell, new BlockHitResult(hitPoint, face, support, false));
                        }
                    }
                }
            }
        }
        return best;
    }

    /** A hotbar slot with a full, non-falling block; the held slot wins, then the biggest stack. */
    private int blockSlot(Inventory inventory) {
        int selected = inventory.getSelectedSlot();
        boolean preferHeld = bool("prefer-held");
        int best = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (DihHandArbiter.slotReserved(slot, id())) continue;
            ItemStack stack = inventory.getItem(slot);
            if (!isClutchBlock(stack) || !filterAllows(stack)) continue;
            if (preferHeld && slot == selected) return slot;
            if (best < 0 || stack.getCount() > inventory.getItem(best).getCount()) best = slot;
        }
        return best;
    }

    private static boolean isClutchBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (block instanceof FallingBlock || block instanceof EntityBlock) return false;
        return block.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private boolean filterAllows(ItemStack stack) {
        String mode = choice("filter-mode");
        if ("Off".equals(mode)) return true;
        boolean listed = filteredBlocks().contains(((BlockItem) stack.getItem()).getBlock());
        return "Whitelist".equals(mode) ? listed : !listed;
    }

    private Set<Block> filteredBlocks() {
        String raw = value("blocks");
        if (raw.equals(cachedFilterRaw)) return cachedFilterBlocks;
        Set<Block> blocks = new HashSet<>();
        for (String entry : list("blocks")) {
            Identifier id = Identifier.tryParse(RegistryListCodec.normalizeId(entry));
            if (id != null) BuiltInRegistries.BLOCK.getOptional(id).ifPresent(blocks::add);
        }
        cachedFilterRaw = raw;
        cachedFilterBlocks = Set.copyOf(blocks);
        return cachedFilterBlocks;
    }

    private void switchBack() {
        if (originalSlot < 0 || MC.player == null) {
            originalSlot = -1;
            return;
        }
        if (bool("switch-back") && DihHandArbiter.beginHandPacketGroup(id())) {
            try {
                DihInventoryHelper.selectHotbarSlot(MC, originalSlot);
            } finally {
                DihHandArbiter.endHandPacketGroup(id());
            }
        }
        originalSlot = -1;
    }

    private void resetFall() {
        airTicks = 0;
        placedThisFall = 0;
        hitThisFall = false;
    }

    private record Placement(BlockPos cell, BlockHitResult hit) {
    }
}
