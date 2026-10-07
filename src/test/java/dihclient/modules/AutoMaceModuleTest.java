package dihclient.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoMaceModuleTest {
    @Test
    void inventoryIndexesMapToPlayerMenuSlots() {
        assertEquals(36, AutoMaceModule.menuSlotOf(0));
        assertEquals(44, AutoMaceModule.menuSlotOf(8));
        assertEquals(9, AutoMaceModule.menuSlotOf(9));
        assertEquals(35, AutoMaceModule.menuSlotOf(35));
        assertEquals(6, AutoMaceModule.CHEST_MENU_SLOT);
    }
}
