package dihclient.modules;

import dihclient.api.module.Setting;
import dihclient.util.DihConfig;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatModuleSettingsTest {
    private static final List<Supplier<Module>> MODULES = List.of(
        BuiltinModules.AimAssistModule::new,
        BuiltinModules.AutoClickerModule::new,
        BuiltinModules.TriggerBotModule::new,
        CrystalAuraModule::new,
        AnchorAuraModule::new,
        AutoArmorModule::new,
        AutoMaceModule::new,
        AutoTrapModule::new,
        ArrowDamageModule::new,
        BlockHitModule::new,
        BowAimAssistModule::new,
        CriticalsModule::new,
        DangerModule::new,
        MaceKillModule::new,
        ReachModule::new,
        SpearKillModule::new,
        WTapModule::new,
        STapModule::new);

    @BeforeAll
    static void inMemoryConfig() {
        // Some modules keep item and block sets in static fields, which need the registries.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // Outside the game there is no config folder to load from, so modules keep their state in memory.
        DihConfig config = new DihConfig();
        // AutoArmor turns itself on (and saves the config) the first time it is built.
        config.modules.put("auto-armor", new DihConfig.ModuleState());
        DihConfig.setGlobal(config);
    }

    @Test
    void everySettingIsGroupedAndDescribed() {
        for (Supplier<Module> factory : MODULES) {
            Module module = factory.get();
            List<Setting<?, ?>> settings = module.settings();
            assertEquals("General", settings.getFirst().group(), module.name() + " starts with General");
            for (Setting<?, ?> setting : settings) {
                String where = module.name() + "." + setting.id();
                assertFalse(setting.group() == null || setting.group().isBlank(), where + " has a group");
                assertFalse(setting.description() == null || setting.description().isBlank(),
                    where + " has a description");
            }
        }
    }

    @Test
    void everyModuleHasARealSet() {
        for (Supplier<Module> factory : MODULES) {
            Module module = factory.get();
            assertTrue(module.settings().size() >= 7, module.name() + " has " + module.settings().size());
        }
    }

    @Test
    void visibilityRulesEvaluate() {
        for (Supplier<Module> factory : MODULES) {
            Module module = factory.get();
            for (Setting<?, ?> setting : module.settings()) {
                assertDoesNotThrow(setting::isVisible, module.name() + "." + setting.id());
            }
        }
    }

    @Test
    void constantsTurnedSettingsKeepTheirOldValues() {
        assertDefault(new CriticalsModule(), "max-hold", Integer.toString(CriticalsModule.MAX_HOLD_TICKS));
        assertDefault(new MaceKillModule(), "min-height", Integer.toString(MaceKillModule.MIN_HEIGHT));
        assertDefault(new AutoTrapModule(), "switch-back-delay", "2");
        assertDefault(new AutoTrapModule(), "pickup-delay", "30");
        assertDefault(new DangerModule(), "hud-lines", "3");
        assertDefault(new DangerModule(), "urgent-time", "10");
        assertDefault(new DangerModule(), "deflect-cooldown", "4");
        assertDefault(new BuiltinModules.AimAssistModule(), "switch-delay", "4");
        assertDefault(new AutoArmorModule(), "expected-damage", "6");
        assertDefault(new BuiltinModules.TriggerBotModule(), "min-charge", "100");
    }

    private static void assertDefault(Module module, String id, String expected) {
        Setting<?, ?> setting = module.setting(id);
        assertNotNull(setting, module.name() + "." + id);
        assertEquals(expected, setting.defaultValue(), module.name() + "." + id);
    }

    @Test
    void tapCooldownSpacesTaps() {
        assertTrue(SprintResetModule.cooledDown(Integer.MIN_VALUE, 5, 10), "first tap");
        assertTrue(SprintResetModule.cooledDown(100, 101, 0), "no cooldown");
        assertFalse(SprintResetModule.cooledDown(100, 105, 10), "too soon");
        assertTrue(SprintResetModule.cooledDown(100, 110, 10), "cooled down");
    }

    @Test
    void itemListsMatchWithOrWithoutNamespace() {
        CombatFilters.IdSet ids = new CombatFilters.IdSet();
        List<String> entries = List.of(" Minecraft:Mace ", "diamond_sword");
        assertTrue(ids.contains(entries, "minecraft:mace"));
        assertTrue(ids.contains(entries, "minecraft:diamond_sword"));
        assertFalse(ids.contains(entries, "minecraft:stick"));
        assertEquals(Set.of("minecraft:mace", "mace"), CombatFilters.normalize(List.of("minecraft:mace", " ")));
    }

    @Test
    void defaultWeaponsCoverEveryMeleeWeapon() {
        Set<String> weapons = CombatFilters.normalize(List.of(CombatFilters.DEFAULT_WEAPONS.split("\\|")));
        for (String id : List.of("netherite_sword", "wooden_axe", "iron_spear", "mace", "trident")) {
            assertTrue(weapons.contains(id), id);
        }
    }

    @Test
    void angleBetweenDirections() {
        assertEquals(0.0D, CombatFilters.angleBetween(new Vec3(1, 0, 0), new Vec3(3, 0, 0)), 1.0E-9D);
        assertEquals(90.0D, CombatFilters.angleBetween(new Vec3(1, 0, 0), new Vec3(0, 0, 2)), 1.0E-9D);
        assertEquals(180.0D, CombatFilters.angleBetween(new Vec3(0, 1, 0), new Vec3(0, -1, 0)), 1.0E-9D);
        assertEquals(0.0D, CombatFilters.angleBetween(Vec3.ZERO, new Vec3(0, 1, 0)), 1.0E-9D);
    }
}
