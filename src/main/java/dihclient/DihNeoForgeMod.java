package dihclient;

//? if neoforge {
/*import dihclient.platform.DihPlatform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/^* NeoForge entry point: the Fabric entrypoints (preLaunch, client) run once Minecraft exists (see DihPlatform). ^/
@Mod(value = "dih", dist = Dist.CLIENT)
public final class DihNeoForgeMod {
    public DihNeoForgeMod(IEventBus modBus) {
        DihPlatform.initNeoForge(modBus, () -> {
            new DihLegacyMigration().onPreLaunch();
            new DihClientMod().onInitializeClient();
            if (Boolean.getBoolean("dih.auditAndExit")) auditAndExit();
        });
    }

    /^*
     * Dev smoke test (-Ddih.auditAndExit=true, gradlew runClient -PdihAudit): ten seconds in, apply every mixin (a broken
     * one throws); then create a flat creative world, play it for ten seconds and quit. NeoForge has no client game
     * tests, so this is how a NeoForge build is checked. Each step logs "[DIH] Smoke".
     ^/
    private static void auditAndExit() {
        int[] ticks = {0};
        int[] inWorld = {0};
        boolean[] worldStarted = {false};
        DihPlatform.onEndClientTick(client -> {
            ticks[0]++;
            if (ticks[0] == 200) { // past resource loading, on the title screen
                try {
                    org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
                    DihClientAddon.LOG.info("[DIH] Smoke: mixin audit passed");
                } catch (Throwable t) {
                    DihClientAddon.LOG.error("[DIH] Smoke: mixin audit FAILED", t);
                    client.stop();
                    return;
                }
                worldStarted[0] = true;
                client.createWorldOpenFlows().createFreshLevel("dih-smoke-" + System.currentTimeMillis(),
                    new net.minecraft.world.level.LevelSettings("dih-smoke", net.minecraft.world.level.GameType.CREATIVE,
                        net.minecraft.world.level.LevelSettings.DifficultySettings.DEFAULT, true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(12345L, false, false),
                    registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),
                    new net.minecraft.client.gui.screens.TitleScreen());
                return;
            }
            if (!worldStarted[0]) return;
            if (client.player != null && client.level != null && ++inWorld[0] == 200) {
                DihClientAddon.LOG.info("[DIH] Smoke: played a singleplayer world for 10 s; quitting");
                client.stop();
            } else if (ticks[0] > 200 + 20 * 120 && inWorld[0] == 0) {
                DihClientAddon.LOG.error("[DIH] Smoke: FAILED, the world did not load in two minutes");
                client.stop();
            }
        });
    }
}
*///?}
