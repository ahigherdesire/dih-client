package baritone.acquire;

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
 * End to end: from an empty inventory, #acquire really mines, crafts and smelts until the item is held.
 * The test world is superflat, so each scenario builds what it needs (stone, logs) next to the player.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AcquireGatherGameTest implements FabricClientGameTest {
    /** Five minutes of game ticks per scenario. */
    private static final int TIMEOUT_TICKS = 5 * 60 * 20;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            DihConfig config = DihConfig.getGlobal();
            if (config == null) throw new AssertionError("DIH config did not load");
            config.customMainMenu = false; // Fabric's runner requires the vanilla title screen after the test.
        });
        scenario(context, "dirt", 8, Items.DIRT, List.of());
        scenario(context, "stone_bricks", 4, Items.STONE_BRICKS, List.of(
                "execute at @p run fill ~4 ~ ~-2 ~8 ~2 ~2 minecraft:stone",
                "execute at @p run fill ~-4 ~ ~ ~-4 ~3 ~ minecraft:oak_log",
                "execute at @p run fill ~-4 ~ ~3 ~-4 ~3 ~3 minecraft:oak_log"));
        context.setScreen(TitleScreen::new);
    }

    private static void scenario(ClientGameTestContext context, String item, int count, Item expected, List<String> setup) {
        List<String> events = new ArrayList<>();
        boolean[] ended = {false};
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            world.getServer().runCommand("/time set day");
            world.getServer().runCommand("/clear @p");
            for (String command : setup) world.getServer().runCommand("/" + command);
            context.waitTicks(20);
            try {
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    if (acquire == null) throw new AssertionError("Acquire control did not register");
                    acquire.addListener(e -> {
                        events.add(e.kind() + " " + e.message());
                        if (e.kind() != AcquireControl.AcquireEvent.Kind.STARTED
                                && e.kind() != AcquireControl.AcquireEvent.Kind.STEP) ended[0] = true;
                    });
                    events.add("plan:\n" + acquire.plan(item, count));
                    events.add("start: " + acquire.start(item, count));
                });
                context.waitFor(client -> ended[0] || held(client, expected) >= count, TIMEOUT_TICKS);
            } catch (RuntimeException | AssertionError e) {
                events.add("wait ended with: " + e);
            } finally {
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    if (acquire != null) {
                        events.add("final status: " + acquire.status());
                        acquire.stop();
                    }
                    events.add("held " + held(client, expected) + " of " + count + " " + item);
                });
                context.takeScreenshot("acquire-gather-" + item);
                System.out.println("[AcquireGatherGameTest] " + item + "\n  " + String.join("\n  ", events));
            }
            int[] have = {0};
            context.runOnClient(client -> have[0] = held(client, expected));
            if (have[0] < count) {
                throw new AssertionError("#acquire " + item + " " + count + " ended with " + have[0] + ":\n  "
                        + String.join("\n  ", events));
            }
        }
    }

    private static int held(Minecraft client, Item item) {
        if (client.player == null) return 0;
        return client.player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item)).mapToInt(stack -> stack.getCount()).sum();
    }
}
