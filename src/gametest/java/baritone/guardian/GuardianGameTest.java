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

    private enum Threat { ZOMBIE, SKELETON, CREEPER, LAVA, CROWD }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        // DIH_GUARDIAN_ONLY=crowd DIH_GUARDIAN_REPEAT=5 reruns one scenario to chase a flaky death.
        String only = System.getenv("DIH_GUARDIAN_ONLY");
        int repeat = 1;
        try {
            repeat = Math.max(1, Integer.parseInt(System.getenv().getOrDefault("DIH_GUARDIAN_REPEAT", "1")));
        } catch (NumberFormatException ignored) {
        }
        for (Threat threat : Threat.values()) {
            if (only != null && !threat.name().equalsIgnoreCase(only)) continue;
            for (int run = 0; run < repeat; run++) scenario(context, threat);
        }
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
            if (threat == Threat.CROWD) {
                // Hurt, at night, three zombies closing in: dig in, heal, wait for the sun to deal with them.
                world.getServer().runCommand("/execute in minecraft:overworld run fill -24 95 -24 24 99 24 minecraft:dirt");
                world.getServer().runCommand("/give @p minecraft:dirt 16");
                world.getServer().runCommand("/clear @p minecraft:bread");
                // A player already low: dig in at first sight rather than trading hits first.
                context.runOnClient(client -> Baritone.settings().guardianFleeHealth.value = 14);
                context.waitTicks(80); // past the spawn invulnerability, or the damage doesn't land
                world.getServer().runCommand("/damage @p 12");
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
                    .anyMatch(stack -> stack.is(threat == Threat.CROWD ? Items.DIRT : Items.BREAD)));
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
                    case CROWD -> {
                        world.getServer().runCommand("/summon minecraft:zombie -10 100 0 {PersistenceRequired:1b}");
                        world.getServer().runCommand("/summon minecraft:zombie 0 100 10 {PersistenceRequired:1b}");
                        world.getServer().runCommand("/summon minecraft:zombie 0 100 -10 {PersistenceRequired:1b}");
                    }
                }
                if (threat == Threat.CROWD) {
                    // Every change of health, block or status on the way down, to see what a death came from.
                    String[] last = {""};
                    context.waitFor(client -> {
                        if (client.player == null) return true;
                        String now = "hp " + Math.round(client.player.getHealth()) + " at " + client.player.blockPosition().toShortString()
                                + " status " + guardian().status();
                        if (!now.equals(last[0])) {
                            last[0] = now;
                            events.add("  tick " + client.player.tickCount + " " + now + " zombies " + client.level.getEntities(
                                    (net.minecraft.world.entity.Entity) null, client.player.getBoundingBox().inflate(3),
                                    e -> e.getType().toShortString().equals("zombie")).stream()
                                    .map(z -> String.format("(%.1f %.1f %.1f)", z.getX(), z.getY(), z.getZ())).toList());
                        }
                        return client.player.isDeadOrDying()
                                || guardian().log().recent().stream().anyMatch(e -> e.text().contains("sheltering"));
                    }, 30 * 20);
                    context.takeScreenshot("guardian-crowd-sheltered");
                    for (int second = 0; second < 10; second++) {
                        context.waitTicks(20);
                        events.add("t+" + second + "s " + context.computeOnClient(client -> {
                            if (client.player == null) return "no player";
                            var pos = client.player.blockPosition();
                            var level = client.level;
                            return "at " + pos.toShortString() + " hp " + Math.round(client.player.getHealth())
                                    + " ground " + client.player.onGround()
                                    + " above " + level.getBlockState(pos.above(2)).getBlock().getName().getString()
                                    + " head N/S/E/W " + level.getBlockState(pos.above().north()).isAir()
                                    + "/" + level.getBlockState(pos.above().south()).isAir()
                                    + "/" + level.getBlockState(pos.above().east()).isAir()
                                    + "/" + level.getBlockState(pos.above().west()).isAir()
                                    + " feet/head " + level.getBlockState(pos).getBlock().getName().getString()
                                    + "/" + level.getBlockState(pos.above()).getBlock().getName().getString()
                                    + " inWall " + client.player.isInWall()
                                    + " status " + guardian().status();
                        }) + " | " + world.getServer().computeOnServer(server -> {
                            // The server's view: a dig it rejected leaves the player inside a block it can't see.
                            var player = server.getPlayerList().getPlayers().get(0);
                            var pos = player.blockPosition();
                            var source = player.getLastDamageSource();
                            return "server at " + pos.toShortString() + " hp " + Math.round(player.getHealth())
                                    + " feet " + player.level().getBlockState(pos).getBlock().getName().getString()
                                    + " head " + player.level().getBlockState(pos.above()).getBlock().getName().getString()
                                    + " hurt by " + (source == null ? "-" : source.getMsgId()
                                    + (source.getEntity() == null ? "" : " " + source.getEntity().getType().toShortString()
                                    + " at " + source.getEntity().blockPosition().toShortString()))
                                    + " zombies " + player.level().getEntities((net.minecraft.world.entity.Entity) null,
                                            player.getBoundingBox().inflate(4), e -> e.getType().toShortString().equals("zombie")).stream()
                                            .map(z -> z.position().toString()).toList();
                        }));
                    }
                    world.getServer().runCommand("/time set 1000");
                }
                context.waitFor(client -> {
                    if (client.player == null || client.player.isDeadOrDying()) died[0] = true;
                    return ended[0] || died[0];
                }, TIMEOUT_TICKS);
            } finally {
                context.runOnClient(client -> {
                    AcquireControl acquire = AcquireControl.get();
                    if (acquire != null) acquire.stop();
                    Baritone.settings().guardianFleeHealth.value = Baritone.settings().guardianFleeHealth.defaultValue;
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
            String expected = threat == Threat.LAVA ? "lava" : threat == Threat.CROWD ? "digging in" : label;
            if (!log.contains(expected)) throw new AssertionError(label + ": the Guardian never dealt with it\n" + trail);
        }
    }

    private static GuardianProcess guardian() {
        return ((Baritone) BaritoneAPI.getProvider().getPrimaryBaritone()).getGuardianProcess();
    }
}
