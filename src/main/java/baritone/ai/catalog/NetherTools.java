package baritone.ai.catalog;

import baritone.acquire.exec.InventoryOps;
import baritone.acquire.model.Goal;
import baritone.acquire.model.Location;
import baritone.ai.BuiltinTools;
import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;
import baritone.api.BaritoneAPI;
import baritone.api.utils.IPlayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Portals and the fortress: the trips {@code #beat} makes through the Nether, one at a time. */
final class NetherTools {

    /** How far from the feet {@code light_portal} looks for a frame; it must also be in reach. */
    private static final int FRAME_RADIUS = 5;

    private NetherTools() {
    }

    static void register(ToolRegistry registry) {
        registry.register(AiTool.builder("build_portal", ToolCategory.NETHER_END)
                .summary("Go to the Nether, or back to the Overworld, building and lighting a portal if none is known.")
                .description("Go through a nether portal to the other dimension: the one remembered here or the nearest "
                        + "in view, or, with none, make one in the Overworld: with 10 obsidian it builds the frame on flat "
                        + "ground nearby; otherwise it casts the frame in place from lava and water with two buckets and 20 "
                        + "mould blocks, so no diamond pickaxe is needed. Then it lights it with flint and steel and steps "
                        + "in. Whatever is missing (buckets, blocks, flint and steel) is gathered first, as acquire would. "
                        + "Both ends of the portal are remembered for the trip back.")
                .schema(ToolSchema.builder()
                        .enumOf("to", "Where to go (default: the other dimension from here).", "nether", "overworld")
                        .build())
                .handler((ctx, args) -> {
                    Location to;
                    if (args.has("to")) {
                        to = args.string("to").equals("nether") ? Location.NETHER : Location.OVERWORLD;
                    } else {
                        LocalPlayer player = Minecraft.getInstance().player;
                        if (player == null) return ToolResult.failed("Not in a world.");
                        to = player.level().dimension() == Level.NETHER ? Location.OVERWORLD : Location.NETHER;
                    }
                    return BuiltinTools.startGoal(ctx, new Goal.AtLocation(to));
                })
                .build());

        registry.register(AiTool.builder("light_portal", ToolCategory.NETHER_END)
                .gameThread()
                .summary("Light the empty obsidian portal frame next to the player with flint and steel.")
                .description("Light an empty, complete obsidian portal frame within reach using flint and steel from the "
                        + "inventory. Walk to the frame first; build_portal builds and lights one on its own.")
                .schema(ToolSchema.EMPTY)
                .handler((ctx, args) -> lightPortal())
                .build());

        registry.register(AiTool.builder("barter_piglins", ToolCategory.NETHER_END)
                .summary("Throw gold ingots to piglins for an item, ender pearls by default.")
                .description("Barter with the piglins nearby, in the Nether: puts a gold armour piece on (piglins attack "
                        + "a player wearing none), throws them gold ingots one each and picks up what they throw back, "
                        + "until the inventory holds the count or the gold runs out; then acquire gets the rest the "
                        + "cheapest other way. About one trade in 47 gives 2 to 4 pearls: some 16 gold ingots a pearl.")
                .schema(ToolSchema.builder()
                        .string("item", "What to barter for (default ender_pearl).").defaultsTo("ender_pearl")
                        .integer("count", "How many to have in the inventory. Default 12.").range(1, 64).defaultsTo(12)
                        .build())
                .handler((ctx, args) -> BuiltinTools.startBarter(ctx,
                        args.has("item") ? args.string("item") : "ender_pearl", args.has("count") ? args.integer("count") : 12))
                .build());

        registry.register(AiTool.builder("find_fortress", ToolCategory.NETHER_END)
                .summary("Travel to a nether fortress, going to the Nether first if needed.")
                .description("Get to a nether fortress: through a portal to the Nether first if the player is in the "
                        + "Overworld, then to the nearest fortress from the seed map when the seed is known, or exploring "
                        + "along an axis otherwise. Blazes spawn there; acquire blaze_rod once inside.")
                .schema(ToolSchema.EMPTY)
                .handler((ctx, args) -> BuiltinTools.startGoal(ctx, new Goal.AtLocation(Location.FORTRESS)))
                .build());
    }

    private static ToolResult lightPortal() {
        IPlayerContext ctx = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext();
        LocalPlayer player = ctx.player();
        if (player == null) return ToolResult.failed("Not in a world.");
        Level level = ctx.world();
        BlockPos base = frameBase(level, ctx.playerFeet(), player.blockInteractionRange(), ctx.playerHead());
        if (base == null) {
            return ToolResult.failed("No empty obsidian portal frame within reach. Stand next to a complete frame "
                    + "(4 wide and 5 tall on the outside), or use build_portal.");
        }
        int slot = InventoryOps.toHotbar(ctx, stack -> stack.is(Items.FLINT_AND_STEEL));
        if (slot < 0) return ToolResult.failed("No flint and steel carried.");
        player.getInventory().setSelectedSlot(slot);
        Vec3 top = Vec3.atCenterOf(base).add(0, 0.5, 0);
        BlockHitResult hit = new BlockHitResult(top, Direction.UP, base, false);
        if (!ctx.playerController().processRightClickBlock(player, level, InteractionHand.MAIN_HAND, hit).consumesAction()) {
            return ToolResult.failed("The flint and steel didn't strike on the frame at " + base.toShortString() + ".");
        }
        player.swing(InteractionHand.MAIN_HAND);
        return ToolResult.ok("Lit the portal frame at " + base.above().toShortString()
                + " (the portal fills in once the server confirms).").fact("portal", base.above().toShortString());
    }

    /** An obsidian block within reach whose top is the floor of an empty, complete portal frame, or null. */
    private static BlockPos frameBase(Level level, BlockPos feet, double reach, Vec3 eye) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-FRAME_RADIUS, -2, -FRAME_RADIUS), feet.offset(FRAME_RADIUS, 2, FRAME_RADIUS))) {
            if (!level.getBlockState(pos).is(Blocks.OBSIDIAN) || !level.getBlockState(pos.above()).isAir()) continue;
            double distance = eye.distanceToSqr(Vec3.atCenterOf(pos).add(0, 0.5, 0));
            if (distance > reach * reach || distance >= bestDistance) continue;
            for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
                if (PortalShape.findEmptyPortalShape(level, pos.above(), axis).isPresent()) {
                    best = pos.immutable();
                    bestDistance = distance;
                    break;
                }
            }
        }
        return best;
    }
}
