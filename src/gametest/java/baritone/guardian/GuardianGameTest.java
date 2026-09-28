package baritone.guardian;

import baritone.Baritone;
import baritone.acquire.AcquireControl;
import baritone.api.BaritoneAPI;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Normal difficulty, at night: a threat shows up while {@code #acquire cobblestone 16} runs. The Guardian must deal
 * with it, the player must never die, and the acquire must still finish.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GuardianGameTest implements FabricClientGameTest {
    private static final int TIMEOUT_TICKS = 6 * 60 * 20;
    private static final int COBBLESTONE = 16;

    private enum Threat { ZOMBIE, SKELETON, CREEPER, LAVA }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        for (Threat threat : Threat.values()) scenario(context, threat);
        context.setScreen(TitleScreen::new);
    }

    private static void scenario(ClientGameTestContext context, Threat threat) {
        String label = threat.name().toLowerCase();
        List<String> events = new ArrayList<>();
        boolean[] ended = {false};
        boolean[] succeeded = {false};
        boolean[] died = {false};
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            for (String command : List.of(
                    "/difficulty normal",
                    "/gamerule doMobSpawning false",
                    "/gamerule doDaylightCycle false",
                    "/time set midnight",
                    "/clear @p",
                    "/execute in minecraft:overworld run fill -24 99 -24 24 99 24 minecraft:smooth_stone",
                    "/execute in minecraft:overworld run fill 8 100 -2 12 101 2 minecraft:stone",
                    "/tp @p 0 100 0",
                    "/give @p minecraft:wooden_pickaxe 1",
                    "/give @p minecraft:stone_sword 1",
                    "/give @p minecraft:bread 8")) {
                world.getServer().runCommand(command);
            }
            if (threat == Threat.LAVA) {
                // A one-block lava hole (how people step into lava), and a pond to put the fire out in.
                world.getServer().runCommand("/execute in minecraft:overworld run setblock 0 98 0 minecraft:smooth_stone");
                world.getServer().runCommand("/execute in minecraft:overworld run setblock 0 99 0 minecraft:lava");
                world.getServer().runCommand("/execute in minecraft:overworld run fill -6 98 -1 -5 98 1 minecraft:smooth_stone");
                world.getServer().runCommand("/execute in minecraft:overworld run fill -6 99 -1 -5 99 1 minecraft:water");
                world.getServer().runCommand("/tp @p 0 99 0");
            }
            context.waitFor(client -> client.player.getInventory().getNonEquipmentItems().stream()
                    .anyMatch(stack -> stack.is(Items.BREAD)));
            try {
                context.runOnClient(client -> {
                    guardian().log().clear();
                    AcquireControl acquire = AcquireControl.get();
                    acquire.addListener(event -> {
                        events.add(event.kind() + " " + event.message());
                        if (event.kind() == AcquireControl.AcquireEvent.Kind.DONE) succeeded[0] = true;
                        if (event.kind() == AcquireControl.AcquireEvent.Kind.DONE
                                || event.kind() == AcquireControl.AcquireEvent.Kind.FAILED
                                || event.kind() == AcquireControl.AcquireEvent.Kind.STOPPED) ended[0] = true;
                    });
                    events.add("start: " + acquire.start("cobblestone", COBBLESTONE));
                });
                switch (threat) {
                    case ZOMBIE -> world.getServer().runCommand("/summon minecraft:zombie -6 100 0 {PersistenceRequired:1b}");
                    case SKELETON -> world.getServer().runCommand("/summon minecraft:skeleton -6 100 0 {PersistenceRequired:1b,equipment:{mainhand:{id:\"minecraft:bow\",count:1}}}");
                    case CREEPER -> world.getServer().runCommand("/summon minecraft:creeper -7 100 0 {PersistenceRequired:1b}");
                    case LAVA -> { }
                }
                context.waitFor(client -> {
                    if (client.player == null || client.player.isDeadOrDying()) died[0] = true;
                    return ended[0] || died[0];
                }, TIMEOUT_TICKS);
            } finally {
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    if (acquire != null) acquire.stop();
                    events.add("guardian log: " + guardian().log().recent().stream()
                            .map(GuardianLog.Event::text).collect(Collectors.joining(" | ")));
                });
                context.takeScreenshot("guardian-" + label);
                System.out.println("[GuardianGameTest] " + label + "\n  " + String.join("\n  ", events));
            }
            String trail = String.join("\n", events);
            if (died[0]) throw new AssertionError(label + ": the player died\n" + trail);
            if (!succeeded[0]) throw new AssertionError(label + ": the acquire did not finish\n" + trail);
            String log = context.computeOnClient(client -> guardian().log().recent().stream()
                    .map(GuardianLog.Event::text).collect(Collectors.joining("\n")));
            String expected = threat == Threat.LAVA ? "lava" : label;
            if (!log.contains(expected)) throw new AssertionError(label + ": the Guardian never dealt with it\n" + trail);
        }
    }

    private static GuardianProcess guardian() {
        return ((Baritone) BaritoneAPI.getProvider().getPrimaryBaritone()).getGuardianProcess();
    }
}
