package baritone.acquire;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Normal-difficulty terrain regressions for a carried table and a pickaxe near breakage. */
@SuppressWarnings("UnstableApiUsage")
public final class AcquireUndergroundGameTest implements FabricClientGameTest {
    private static final int TIMEOUT_TICKS = 15 * 60 * 20;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        scenario(context, false);
        scenario(context, true);
        context.setScreen(TitleScreen::new);
    }

    private static void scenario(ClientGameTestContext context, boolean nearlyBrokenTool) {
        String label = nearlyBrokenTool ? "tool-break" : "far-underground-iron";
        List<String> events = new ArrayList<>();
        boolean[] ended = {false};
        boolean[] succeeded = {false};
        String[] planText = {""};
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty normal");
            world.getServer().runCommand("/gamerule doMobSpawning true");
            world.getServer().runCommand("/time set day");
            world.getServer().runCommand("/clear @p");
            world.getServer().runCommand("/execute in minecraft:overworld run fill -8 99 -6 58 99 6 minecraft:stone");
            world.getServer().runCommand("/tp @p 0 100 0");
            world.getServer().runCommand("/execute in minecraft:overworld run fill -4 100 2 -4 107 2 minecraft:oak_log");
            if (nearlyBrokenTool) {
                world.getServer().runCommand("/execute in minecraft:overworld run fill 5 100 -2 14 102 2 minecraft:stone");
                world.getServer().runCommand("/give @p minecraft:wooden_pickaxe[minecraft:damage=55] 1");
                context.waitFor(client -> client.player.getInventory().getNonEquipmentItems().stream()
                        .anyMatch(stack -> stack.is(Items.WOODEN_PICKAXE) && stack.getDamageValue() >= 55));
            } else {
                world.getServer().runCommand("/execute in minecraft:overworld run fill 36 80 -4 53 99 4 minecraft:stone");
                world.getServer().runCommand("/execute in minecraft:overworld run fill 42 86 -2 53 86 2 minecraft:iron_ore");
            }
            context.waitTicks(20);
            int[] oldMax = {0};
            try {
                context.runOnClient(client -> {
                    if (nearlyBrokenTool) {
                        oldMax[0] = Baritone.settings().acquireMaxReplans.value;
                        Baritone.settings().acquireMaxReplans.value = 0;
                    }
                    AcquireControl acquire = AcquireControl.get();
                    acquire.addListener(event -> {
                        events.add(event.kind() + " " + event.message());
                        if (event.kind() == AcquireControl.AcquireEvent.Kind.DONE) succeeded[0] = true;
                        if (event.kind() == AcquireControl.AcquireEvent.Kind.DONE
                                || event.kind() == AcquireControl.AcquireEvent.Kind.FAILED) ended[0] = true;
                    });
                    String goal = nearlyBrokenTool ? "cobblestone" : "iron_pickaxe";
                    planText[0] = acquire.plan(goal, nearlyBrokenTool ? 12 : 1);
                    events.add("plan:\n" + planText[0]);
                    events.add("start: " + acquire.start(goal, nearlyBrokenTool ? 12 : 1));
                });
                context.waitFor(client -> ended[0], TIMEOUT_TICKS);
            } finally {
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    if (acquire != null) acquire.stop();
                    if (nearlyBrokenTool) Baritone.settings().acquireMaxReplans.value = oldMax[0];
                });
                context.takeScreenshot("acquire-" + label);
                System.out.println("[AcquireUndergroundGameTest] " + label + "\n  " + String.join("\n  ", events));
            }
            context.runOnClient(client -> {
                int result = held(client, nearlyBrokenTool);
                if (!succeeded[0] || result < (nearlyBrokenTool ? 12 : 1))
                    throw new AssertionError(label + " ended short: " + result + "\n" + String.join("\n", events));
                if (nearlyBrokenTool) return;
                if (countItem(client, Items.CRAFTING_TABLE) < 1)
                    throw new AssertionError("The crafting table was left behind\n" + String.join("\n", events));
                if (countItem(client, Items.STONE_SWORD) < 1)
                    throw new AssertionError("The underground plan never supplied a stone sword\n" + String.join("\n", events));
                if (planText[0].contains("craft 1 iron_helmet")
                        && !client.player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET))
                    throw new AssertionError("Planned iron helmet was not worn\n" + String.join("\n", events));
                if (planText[0].contains("craft 1 shield") && !client.player.getOffhandItem().is(Items.SHIELD))
                    throw new AssertionError("Planned shield was not equipped\n" + String.join("\n", events));
            });
        }
    }

    private static int held(net.minecraft.client.Minecraft client, boolean cobblestone) {
        if (client.player == null) return 0;
        return client.player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(cobblestone ? Items.COBBLESTONE : Items.IRON_PICKAXE))
                .mapToInt(stack -> stack.getCount()).sum();
    }

    private static int countItem(net.minecraft.client.Minecraft client, Item item) {
        return client.player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item)).mapToInt(stack -> stack.getCount()).sum();
    }
}
