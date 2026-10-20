package dihclient.modules;

import dihclient.api.module.Setting;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MovementModuleSettingsTest {
    @TempDir
    static Path gameDir;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        ensureGameDir(gameDir);
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static List<Module> movementModules() {
        return List.of(
            new BuiltinModules.FlightModule(), new BuiltinModules.SpeedModule(), new BuiltinModules.SprintModule(),
            new BuiltinModules.SneakModule(), new BuiltinModules.ParkourModule(), new BuiltinModules.InvMoveModule(),
            new BuiltinModules.NoFallModule(), new AirJumpModule(), new BoatFlyModule(), new ClutchModule(),
            new PearlChargeModule(), new EntityControlModule(), new NoClipModule(), new SafeWalkModule(),
            new ScaffoldModule());
    }

    @Test
    void everyMovementSettingIsGroupedAndDescribed() {
        for (Module module : movementModules()) {
            assertFalse(module.settings().isEmpty(), module.id() + " has no settings");
            for (Setting<?, ?> setting : module.settings()) {
                String where = module.id() + "/" + setting.id();
                assertFalse(setting.group().isBlank(), where + " has no group");
                assertFalse(setting.description().isBlank(), where + " has no description");
            }
        }
    }

    @Test
    void theFirstGroupOfEveryMovementModuleIsGeneral() {
        for (Module module : movementModules()) {
            assertTrue("General".equals(module.settings().get(0).group()),
                module.id() + " starts with group " + module.settings().get(0).group());
        }
    }

    @Test
    void newSettingsDefaultToTheOldBehavior() {
        Module flight = new BuiltinModules.FlightModule();
        assertTrue("1.5".equals(flight.value("sprint-boost")), "Velocity sprint stays 15x of speed");
        assertTrue("0.0313".equals(flight.value("nudge")));
        assertTrue("40".equals(flight.value("min-durability")));

        Module speed = new BuiltinModules.SpeedModule();
        assertTrue("0.40123128".equals(speed.value("hop-height")));
        assertTrue("true".equals(speed.value("speed-effect")));

        Module noFall = new BuiltinModules.NoFallModule();
        assertTrue("0.5".equals(noFall.value("trigger-speed")));
        assertTrue("3.0".equals(noFall.value("place-height")));
        assertTrue("5".equals(noFall.value("reach-down")));

        Module clutch = new ClutchModule();
        assertTrue("0.08".equals(clutch.value("trigger-speed")));
        assertTrue("2".equals(clutch.value("search-radius")));
        assertTrue("Blocks".equals(clutch.value("clutch-with")), "Clutch keeps placing blocks by default");

        Module scaffold = new ScaffoldModule();
        assertTrue("true".equals(scaffold.value("auto-switch")));
        assertTrue("Smart".equals(scaffold.value("block-pick")));
    }

    private static synchronized void ensureGameDir(Path dir) {
        try {
            Object loader = net.fabricmc.loader.api.FabricLoader.getInstance();
            Class<?> impl = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl");
            Field gameDirField = impl.getDeclaredField("gameDir");
            gameDirField.setAccessible(true);
            if (gameDirField.get(loader) == null) {
                Method setter = impl.getDeclaredMethod("setGameDir", Path.class);
                setter.setAccessible(true);
                setter.invoke(loader, dir);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("could not initialize movement settings test game dir", t);
        }
    }
}
