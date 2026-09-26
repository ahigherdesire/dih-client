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
            if (Boolean.getBoolean("dih.auditAndExit")) DihSmokeTest.start();
        });
    }
}
*///?}
