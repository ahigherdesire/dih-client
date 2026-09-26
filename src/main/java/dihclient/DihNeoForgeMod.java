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
     * Dev check (-Ddih.auditAndExit=true): ten seconds after start, apply every mixin (a broken one throws), log the
     * result and quit. NeoForge has no client game tests, so this is how a build is smoke-tested.
     ^/
    private static void auditAndExit() {
        int[] ticks = {0};
        DihPlatform.onEndClientTick(client -> {
            if (++ticks[0] != 200) return; // ten seconds in: past resource loading, on the title screen
            try {
                org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
                DihClientAddon.LOG.info("[DIH] Mixin audit passed");
            } catch (Throwable t) {
                DihClientAddon.LOG.error("[DIH] Mixin audit FAILED", t);
            }
            client.stop();
        });
    }
}
*///?}
