package dihclient.util;

//? if >=26.3 {
/*import com.mojang.renderpearl.api.device.DeviceType;
*///?} else {
import com.mojang.blaze3d.systems.DeviceType;
//?}
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihEquipmentAndLightUiTest {
    @Test
    void enchantmentAbbreviations() {
        assertEquals("Sha", DihEquipment.abbreviate("sharpness"));
        assertEquals("FA", DihEquipment.abbreviate("fire_aspect"));
        assertEquals("PP", DihEquipment.abbreviate("projectile_protection"));
        assertEquals("Ma", DihEquipment.abbreviate("ma"));
    }

    @Test
    void levelNamesAreRomanThenDigits() {
        assertEquals("I", DihEquipment.levelName(1));
        assertEquals("IV", DihEquipment.levelName(4));
        assertEquals("X", DihEquipment.levelName(10));
        assertEquals("11", DihEquipment.levelName(11));
        assertEquals("255", DihEquipment.levelName(255));
    }

    @Test
    void wrapBreaksOnlyBetweenParts() {
        List<String> lines = DihEquipment.wrapJoined(List.of("Sharpness V", "Unbreaking III", "Mending"), 30, String::length);
        assertEquals(List.of("Sharpness V, Unbreaking III,", "Mending"), lines);
        assertEquals(List.of("Sharpness V"), DihEquipment.wrapJoined(List.of("Sharpness V"), 3, String::length));
        assertTrue(DihEquipment.wrapJoined(List.of(), 30, String::length).isEmpty());
    }

    @Test
    void lightUiModesCycleAndNormalize() {
        assertEquals("AUTO", DihLightUi.normalize(null));
        assertEquals("AUTO", DihLightUi.normalize("bogus"));
        assertEquals("ON", DihLightUi.normalize(" on "));
        assertEquals("ON", DihLightUi.next("AUTO", true));
        assertEquals("OFF", DihLightUi.next("ON", true));
        assertEquals("AUTO", DihLightUi.next("OFF", true));
        assertEquals("OFF", DihLightUi.next("AUTO", false));
    }

    @Test
    void autoLightUiTargetsWeakHardware() {
        assertTrue(DihLightUi.isWeak(DeviceType.INTEGRATED, 8));
        assertTrue(DihLightUi.isWeak(DeviceType.CPU, 8));
        assertTrue(DihLightUi.isWeak(DeviceType.DISCRETE, 2));
        assertFalse(DihLightUi.isWeak(DeviceType.DISCRETE, 8));
    }
}
