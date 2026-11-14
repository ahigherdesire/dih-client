package dihclient.modules;

import dihclient.util.DihRender;
import dihclient.util.DihEntities;
import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.RegistryListSetting;
import dihclient.mixin.accessor.DihMinecraftAccessor;
import dihclient.util.DihHandArbiter;
import dihclient.util.DihPlacementTick;
import dihclient.util.RegistryListCodec;
import dihclient.util.multi.MultiPilot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.function.Predicate;

public final class AirPlaceModule extends Module {
    static final String MODULE_DESCRIPTION = "Places blocks in air.";
    static final String RANGE_TIP = "Set placement reach";
    static final String GUIDE_TIP = "Show placement guide";
    static final String FILL_TIP = "Fill placement guide";
    static final String COLOR_TIP = "Set guide color";
    static final String HAND_TIP = "Hand to place from";
    static final String DELAY_TIP = "Ticks between placements";
    static final String REPEAT_TIP = "Keep placing while held";
    static final String SNEAK_TIP = "Only while sneaking";
    static final String FILTER_TIP = "Limit usable blocks";
    static final String BLOCKS_TIP = "Blocks the filter uses";

    static final int PLACE_DELAY = 4;

    public record Placement(BlockHitResult hit, InteractionHand hand) {}

    private volatile BlockPos renderTarget;
    private boolean useWasDown;

    AirPlaceModule() {
        super("air-place", "AirPlace", ModuleCategory.PLAYER, MODULE_DESCRIPTION);
        add(new DoubleSetting("range", "Range", 5.0D, 1.0D, 6.0D, 0.05D)
            .group("General")
            .description(RANGE_TIP).build());
        add(new ChoiceSetting("hand", "Hand", "Auto", "Auto", "Main Hand", "Off Hand")
            .group("General")
            .description(HAND_TIP).build());
        add(new IntSetting("place-delay", "Place Delay", PLACE_DELAY, 0, 20, 1).unit("ticks")
            .group("General")
            .description(DELAY_TIP).build());
        add(new BoolSetting("repeat", "Repeat", true)
            .group("General")
            .description(REPEAT_TIP).build());
        add(new BoolSetting("sneak-only", "Sneak Only", false)
            .group("General")
            .description(SNEAK_TIP).build());

        add(new ChoiceSetting("block-filter", "Block Filter", "Off", "Off", "Whitelist", "Blacklist")
            .group("Blocks")
            .description(FILTER_TIP).build());
        add(RegistryListSetting.placeableBlocks("blocks", "Blocks", "")
            .group("Blocks")
            .description(BLOCKS_TIP)
            .visibleWhen(() -> !"Off".equals(choice("block-filter"))).build());

        add(new BoolSetting("guide", "Guide", true)
            .group("Render")
            .description(GUIDE_TIP).build());
        add(new BoolSetting("fill", "Fill", true)
            .group("Render")
            .description(FILL_TIP)
            .visibleWhen(() -> bool("guide")).build());
        add(new ColorSetting("guide-color", "Guide Color", 0xFFFF3B3B)
            .group("Render")
            .description(COLOR_TIP)
            .visibleWhen(() -> bool("guide")).build());
    }

    private Placement placementFor(Player player, HitResult hit) {
        if (bool("sneak-only") && (player == null || !player.isShiftKeyDown())) return null;
        return placement(player, hit, decimal("range"), choice("hand"), this::allowedBlock);
    }

    private boolean allowedBlock(ItemStack stack) {
        String filter = choice("block-filter");
        if ("Off".equals(filter)) return true;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        String id = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
        boolean listed = false;
        for (String entry : list("blocks")) {
            if (RegistryListCodec.matches(id, entry)) {
                listed = true;
                break;
            }
        }
        return "Whitelist".equals(filter) == listed;
    }

    @Override
    public void onDisable() {
        renderTarget = null;
    }

    @Override
    public void tick() {
        renderTarget = null;
        if (MC == null || MC.level == null) return;
        Player player = MultiPilot.commandPlayer();
        Placement placement = placementFor(player, MC.hitResult);
        boolean useDown = MC.options != null && MC.options.keyUse.isDown();
        boolean freshPress = useDown && !useWasDown;
        useWasDown = useDown;
        if (placement == null || ModuleRegistry.shouldCancelUseExcept(placement.hit(), placement.hand(), id())) {
            return;
        }
        if (bool("guide")) {
            renderTarget = placement.hit().getBlockPos().immutable();
        }

        if (bool("repeat") || freshPress) executePlacement(placement);
    }

    @Override
    public boolean shouldCancelUse(HitResult hitResult, InteractionHand ignoredHand) {
        if (MC == null || MC.level == null || MC.player == null || MC.gameMode == null || MultiPilot.isActive()) {
            return false;
        }
        Placement placement = placementFor(MC.player, hitResult);
        if (placement == null) return false;
        if (MC.player.isHandsBusy()) return true;
        if (ModuleRegistry.shouldCancelUseExcept(placement.hit(), placement.hand(), id())) return true;

        return true;
    }

    private void executePlacement(Placement placement) {
        if (MC.player == null || MC.gameMode == null || MultiPilot.isActive()) return;

        if (!MC.options.keyUse.isDown()) return;

        if (((DihMinecraftAccessor) MC).dih$getRightClickDelay() > 0) return;

        if (DihBlinkManager.holdsActionsWithoutMovement()) return;

        if (!DihHandArbiter.beginHandPacketGroup(id())) return;
        try {

            if (!DihPlacementTick.claim(id())) return;
            simulateVanillaUse(placement);
            ((DihMinecraftAccessor) MC).dih$setRightClickDelay(Math.max(0, integer("place-delay")));
        } finally {
            DihHandArbiter.endHandPacketGroup(id());
        }
    }

    private void simulateVanillaUse(Placement placement) {
        BlockHitResult hit = placement.hit();
        for (InteractionHand hand : InteractionHand.values()) {
            if (hand == InteractionHand.MAIN_HAND && placement.hand() == InteractionHand.OFF_HAND) continue;

            if (hand == InteractionHand.OFF_HAND && DihHandArbiter.offhandClaimedByOther(id())) continue;
            ItemStack stack = MC.player.getItemInHand(hand);
            if (!stack.isItemEnabled(MC.level.enabledFeatures())) return;
            int oldCount = stack.getCount();
            InteractionResult blockResult = MC.gameMode.useItemOn(MC.player, hand, hit);
            if (blockResult instanceof InteractionResult.Success success) {
                if (success.swingSource() == InteractionResult.SwingSource.CLIENT) DihEntities.swing(MC.player, hand);
                if (!stack.isEmpty() && (stack.getCount() != oldCount || MC.player.hasInfiniteMaterials())) {
                    DihRender.itemUsed(hand);
                }
                return;
            }
            if (blockResult instanceof InteractionResult.Fail) return;

            if (stack.isEmpty()) continue;
            InteractionResult itemResult = MC.gameMode.useItem(MC.player, hand);
            if (!(itemResult instanceof InteractionResult.Success success)) continue;
            if (success.swingSource() == InteractionResult.SwingSource.CLIENT) DihEntities.swing(MC.player, hand);
            DihRender.itemUsed(hand);
            return;
        }
    }

    public BlockPos renderTarget() {
        return renderTarget;
    }

    public boolean renderFill() {
        return bool("fill");
    }

    public int guideColor() {
        try {
            String value = value("guide-color").replace("#", "");
            if (value.length() == 6) value = "FF" + value;
            return (int) Long.parseLong(value, 16);
        } catch (RuntimeException ignored) {
            return 0xFFFF3B3B;
        }
    }

    public static Placement activePlacement(Player player, HitResult currentHit) {
        Module module = ModuleRegistry.get("air-place");
        if (!(module instanceof AirPlaceModule airPlace) || !airPlace.isEnabled()) return null;
        return airPlace.placementFor(player, currentHit);
    }

    static Placement placement(Player player, HitResult currentHit, double range) {
        return placement(player, currentHit, range, "Auto", stack -> true);
    }

    static Placement placement(Player player, HitResult currentHit, double range, String handMode,
                               Predicate<ItemStack> allowed) {
        if (player == null || player.level() == null || currentHit == null
            || currentHit.getType() != HitResult.Type.MISS || handsBusy(player)) return null;
        InteractionHand hand = placementHand(player, handMode);
        if (hand != null && !allowed.test(player.getItemInHand(hand))) return null;
        if (hand == null) return null;
        HitResult picked = player.pick(Math.max(1.0D, Math.min(6.0D, range)), 0.0F, false);
        if (!usesAirMiss(currentHit, picked) || !(picked instanceof BlockHitResult blockHit)) return null;
        BlockPos pos = blockHit.getBlockPos();
        if (player.level().isOutsideBuildHeight(pos) || !player.level().getBlockState(pos).canBeReplaced()) return null;
        return new Placement(blockHit, hand);
    }

    static InteractionHand placementHand(Player player) {
        return placementHand(player, "Auto");
    }

    static InteractionHand placementHand(Player player, String handMode) {
        if (player == null) return null;
        if ("Main Hand".equals(handMode)) {
            return isPlaceable(player.getMainHandItem(), player) ? InteractionHand.MAIN_HAND : null;
        }
        if ("Off Hand".equals(handMode)) {
            return isPlaceable(player.getOffhandItem(), player) ? InteractionHand.OFF_HAND : null;
        }
        ItemStack main = player.getMainHandItem();
        if (isPlaceable(main, player)) return InteractionHand.MAIN_HAND;

        if (!main.isEmpty()) return null;
        return isPlaceable(player.getOffhandItem(), player) ? InteractionHand.OFF_HAND : null;
    }

    static boolean usesAirMiss(HitResult currentHit, HitResult pickedHit) {
        return currentHit != null && pickedHit instanceof BlockHitResult
            && currentHit.getType() == HitResult.Type.MISS && pickedHit.getType() == HitResult.Type.MISS;
    }

    private static boolean isPlaceable(ItemStack stack, Player player) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem
            && stack.isItemEnabled(player.level().enabledFeatures());
    }

    private static boolean handsBusy(Player player) {
        return player instanceof LocalPlayer local ? local.isHandsBusy() : player.isUsingItem();
    }

    static java.util.List<String> settingTips() {
        return java.util.List.of(RANGE_TIP, GUIDE_TIP, FILL_TIP, COLOR_TIP, HAND_TIP, DELAY_TIP, REPEAT_TIP,
            SNEAK_TIP, FILTER_TIP, BLOCKS_TIP);
    }
}
