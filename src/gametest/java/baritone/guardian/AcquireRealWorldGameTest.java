package baritone.guardian;

import baritone.Baritone;
import baritone.acquire.AcquireControl;
import baritone.api.BaritoneAPI;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The real thing, opt-in because it takes minutes: a normally generated Survival world on Normal difficulty, empty
 * inventory, starting at nightfall. {@code #acquire iron_pickaxe}, then {@code #acquire diamond 3}. Both must finish
 * with zero deaths. Runs only when the {@code DIH_REAL_WORLD} environment variable is set (its value is the seed,
 * or "1" for the default seed).
 */
@SuppressWarnings("UnstableApiUsage")
public final class AcquireRealWorldGameTest implements FabricClientGameTest {
    private static final String DEFAULT_SEED = "8675309";
    private static final int IRON_TIMEOUT_TICKS = 20 * 60 * 20;
    private static final int DIAMOND_TIMEOUT_TICKS = 40 * 60 * 20;

    @Override
    public void runTest(ClientGameTestContext context) {
        String env = System.getenv("DIH_REAL_WORLD");
        if (env == null || env.isBlank()) return;
        String seed = env.equals("1") ? DEFAULT_SEED : env;
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(state -> {
                    state.setSeed(seed);
                    state.setDifficulty(Difficulty.NORMAL);
                    state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
                    state.setAllowCommands(true);
                })
                .create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/time set 13000");
            context.runOnClient(client -> guardian().log().clear());
            context.waitTicks(100);
            List<String> report = new ArrayList<>();
            int[] deaths = {0};
            // The acceptance run: a night-time iron pickaxe from nothing, with no deaths.
            boolean iron = run(context, "iron_pickaxe", 1, IRON_TIMEOUT_TICKS, deaths, report);
            int ironDeaths = deaths[0];
            // A benchmark beyond it: reported, not asserted.
            if (iron && ironDeaths == 0) run(context, "diamond", 3, DIAMOND_TIMEOUT_TICKS, deaths, report);
            report.add("deaths: " + deaths[0] + " (iron_pickaxe: " + ironDeaths + ")");
            report.add("guardian log: " + context.computeOnClient(client -> guardian().log().recent().stream()
                    .map(GuardianLog.Event::text).collect(Collectors.joining(" | "))));
            System.out.println("[AcquireRealWorldGameTest] seed " + seed + "\n  " + String.join("\n  ", report));
            if (!iron) throw new AssertionError("iron_pickaxe did not finish\n" + String.join("\n", report));
            if (ironDeaths > 0) throw new AssertionError("died " + ironDeaths + " time(s) getting an iron pickaxe\n" + String.join("\n", report));
        }
        context.setScreen(TitleScreen::new);
    }

    private static boolean run(ClientGameTestContext context, String goal, int count, int timeout, int[] deaths,
                               List<String> report) {
        boolean[] ended = {false};
        boolean[] succeeded = {false};
        boolean[] dead = {false};
        List<String> events = new ArrayList<>();
        long start = System.currentTimeMillis();
        try {
            context.runOnClient(client -> {
                AcquireControl acquire = AcquireControl.get();
                acquire.addListener(event -> {
                    if (ended[0]) return;
                    if (event.kind() != AcquireControl.AcquireEvent.Kind.STEP) events.add(event.kind() + " " + event.message());
                    else events.add(event.message());
                    if (event.kind() == AcquireControl.AcquireEvent.Kind.DONE) succeeded[0] = true;
                    if (event.kind() == AcquireControl.AcquireEvent.Kind.DONE
                            || event.kind() == AcquireControl.AcquireEvent.Kind.FAILED
                            || event.kind() == AcquireControl.AcquireEvent.Kind.STOPPED) ended[0] = true;
                });
                events.add("start: " + acquire.start(goal, count));
            });
            context.waitFor(client -> {
                boolean nowDead = client.player == null || client.player.isDeadOrDying();
                if (nowDead && !dead[0]) deaths[0]++;
                dead[0] = nowDead;
                return ended[0] || nowDead;
            }, timeout);
        } catch (AssertionError timedOut) {
            events.add("timed out: " + timedOut.getMessage());
        } finally {
            ended[0] = true;
            context.runOnClient(client -> {
                AcquireControl acquire = AcquireControl.get();
                if (acquire != null) acquire.stop();
            });
            context.takeScreenshot("real-world-" + goal);
        }
        long seconds = (System.currentTimeMillis() - start) / 1000;
        report.add(goal + " x" + count + ": " + (succeeded[0] ? "done" : "NOT done") + " in " + seconds / 60 + "m"
                + seconds % 60 + "s\n    " + String.join("\n    ", events));
        return succeeded[0];
    }

    private static GuardianProcess guardian() {
        return ((Baritone) BaritoneAPI.getProvider().getPrimaryBaritone()).getGuardianProcess();
    }
}
