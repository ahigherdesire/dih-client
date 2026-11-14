package baritone.acquire.exec;

import baritone.acquire.AcquireControl;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * A furnace set up where there's no room: the player stands in a 1x1 pocket in stone with a crafting table filling
 * the space above their head, as at the bottom of a shaft dug straight down. The station runner digs out room
 * beside the player instead of failing "no free spot" on every re-plan.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AcquireTightSpotGameTest implements FabricClientGameTest {
    private static final int TIMEOUT_TICKS = 3 * 60 * 20;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        List<String> events = new ArrayList<>();
        boolean[] ended = {false};
        boolean[] succeeded = {false};
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            for (String command : List.of(
                    "/difficulty peaceful",
                    "/gamerule doDaylightCycle false",
                    "/time set 3000",
                    "/clear @p",
                    "/execute in minecraft:overworld run fill -4 90 -4 4 101 4 minecraft:stone",
                    "/execute in minecraft:overworld run fill 0 95 0 0 96 0 minecraft:air",
                    "/execute in minecraft:overworld run setblock 0 97 0 minecraft:crafting_table",
                    "/tp @p 0 95 0",
                    "/give @p minecraft:stone_pickaxe",
                    "/give @p minecraft:furnace",
                    "/give @p minecraft:raw_iron",
                    "/give @p minecraft:oak_planks 4")) {
                world.getServer().runCommand(command);
            }
            context.waitFor(client -> count(client, Items.RAW_IRON) == 1 && count(client, Items.FURNACE) == 1);
            context.waitTicks(20);
            try {
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    acquire.addListener(event -> {
                        events.add(event.kind() + " " + event.message());
                        if (event.kind() == AcquireControl.AcquireEvent.Kind.DONE) succeeded[0] = true;
                        if (event.kind() == AcquireControl.AcquireEvent.Kind.DONE
                                || event.kind() == AcquireControl.AcquireEvent.Kind.FAILED) ended[0] = true;
                    });
                    events.add("plan:\n" + acquire.plan("iron_ingot", 1));
                    events.add("start: " + acquire.start("iron_ingot", 1));
                });
                context.waitFor(client -> ended[0], TIMEOUT_TICKS);
            } finally {
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    if (acquire != null) acquire.stop();
                });
                context.takeScreenshot("acquire-tight-spot");
                System.out.println("[AcquireTightSpotGameTest]\n  " + String.join("\n  ", events));
            }
            context.runOnClient(client -> {
                if (!succeeded[0] || count(client, Items.IRON_INGOT) < 1)
                    throw new AssertionError("no iron ingot from the tight spot\n" + String.join("\n", events));
                if (events.stream().anyMatch(e -> e.contains("no free spot")))
                    throw new AssertionError("still said no free spot\n" + String.join("\n", events));
                if (count(client, Items.FURNACE) < 1)
                    throw new AssertionError("the furnace was left behind\n" + String.join("\n", events));
            });
        }
        context.setScreen(TitleScreen::new);
    }

    private static int count(Minecraft client, Item item) {
        return client.player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item)).mapToInt(stack -> stack.getCount()).sum();
    }
}
