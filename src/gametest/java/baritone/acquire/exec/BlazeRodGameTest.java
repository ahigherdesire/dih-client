package baritone.acquire.exec;

import baritone.acquire.AcquireControl;
import baritone.acquire.model.Goal;
import baritone.api.BaritoneAPI;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * WP 11 part 3: starting in the Nether with Overworld gear (iron armour, sword, shield, bow, food, blocks), {@code
 * #acquire blaze_rod 7} finds a fortress, gets to a blaze spawner and kills blazes until it has 7 rods. Easy
 * difficulty, since blazes don't spawn on peaceful; a death fails the seed. {@code DIH_BLAZE_SEEDS=a,b,c} changes
 * the seeds and {@code DIH_BLAZE_MINUTES} the limit per seed (default 30 game minutes).
 */
@SuppressWarnings("UnstableApiUsage")
public final class BlazeRodGameTest implements FabricClientGameTest {
    private static final List<String> SEEDS = List.of("8675309", "20260929", "-4172144997902289642");
    private static final int RODS = 7;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        String env = System.getenv("DIH_BLAZE_SEEDS");
        List<String> seeds = env == null || env.isBlank() ? SEEDS : List.of(env.split(","));
        String minutes = System.getenv("DIH_BLAZE_MINUTES");
        int ticks = 20 * 60 * (minutes == null || minutes.isBlank() ? 30 : Integer.parseInt(minutes.trim()));
        List<String> report = new ArrayList<>();
        try {
            for (String seed : seeds) report.add(seed(context, seed.trim(), ticks));
        } finally {
            System.out.println("[BlazeRodGameTest]\n  " + String.join("\n  ", report));
        }
        context.setScreen(TitleScreen::new);
    }

    private static String seed(ClientGameTestContext context, String seed, int ticks) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(state -> {
                    state.setSeed(seed);
                    state.setDifficulty(Difficulty.EASY);
                    state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
                    state.setAllowCommands(true);
                })
                .create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            // A pocket of air on an obsidian floor near the Nether's 0 0, clear of lava.
            TestPads.load(context, world, Level.NETHER, -3, -3, 3, 3);
            for (String command : List.of(
                    "/execute in minecraft:the_nether run fill -3 69 -3 3 69 3 minecraft:obsidian",
                    "/execute in minecraft:the_nether run fill -3 70 -3 3 73 3 minecraft:air",
                    "/execute in minecraft:the_nether run tp @p 0 70 0",
                    "/clear @p",
                    "/item replace entity @p armor.head with minecraft:iron_helmet",
                    "/item replace entity @p armor.chest with minecraft:iron_chestplate",
                    "/item replace entity @p armor.legs with minecraft:iron_leggings",
                    "/item replace entity @p armor.feet with minecraft:iron_boots",
                    "/item replace entity @p weapon.offhand with minecraft:shield",
                    "/give @p minecraft:iron_sword",
                    "/give @p minecraft:iron_pickaxe",
                    "/give @p minecraft:bow",
                    "/give @p minecraft:arrow 64",
                    "/give @p minecraft:cobblestone 64",
                    "/give @p minecraft:cooked_beef 32")) {
                world.getServer().runCommand(command);
            }
            context.waitFor(client -> client.level != null && TravelRunner.dimensionId(client.level).equals("the_nether")
                    && client.player.getInventory().getNonEquipmentItems().stream()
                    .anyMatch(stack -> stack.is(net.minecraft.world.item.Items.COOKED_BEEF)));
            context.waitTicks(40);
            TestPads.expect(world, Level.NETHER, new BlockPos(3, 69, 3), Blocks.OBSIDIAN);
            List<String> events = new ArrayList<>();
            context.runOnClient(client -> AcquireControl.get().addListener(event -> {
                String line = event.kind() == AcquireControl.AcquireEvent.Kind.STEP ? event.message() : event.kind() + " " + event.message();
                events.add(line);
                System.out.println("[BlazeRodGameTest] " + seed + ": " + line);
            }));
            long gameStart = context.computeOnClient(client -> client.level.getGameTime());
            boolean[] dead = {false};
            context.runOnClient(client -> events.add("start: "
                    + AcquireControl.get().startGoal(new Goal.ItemGoal("minecraft:blaze_rod", RODS))));
            try {
                context.waitFor(client -> {
                    if (client.player == null || client.player.isDeadOrDying()) {
                        dead[0] = true;
                        return true;
                    }
                    return !AcquireControl.get().isActive();
                }, ticks);
            } catch (Throwable timeout) {
                events.add("timed out: " + context.computeOnClient(client -> AcquireControl.get().status()));
            } finally {
                context.runOnClient(client -> {
                    AcquireControl.get().stop();
                    BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                });
            }
            if (dead[0]) PortalGameTest.fail(context, seed, "died getting blaze rods", events);
            int rods = context.computeOnClient(client -> InventoryReader.count(client.player, "minecraft:blaze_rod"));
            if (rods < RODS) PortalGameTest.fail(context, seed, rods + "/" + RODS + " blaze rods", events);
            float health = context.computeOnClient(client -> client.player.getHealth());
            long game = context.computeOnClient(client -> client.level.getGameTime()) - gameStart;
            context.takeScreenshot("blaze-rods-" + seed);
            return String.format("seed %s: %d rods in %.1f game minutes, %.0f health left, 0 deaths", seed, rods,
                    game / 1200.0, health);
        }
    }
}
