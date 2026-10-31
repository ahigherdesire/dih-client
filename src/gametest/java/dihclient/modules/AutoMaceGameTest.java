package dihclient.modules;

import com.mojang.authlib.GameProfile;
import dihclient.util.DihConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * AutoMace against a real integrated server: the player holds a stick, keeps a Wind Burst mace in the hotbar and drops
 * from 12 blocks onto a still iron golem while looking straight ahead, not at it. With Aim on Nearby it must swap to
 * the mace and smash, get thrown back up by Wind Burst, and smash again on the way down (the client's fall distance
 * never resets during that chain, which used to stop the second hit), then go back to the stick. With Aim on
 * Crosshair the same drop must not hit. A player raising a shield must take no damage from the plain smash, then lose
 * health to Stun Slam, which breaks the shield with a hotbar axe and smashes in the same tick. It has to be a player:
 * only a player's shield can be disabled.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AutoMaceGameTest implements FabricClientGameTest {
    private static final String TAG = "[AutoMaceGameTest] ";

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> DihConfig.getGlobal().customMainMenu = false);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            world.getServer().runCommand("/difficulty peaceful");
            world.getServer().runCommand("/gamemode survival @a");
            world.getServer().runCommand("/item replace entity @a hotbar.0 with minecraft:stick");
            world.getServer().runCommand(
                "/item replace entity @a hotbar.3 with minecraft:mace[minecraft:enchantments={\"minecraft:wind_burst\":1}]");
            context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
            context.waitTicks(40);
            try {
                context.runOnClient(client -> {
                    Module mace = ModuleRegistry.get("auto-mace");
                    mace.setValue("targets", "All Living");
                    mace.setValue("aim", "Nearby");
                    mace.setValue("prefer", "Wind Burst");
                    mace.setValue("swap-back", "true");
                    mace.setValue("swap-back-delay", "2");
                    mace.setEnabled(true);
                });
                nearbyChain(context, world);
                System.out.println(TAG + "nearby chain ok");

                context.runOnClient(client -> ModuleRegistry.get("auto-mace").setValue("aim", "Crosshair"));
                float untouched = dropOnGolem(context, world, 60);
                if (untouched != 100.0F) {
                    throw new AssertionError("Crosshair aim must not hit a golem it is not looking at, health " + untouched);
                }
                System.out.println(TAG + "crosshair ok");

                stunSlam(context, world);
                System.out.println(TAG + "stun slam ok");
            } catch (Throwable t) {
                System.out.println(TAG + "FAILED: " + t);
                throw t;
            } finally {
                context.runOnClient(client -> ModuleRegistry.get("auto-mace").setEnabled(false));
            }
        }
        context.setScreen(TitleScreen::new);
    }

    private static void stunSlam(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("/kill @e[type=minecraft:iron_golem]");
        world.getServer().runCommand("/item replace entity @a hotbar.4 with minecraft:diamond_axe");
        context.runOnClient(client -> {
            Module mace = ModuleRegistry.get("auto-mace");
            mace.setValue("aim", "Nearby");
            mace.setValue("stun-slam", "false");
        });
        FakePlayer blocker = world.getServer().computeOnServer(server -> {
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            FakePlayer fake = new Blocker(level);
            fake.snapTo(0.5D, -60.0D, 0.5D, 0.0F, 0.0F);
            fake.setYHeadRot(0.0F);
            fake.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fake)));
            level.addFreshEntity(fake);
            // Until its client reports in, a server player takes no damage at all.
            fake.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            fake.startUsingItem(InteractionHand.OFF_HAND);
            return fake;
        });
        try {
            float blocked = dropOnBlocker(context, world, blocker);
            System.out.println(TAG + "smash into a shield leaves " + blocked);
            if (blocked < 20.0F) throw new AssertionError("a raised shield should stop the plain smash, health " + blocked);

            context.waitFor(client -> client.player.onGround(), 200);
            context.runOnClient(client -> ModuleRegistry.get("auto-mace").setValue("stun-slam", "true"));
            float slammed = dropOnBlocker(context, world, blocker);
            System.out.println(TAG + "stun slam leaves " + slammed);
            if (slammed >= 20.0F) throw new AssertionError("Stun Slam never got through the shield");
            context.waitFor(client -> client.player.onGround(), 200);
        } finally {
            world.getServer().runOnServer(server -> blocker.discard());
        }
    }

    /** A FakePlayer that can be hurt: Fabric's own is immune to all damage, so no hit would ever register. */
    private static final class Blocker extends FakePlayer {
        Blocker(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "Blocker"));
        }

        @Override
        public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
            return false;
        }
    }

    /** Drops the player onto the front of a server-side player holding a shield up; returns its health. */
    private static float dropOnBlocker(ClientGameTestContext context, TestSingleplayerContext world, FakePlayer blocker) {
        world.getServer().runCommand("/tp @a 0.5 -60 3.5 0 0");
        context.waitFor(client -> client.player.onGround(), 40);
        for (int i = 0; i < 10; i++) {
            world.getServer().runOnServer(server -> blocker.doTick());
            context.waitTick();
        }
        world.getServer().runCommand("/tp @a 0.5 -48 1.8 0 0");
        for (int i = 0; i < 60; i++) {
            world.getServer().runOnServer(server -> {
                if (blocker.isAlive()) blocker.doTick();
            });
            context.waitTick();
            if (world.getServer().computeOnServer(server -> blocker.getHealth()) < 20.0F) break;
        }
        return world.getServer().computeOnServer(server -> blocker.getHealth());
    }

    private static void nearbyChain(ClientGameTestContext context, TestSingleplayerContext world) {
        float first = dropOnGolem(context, world, 60);
        System.out.println(TAG + "first smash leaves " + first);
        if (first >= 100.0F) throw new AssertionError("AutoMace never smashed the golem below");

        context.waitFor(client -> client.player.getDeltaMovement().y > 0.3D, 20);
        System.out.println(TAG + "wind burst launched the player");

        float second = waitForGolemBelow(context, world, first, 100);
        System.out.println(TAG + "second smash leaves " + second);
        if (second >= first) throw new AssertionError("after the Wind Burst launch AutoMace never smashed again");

        context.waitFor(client -> client.player.onGround(), 200);
        context.waitTicks(10);
        int slot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
        if (slot != 0) throw new AssertionError("Swap Back should return to the stick in slot 0, holding " + slot);
    }

    /** Puts a fresh still golem on the pad, drops the player 12 blocks above it looking level and returns its health. */
    private static float dropOnGolem(ClientGameTestContext context, TestSingleplayerContext world, int ticks) {
        world.getServer().runCommand("/kill @e[type=minecraft:iron_golem]");
        world.getServer().runCommand("/tp @a 0.5 -60 3.5 0 0");
        context.waitFor(client -> client.player.onGround(), 40);
        world.getServer().runCommand("/summon minecraft:iron_golem 0.5 -60 0.5 {NoAI:1b}");
        context.waitTicks(10);
        world.getServer().runCommand("/tp @a 0.5 -48 0.5 0 0");
        return waitForGolemBelow(context, world, 100.0F, ticks);
    }

    /** Waits up to {@code ticks} for the golem's health to drop under {@code health}; returns its health either way. */
    private static float waitForGolemBelow(ClientGameTestContext context, TestSingleplayerContext world, float health, int ticks) {
        float now = golemHealth(world);
        for (int i = 0; i < ticks && now >= health; i++) {
            context.waitTick();
            now = golemHealth(world);
        }
        return now;
    }

    private static float golemHealth(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> {
            List<? extends LivingEntity> golems = server.getLevel(Level.OVERWORLD).getEntities(EntityTypes.IRON_GOLEM, LivingEntity::isAlive);
            return golems.isEmpty() ? 0.0F : golems.get(0).getHealth();
        });
    }
}
