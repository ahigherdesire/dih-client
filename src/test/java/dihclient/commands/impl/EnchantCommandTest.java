package dihclient.commands.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnchantCommandTest {
    @Test
    void levelsAreCutToWhatItemsStore() {
        assertEquals(255, EnchantCommand.clampLevel(10_000));
        assertEquals(255, EnchantCommand.clampLevel(Integer.MAX_VALUE));
        assertEquals(5, EnchantCommand.clampLevel(5));
        assertEquals(1, EnchantCommand.clampLevel(0));
    }

    @Test
    void enchantmentNamesNormalize() {
        assertEquals("fire_aspect", EnchantCommand.normalize(" Fire-Aspect "));
        assertEquals("sharpness", EnchantCommand.normalize("SHARPNESS"));
    }
}
