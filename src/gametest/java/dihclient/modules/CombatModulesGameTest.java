package dihclient.modules;

import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Criticals and Reach against a real integrated server. A wooden sword (4 damage) hits a 10-health pig standing still:
 * a plain hit leaves 6, a critical (x1.5) leaves 4. Criticals in both modes must leave 4 from a hit made on the ground;
 * with Reach off a pig 4.6 blocks away can't be targeted, with Reach at 5 it is targeted and the server takes the hit.
 */
@SuppressWarnings("UnstableApiUsage")
public final class CombatModulesGameTest implements FabricClientGameTest {
    private static final String TAG = "[CombatModulesGameTest] ";

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            world.getServer().runCommand("/gamemode survival @a");
            world.getServer().runCommand("/item replace entity @a weapon.mainhand with minecraft:wooden_sword");
            context.waitTicks(40);
            try {
                float plain = hitPig(context, world, 2.5D, null);
                System.out.println(TAG + "plain hit leaves " + plain);
                expect(plain, 6.0F, "a plain hit");

                float packet = hitPig(context, world, 2.5D, () -> enableCriticals(context, "Packet"));
                System.out.println(TAG + "packet crit leaves " + packet);
                expect(packet, 4.0F, "a Packet-mode critical");

                float jump = hitPig(context, world, 2.5D, () -> enableCriticals(context, "Jump"));
                System.out.println(TAG + "jump crit leaves " + jump);
                expect(jump, 4.0F, "a Jump-mode critical");
                context.runOnClient(client -> ModuleRegistry.get("criticals").setEnabled(false));

                reach(context, world);
                System.out.println(TAG + "reach ok");
            } catch (Throwable t) {
                System.out.println(TAG + "FAILED: " + t);
                throw t;
            } finally {
                context.runOnClient(client -> {
                    ModuleRegistry.get("criticals").setEnabled(false);
                    ModuleRegistry.get("reach").setEnabled(false);
                });
            }
        }
        context.setScreen(TitleScreen::new);
    }

    private static void enableCriticals(ClientGameTestContext context, String mode) {
        context.runOnClient(client -> {
            Module criticals = ModuleRegistry.get("criticals");
            criticals.setValue("mode", mode);
            criticals.setEnabled(true);
        });
    }

    private static void reach(ClientGameTestContext context, TestSingleplayerContext world) {
        float without = hitPig(context, world, 5.0D, null);
        // The pig's near side is 4.6 blocks from the eyes: past the vanilla 3, inside Reach's 5.
        if (without != 10.0F) throw new AssertionError("without Reach a pig 4.6 blocks away must be out of reach, health " + without);

        context.runOnClient(client -> {
            Module reach = ModuleRegistry.get("reach");
            reach.setValue("entity-range", "5.0");
            reach.setEnabled(true);
        });
        context.waitTicks(2);
        double range = context.computeOnClient(client -> client.player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
        if (Math.abs(range - 5.0D) > 1.0E-6D) throw new AssertionError("Reach at 5 should set the entity range to 5, got " + range);
        float with = hitPig(context, world, 5.0D, null);
        expect(with, 6.0F, "a hit with Reach at 5");

        context.runOnClient(client -> ModuleRegistry.get("reach").setEnabled(false));
        context.waitTicks(2);
        double restored = context.computeOnClient(client -> client.player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE));
        if (Math.abs(restored - 3.0D) > 1.0E-6D) throw new AssertionError("turning Reach off should restore range 3, got " + restored);
    }

    /**
     * Stands the player on the pad, puts a still 10-health pig {@code distance} blocks east, swings once at it with
     * full attack strength and returns its health afterwards (10 if the swing never reached it).
     */
    private static float hitPig(ClientGameTestContext context, TestSingleplayerContext world, double distance, Runnable setup) {
        world.getServer().runCommand("/kill @e[type=minecraft:pig]");
        world.getServer().runCommand("/tp @a 0.5 -60 0.5 -90 0");
        world.getServer().runCommand("/summon minecraft:pig " + (0.5D + distance) + " -60 0.5 {NoAI:1b,Health:10f,Attributes:[{id:\"minecraft:max_health\",base:10d}]}");
        if (setup != null) setup.run();
        context.waitTicks(30);
        context.waitFor(client -> client.player.onGround(), 40);
        context.runOnClient(client -> {
            Vec3 eye = client.player.getEyePosition();
            Vec3 target = new Vec3(0.5D + distance, -60.0D + 0.45D, 0.5D);
            Vec3 d = target.subtract(eye);
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
        });
        context.waitTicks(3);
        boolean aimed = context.computeOnClient(client -> client.hitResult instanceof EntityHitResult);
        System.out.println(TAG + "distance " + distance + " targeted=" + aimed);
        context.getInput().pressMouse(0);
        context.waitTicks(25);
        return world.getServer().computeOnServer(server -> {
            List<? extends LivingEntity> pigs = server.getLevel(Level.OVERWORLD).getEntities(EntityTypes.PIG, pig -> true);
            if (pigs.isEmpty()) throw new AssertionError("the pig is gone");
            return pigs.get(0).getHealth();
        });
    }

    private static void expect(float health, float wanted, String what) {
        if (Math.abs(health - wanted) > 0.01F) throw new AssertionError(what + " should leave the pig at " + wanted + ", got " + health);
    }
}
