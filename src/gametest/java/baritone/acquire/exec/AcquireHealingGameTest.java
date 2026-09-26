package baritone.acquire.exec;

import baritone.acquire.AcquireControl;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Verifies that an active acquire eats a golden apple at emergency health, even with full food. */
@SuppressWarnings("UnstableApiUsage")
public final class AcquireHealingGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            DihConfig config = DihConfig.getGlobal();
            if (config == null) throw new AssertionError("DIH config did not load");
            config.customMainMenu = false;
        });
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            context.runOnClient(client -> checkVanillaFoodData());
            world.getServer().runCommand("/give @p minecraft:golden_apple 1");
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers()
                    .forEach(player -> player.setHealth(4.0F)));
            context.waitFor(client -> client.player != null && client.player.getHealth() <= 6.0F
                    && client.player.getInventory().getNonEquipmentItems().stream()
                    .anyMatch(stack -> stack.is(Items.GOLDEN_APPLE)));
            try {
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    if (acquire == null) throw new AssertionError("Acquire control did not register");
                    acquire.start("dirt", 64);
                    if (!acquire.isActive()) throw new AssertionError("Acquire did not start");
                });
                context.waitFor(client -> client.player != null
                        && client.player.getAbsorptionAmount() > 0
                        && client.player.getInventory().getNonEquipmentItems().stream()
                        .noneMatch(stack -> stack.is(Items.GOLDEN_APPLE)));
                context.runOnClient(client -> {
                    if (client.player.getHealth() <= 0) throw new AssertionError("Player died before healing");
                });
            } finally {
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    if (acquire != null) acquire.stop();
                });
            }
        }
        context.setScreen(TitleScreen::new);
    }

    private static void checkVanillaFoodData() {
        FoodChoice.Food steak = Foods.of(new ItemStack(Items.COOKED_BEEF));
        if (steak == null || !steak.id().equals("minecraft:cooked_beef") || steak.nutrition() != 8
                || Math.abs(steak.saturation() - 12.8F) > 0.01F || steak.harmful()) {
            throw new AssertionError("Cooked beef food components were read incorrectly: " + steak);
        }
        if (Foods.eatTicks(new ItemStack(Items.COOKED_BEEF)) != Foods.DEFAULT_EAT_TICKS
                || Foods.of(new ItemStack(Items.STONE)) != null
                || Foods.of(new ItemStack(Items.MILK_BUCKET)) != null
                || Foods.of(ItemStack.EMPTY) != null) {
            throw new AssertionError("Non-food or eating duration was read incorrectly");
        }
        for (var item : List.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO,
                Items.PUFFERFISH, Items.CHICKEN)) {
            ItemStack stack = new ItemStack(item);
            if (!Foods.harmfulEffect(stack.get(DataComponents.CONSUMABLE)) || !Foods.of(stack).harmful()) {
                throw new AssertionError("Harmful food was not classified: " + stack);
            }
        }
        for (var item : List.of(Items.COOKED_BEEF, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
                Items.HONEY_BOTTLE, Items.BREAD)) {
            ItemStack stack = new ItemStack(item);
            if (Foods.harmfulEffect(stack.get(DataComponents.CONSUMABLE))) {
                throw new AssertionError("Safe food was classified as harmful: " + stack);
            }
        }
        if (!Foods.of(new ItemStack(Items.SUSPICIOUS_STEW)).harmful()
                || !Foods.of(new ItemStack(Items.GOLDEN_APPLE)).golden()) {
            throw new AssertionError("Special food classification was read incorrectly");
        }
        for (FoodGoal.Candidate candidate : FoodGoal.CANDIDATES) {
            FoodChoice.Food food = Foods.of(new ItemStack(InventoryReader.itemOf(candidate.item())));
            if (food == null || food.nutrition() != candidate.nutrition() || food.harmful()) {
                throw new AssertionError("Invalid food-goal candidate: " + candidate.item());
            }
        }
    }
}
