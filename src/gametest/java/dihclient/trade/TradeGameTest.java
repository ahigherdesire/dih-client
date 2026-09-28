package dihclient.trade;

import dihclient.api.module.Setting;
import dihclient.modules.Module;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihConfig;
import dihclient.util.DihMacro;
import dihclient.util.macro.MacroExecutor;
import dihclient.util.macro.SendChatAction;
import dihclient.util.macro.TradeAction;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AutoTrade against real villagers: fixed offers (summoned with NBT) for Trade mode, the Trade macro action and the
 * offer cache, then a live librarian and lectern for Reroll mode.
 */
@SuppressWarnings({"UnstableApiUsage", "unchecked", "rawtypes"})
public final class TradeGameTest implements FabricClientGameTest {
    private static final String MENDING_BOOK =
            "{id:\"minecraft:enchanted_book\",count:1,components:{\"minecraft:stored_enchantments\":{\"minecraft:mending\":1}}}";
    private static final List<String> CHAT = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void runTest(ClientGameTestContext context) {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> CHAT.add(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> CHAT.add(message.getString()));
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        tradeModeMacroAndCache(context);
        reroll(context);
        context.setScreen(TitleScreen::new);
    }

    private static void setup(TestSingleplayerContext world) {
        for (String command : List.of(
                "/difficulty peaceful",
                "/gamerule doDaylightCycle false",
                "/time set 3000",
                "/clear @p",
                "/execute in minecraft:overworld run fill -8 99 -8 8 99 8 minecraft:smooth_stone",
                "/tp @p 0 100 0 -90 0",
                "/give @p minecraft:emerald 64",
                "/give @p minecraft:book 8")) {
            world.getServer().runCommand(command);
        }
    }

    private static void tradeModeMacroAndCache(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            setup(world);
            world.getServer().runCommand("/summon minecraft:villager 2.5 100 0.5 {NoAI:1b,Rotation:[90f,0f],"
                    + "VillagerData:{profession:\"minecraft:librarian\",level:1,type:\"minecraft:plains\"},"
                    + "Offers:{Recipes:["
                    + "{buy:{id:\"minecraft:emerald\",count:10},buyB:{id:\"minecraft:book\",count:1},sell:" + MENDING_BOOK + ",maxUses:12,xp:1},"
                    + "{buy:{id:\"minecraft:emerald\",count:1},sell:{id:\"minecraft:bookshelf\",count:1},maxUses:12,xp:1}"
                    + "]}}");
            context.waitFor(client -> client.player.getInventory().getNonEquipmentItems().stream().anyMatch(s -> s.is(Items.BOOK))
                    && !client.level.getEntitiesOfClass(Villager.class, client.player.getBoundingBox().inflate(8)).isEmpty());

            // Trade mode: walks up, opens the trades, buys two mending books (the per-visit cap), leaves.
            context.runOnClient(client -> {
                Module m = ModuleRegistry.get("auto-trade");
                set(m, "mode", "Trade");
                set(m, "rule", "mending");
                set(m, "per-visit", 2);
                set(m, "walk", true);
                m.setEnabled(true);
            });
            context.waitFor(client -> count(client, Items.ENCHANTED_BOOK) >= 2 && !(client.player.containerMenu instanceof MerchantMenu),
                    30 * 20);
            context.waitTicks(40);
            context.runOnClient(client -> {
                ModuleRegistry.get("auto-trade").setEnabled(false);
                if (count(client, Items.ENCHANTED_BOOK) != 2)
                    throw new AssertionError("trade mode should stop at the per-visit cap; bought " + count(client, Items.ENCHANTED_BOOK));
                if (count(client, Items.EMERALD) != 44) throw new AssertionError("expected 44 emeralds left, have " + count(client, Items.EMERALD));
                ItemStack book = client.player.getInventory().getNonEquipmentItems().stream()
                        .filter(s -> s.is(Items.ENCHANTED_BOOK)).findFirst().orElseThrow();
                if (!String.valueOf(book.get(DataComponents.STORED_ENCHANTMENTS)).contains("mending"))
                    throw new AssertionError("bought the wrong book: " + book.get(DataComponents.STORED_ENCHANTMENTS));
                Villager villager = villager(client);
                VillagerOffers cached = TradeCaches.current().get(villager.getStringUUID());
                if (cached == null) throw new AssertionError("the offers were not cached");
                if (!"mending 1 – 10e".equals(cached.label())) throw new AssertionError("label: " + cached.label());
                EnchantPlanner.Plan plan = EnchantPlanner.plan(EnchantPlanner.parseWants(List.of("mending")),
                        TradeCaches.current().all(), 0, 100, 0);
                if (!plan.complete() || plan.totalEmeralds() != 10) throw new AssertionError(EnchantPlanner.explain(plan).toString());
            });
            context.takeScreenshot("autotrade-label");

            // The Trade macro action: buy one bookshelf (slot 1) from the open screen, then print trade_status.
            context.runOnClient(client -> {
                Villager villager = villager(client);
                client.gameMode.interact(client.player, villager,
                        new EntityHitResult(villager, villager.getBoundingBox().getCenter()), InteractionHand.MAIN_HAND);
            });
            context.waitFor(client -> client.player.containerMenu instanceof MerchantMenu menu && !menu.getOffers().isEmpty(), 100);
            CHAT.clear();
            context.runOnClient(client -> {
                TradeAction trade = new TradeAction();
                trade.rule = "slot 1";
                trade.count = 1;
                SendChatAction say = new SendChatAction();
                say.message = "/say trade_status={trade_status} count={trade_count} spent={trade_spent}";
                say.waitForGuiAfter = false;
                DihMacro macro = new DihMacro();
                macro.name = "trade-test";
                macro.actions = new ArrayList<>(List.of(trade, say));
                MacroExecutor.execute(macro);
            });
            context.waitFor(client -> CHAT.stream().anyMatch(line -> line.contains("trade_status=")), 200);
            String line = CHAT.stream().filter(l -> l.contains("trade_status=")).findFirst().orElse("");
            if (!line.contains("trade_status=ok count=1 spent=1")) throw new AssertionError("macro said: " + line);
            context.runOnClient(client -> {
                if (count(client, Items.BOOKSHELF) != 1) throw new AssertionError("the macro didn't buy the bookshelf");
                client.player.closeContainer();
            });
        }
    }

    private static void reroll(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            setup(world);
            // A one-block cell: glass on three sides, the lectern on the player's side (with glass above it).
            for (String command : List.of(
                    "/execute in minecraft:overworld run fill 4 100 0 4 101 0 minecraft:glass",
                    "/execute in minecraft:overworld run fill 3 100 1 3 101 1 minecraft:glass",
                    "/execute in minecraft:overworld run fill 3 100 -1 3 101 -1 minecraft:glass",
                    "/execute in minecraft:overworld run fill 3 102 0 3 102 0 minecraft:glass",
                    "/execute in minecraft:overworld run setblock 2 101 0 minecraft:glass",
                    "/execute in minecraft:overworld run setblock 2 100 0 minecraft:lectern",
                    "/tp @p 1 100 0 -90 0",
                    "/summon minecraft:villager 3.5 100 0.5 {VillagerData:{profession:\"minecraft:librarian\",level:1,type:\"minecraft:plains\"},"
                            + "Offers:{Recipes:[{buy:{id:\"minecraft:paper\",count:24},sell:{id:\"minecraft:emerald\",count:1},maxUses:16,xp:2}]}}")) {
                world.getServer().runCommand(command);
            }
            // Let it claim the lectern as its workstation.
            context.waitTicks(200);
            context.runOnClient(client -> {
                Module m = ModuleRegistry.get("auto-trade");
                set(m, "mode", "Reroll");
                set(m, "rule", "book");
                set(m, "per-visit", 0);
                m.setEnabled(true);
                if (!m.isEnabled()) throw new AssertionError("Reroll mode refused to start: " + String.join(" | ", CHAT));
            });
            context.waitFor(client -> !ModuleRegistry.get("auto-trade").isEnabled(), 5 * 60 * 20);
            context.takeScreenshot("autotrade-reroll");
            String status = context.computeOnClient(client -> ((dihclient.modules.AutoTradeModule) ModuleRegistry.get("auto-trade")).status());
            System.out.println("[TradeGameTest] reroll: " + status);
            if (!status.startsWith("locked")) throw new AssertionError("reroll ended: " + status + "\n" + String.join("\n", CHAT));
            context.runOnClient(client -> {
                if (count(client, Items.ENCHANTED_BOOK) != 1) throw new AssertionError("locking should buy exactly one book");
            });
        }
    }

    private static Villager villager(Minecraft client) {
        return client.level.getEntitiesOfClass(Villager.class, client.player.getBoundingBox().inflate(8)).get(0);
    }

    private static int count(Minecraft client, net.minecraft.world.item.Item item) {
        return client.player.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void set(Module module, String id, Object value) {
        for (Setting<?, ?> setting : module.settings()) {
            if (setting.id().equals(id)) {
                ((Setting) setting).set(value);
                return;
            }
        }
        throw new AssertionError("no setting " + id);
    }
}
