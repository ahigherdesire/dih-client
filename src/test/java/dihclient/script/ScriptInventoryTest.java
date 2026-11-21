package dihclient.script;

import dihclient.script.lang.ScriptException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScriptInventoryTest {
    @Test void slotsPreserveUserNumberingAndOffhand() {
        assertEquals(0,ScriptInventory.slotIndex(1.0));
        assertEquals(35,ScriptInventory.slotIndex(36.0));
        assertEquals(40,ScriptInventory.slotIndex("offhand"));
        for(Object invalid:List.of(0.0,37.0,1.5,Double.NaN,"1","helmet"))
            assertThrows(ScriptException.class,()->ScriptInventory.slotIndex(invalid));
    }
}
