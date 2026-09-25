package baritone.acquire.exec;

import baritone.Baritone;
import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** What every step runner needs: the player, the knowledge base, stations, tools, looking and inventory room. */
final class ExecContext implements Helper {

    final Baritone baritone;
    final IPlayerContext ctx;
    final Knowledge knowledge;
    final StationFinder stations;
    private final Supplier<Set<String>> neededItems;

    ExecContext(Baritone baritone, Knowledge knowledge, StationFinder stations, Supplier<Set<String>> neededItems) {
        this.baritone = baritone;
        this.ctx = baritone.getPlayerContext();
        this.knowledge = knowledge;
        this.stations = stations;
        this.neededItems = neededItems;
    }

    LocalPlayer player() {
        return ctx.player();
    }

    int have(String item) {
        return InventoryReader.count(ctx.player(), item);
    }

    int stationRadius() {
        return Baritone.settings().acquireStationRadius.value;
    }

    // ---- tools ----

    /** True when {@code req} asks for nothing, or a matching tool is in the main inventory or offhand. */
    boolean hasTool(ToolReq req) {
        if (!needsTool(req)) return true;
        Predicate<ItemStack> match = toolMatcher(req);
        return InventoryReader.find(player(), match) >= 0 || match.test(player().getOffhandItem());
    }

    static boolean needsTool(ToolReq req) {
        return req != null && req.required() && req.type() != null;
    }

    Predicate<ItemStack> toolMatcher(ToolReq req) {
        return stack -> {
            if (stack.isEmpty()) return false;
            String id = InventoryReader.idOf(stack);
            return req.type().equals(knowledge.toolType(id)) && knowledge.toolTier(id) >= req.minTier();
        };
    }

    /** "pickaxe", "stone pickaxe or better", "shears". Tier 1 (wood, gold) means any tool of the type. */
    static String describeTool(ToolReq req) {
        String tier = switch (req.minTier()) {
            case 2 -> "stone ";
            case 3 -> "iron ";
            case 4 -> "diamond ";
            case 5 -> "netherite ";
            default -> "";
        };
        return tier + req.type() + (tier.isEmpty() ? "" : " or better");
    }

    // ---- looking and reach ----

    double reach() {
        return player().blockInteractionRange();
    }

    /** A rotation that hits {@code pos} from where the player stands, if it is in reach and visible. */
    Optional<Rotation> reachable(BlockPos pos) {
        return RotationUtils.reachable(ctx, pos, reach());
    }

    void look(Rotation rotation, boolean blockInteract) {
        baritone.getLookBehavior().updateTarget(rotation, blockInteract);
    }

    void lookAt(Vec3 point, boolean blockInteract) {
        look(RotationUtils.calcRotationFromVec3d(ctx.playerHead(), point, ctx.playerRotations()), blockInteract);
    }

    // ---- inventory room ----

    /**
     * Null when one more {@code item} fits. Otherwise drops one stack of junk if the user allowed it, or
     * returns a fatal result: acquire never throws items away on its own.
     */
    StepRunner.Result checkRoom(String item) {
        LocalPlayer player = player();
        Item it = InventoryReader.itemOf(item);
        if (InventoryReader.hasRoomFor(player, it)) return null;
        if (!Baritone.settings().acquireDropJunk.value) {
            return StepRunner.Result.fatal("Inventory full: no room for " + Step.shortId(item)
                    + ". Make room (or #set acquireDropJunk true to let acquire drop filler blocks), then #acquire again.");
        }
        List<ItemStack> main = player.getInventory().getNonEquipmentItems();
        List<String> ids = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (int i = 0; i < InventoryReader.MAIN_SIZE; i++) {
            ItemStack stack = main.get(i);
            ids.add(stack.isEmpty() ? null : InventoryReader.idOf(stack));
            counts.add(stack.getCount());
        }
        int slot = JunkPolicy.pickStack(ids, counts, neededItems.get());
        if (slot >= 0 && InventoryOps.throwStack(ctx, slot)) {
            logDirect("Inventory full: dropped " + counts.get(slot) + " " + Step.shortId(ids.get(slot)) + ".");
            return null;
        }
        return StepRunner.Result.fatal("Inventory full and nothing is safe to drop. Make room, then #acquire again.");
    }
}
