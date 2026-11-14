package baritone.acquire.exec;

import baritone.acquire.AcquireControl;
import baritone.acquire.model.Location;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;

import java.util.ArrayList;
import java.util.List;

/**
 * WP 11 part 2's acceptance: from an empty inventory at the world spawn, {@code #acquire} gets to the Nether on its
 * own: wood, stone, iron, diamonds, buckets, obsidian cast from lava, flint and steel, the portal. Each seed is a fresh
 * survival world; a death or a trip that doesn't end fails it. Mobs are off (peaceful) so the run measures the
 * gathering; fighting is the combat test's. {@code DIH_PORTAL_SEEDS=a,b,c} changes the seeds and
 * {@code DIH_NETHER_MINUTES} the limit per seed (default 60 game minutes).
 */
@SuppressWarnings("UnstableApiUsage")
public final class NetherFromNothingGameTest implements FabricClientGameTest {
    private static final List<String> SEEDS = List.of("8675309", "20260929", "-4172144997902289642");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        String env = System.getenv("DIH_PORTAL_SEEDS");
        List<String> seeds = env == null || env.isBlank() ? SEEDS : List.of(env.split(","));
        String minutes = System.getenv("DIH_NETHER_MINUTES");
        int ticks = 20 * 60 * (minutes == null || minutes.isBlank() ? 60 : Integer.parseInt(minutes.trim()));
        List<String> report = new ArrayList<>();
        try {
            for (String seed : seeds) report.add(seed(context, seed.trim(), ticks));
        } finally {
            System.out.println("[NetherFromNothingGameTest]\n  " + String.join("\n  ", report));
        }
        context.setScreen(TitleScreen::new);
    }

    private static String seed(ClientGameTestContext context, String seed, int ticks) {
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(state -> {
                    state.setSeed(seed);
                    state.setDifficulty(Difficulty.PEACEFUL);
                    state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
                    state.setAllowCommands(true);
                })
                .create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/clear @p");
            context.waitTicks(40);
            context.runOnClient(client -> PortalMemory.clear());
            List<String> events = new ArrayList<>();
            context.runOnClient(client -> AcquireControl.get().addListener(event -> {
                String line = event.kind() == AcquireControl.AcquireEvent.Kind.STEP ? event.message() : event.kind() + " " + event.message();
                events.add(line);
                System.out.println("[NetherFromNothingGameTest] " + seed + ": " + line);
            }));
            long start = System.currentTimeMillis();
            long gameStart = context.computeOnClient(client -> client.level.getGameTime());
            PortalGameTest.trip(context, "the_nether", Location.NETHER, events, seed, ticks);
            double real = (System.currentTimeMillis() - start) / 1000.0;
            long game = context.computeOnClient(client -> client.level.getGameTime()) - gameStart;
            long steps = events.stream().filter(e -> e.startsWith("Step ")).count();
            context.takeScreenshot("nether-from-nothing-" + seed);
            return String.format("seed %s: in the Nether after %.1f game minutes (%.0f s real), %d steps, 0 deaths",
                    seed, game / 1200.0, real, steps);
        }
    }
}
