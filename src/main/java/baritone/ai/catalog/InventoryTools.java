package baritone.ai.catalog;

import baritone.acquire.exec.InventoryOps;
import baritone.ai.tool.AiTool;
import baritone.ai.tool.CommandTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;
import baritone.api.BaritoneAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What is carried and worn, eating, and putting things in hand. */
final class InventoryTools {

    /** One stack as the summary sees it. {@code maxDamage} is 0 for items that don't wear out. */
    record Stack(String id, int count, int damage, int maxDamage) {
    }

    private InventoryTools() {
    }

    static void register(ToolRegistry registry) {
        registry.register(AiTool.builder("inventory", ToolCategory.INVENTORY)
                .gameThread()
                .summary("Everything carried and worn, with counts and tool durability.")
                .schema(ToolSchema.EMPTY)
                .handler((ctx, args) -> {
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (player == null) return ToolResult.failed("Not in a world.");
                    List<Stack> carried = new ArrayList<>();
                    int free = 0;
                    for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                        if (stack.isEmpty()) free++;
                        else carried.add(stack(stack));
                    }
                    List<Stack> worn = new ArrayList<>();
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        if (slot == EquipmentSlot.MAINHAND) continue;
                        ItemStack stack = player.getItemBySlot(slot);
                        if (!stack.isEmpty()) worn.add(stack(stack));
                    }
                    return summarize(carried, worn, free);
                })
                .build());

        registry.register(Cmd.start(AiTool.builder("eat", ToolCategory.INVENTORY)
                .summary("Eat now: the best food carried, or a named one.")
                .schema(ToolSchema.builder()
                        .string("food", "A food id, e.g. bread (default: the best for health and hunger).")
                        .build()), "eat", args -> List.of(args.has("food") ? "eat " + Cmd.id(args, "food") : "eat")));

        registry.register(AiTool.builder("equip", ToolCategory.INVENTORY)
                .gameThread()
                .summary("Hold an item, wear an armour piece, or put an item in the off hand.")
                .schema(ToolSchema.builder()
                        .string("item", "The item id.").required()
                        .bool("offhand", "Put it in the off hand (a shield, a totem).").defaultsTo(false)
                        .build())
                .handler((ctx, args) -> equip(CommandTool.id(args.string("item")), args.bool("offhand")))
                .build());
    }

    private static Stack stack(ItemStack stack) {
        return new Stack(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(),
                stack.isDamageableItem() ? stack.getDamageValue() : 0, stack.isDamageableItem() ? stack.getMaxDamage() : 0);
    }

    /** Counts per item (tools listed with durability left), what is worn, and free slots. */
    static ToolResult summarize(List<Stack> carried, List<Stack> worn, int freeSlots) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> tools = new ArrayList<>();
        for (Stack s : carried) {
            if (s.maxDamage() > 0) tools.add(s.id() + " " + durability(s));
            else counts.merge(s.id(), s.count(), Integer::sum);
        }
        List<String> items = new ArrayList<>();
        counts.forEach((id, count) -> items.add(count + " " + id));
        List<String> wornText = new ArrayList<>();
        for (Stack s : worn) wornText.add(s.maxDamage() > 0 ? s.id() + " " + durability(s) : s.id());
        String text = "Carrying: " + (items.isEmpty() && tools.isEmpty() ? "nothing" : String.join(", ", concat(items, tools)))
                + ". Wearing: " + (wornText.isEmpty() ? "nothing" : String.join(", ", wornText))
                + ". Free slots: " + freeSlots + ".";
        return ToolResult.ok(text).fact("items", counts).fact("tools", tools).fact("worn", wornText).fact("free_slots", freeSlots);
    }

    private static String durability(Stack s) {
        return "(" + (s.maxDamage() - s.damage()) + "/" + s.maxDamage() + ")";
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static ToolResult equip(String id, boolean offhand) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return ToolResult.failed("Not in a world.");
        var ctx = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext();
        int slot = InventoryOps.toHotbar(ctx, stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(id));
        if (slot < 0) return ToolResult.failed("No " + id + " carried.").fact("item", id);
        player.getInventory().setSelectedSlot(slot);
        ItemStack held = player.getMainHandItem();
        EquipmentSlot wearSlot = player.getEquipmentSlotForItem(held);
        if (offhand) {
            player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                    net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.DOWN));
            return ToolResult.ok("Put " + id + " in the off hand.").fact("item", id).fact("slot", "offhand");
        }
        if (wearSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            return ToolResult.ok("Wearing " + id + ".").fact("item", id).fact("slot", wearSlot.getName());
        }
        return ToolResult.ok("Holding " + id + ".").fact("item", id).fact("slot", "hotbar " + (slot + 1));
    }
}
