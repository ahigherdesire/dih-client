package dihclient;

//? if forge {
/*import dihclient.platform.DihPlatform;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/^* Forge entry point: the Fabric entrypoints (preLaunch, client) run once Minecraft exists (see DihPlatform). ^/
@Mod("dih")
public final class DihForgeMod {
    public DihForgeMod(FMLJavaModLoadingContext context) {
        DihPlatform.setClientStart(() -> {
            new DihLegacyMigration().onPreLaunch();
            new DihClientMod().onInitializeClient();
            if (Boolean.getBoolean("dih.auditAndExit")) DihSmokeTest.start();
        });
    }
}
*///?}
