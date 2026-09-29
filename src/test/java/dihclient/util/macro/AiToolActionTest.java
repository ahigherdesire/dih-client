package dihclient.util.macro;

import dihclient.util.DihMacro;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiToolActionTest {
    @Test
    void roundTripsThroughAMacro() {
        AiToolAction action = new AiToolAction();
        action.tool = "find";
        action.args = "oak_log";
        action.allowDangerous = true;
        DihMacro macro = new DihMacro();
        macro.name = "tools";
        macro.actions = new ArrayList<>(List.of(action));

        DihMacro restored = new DihMacro().fromTag(macro.toTag());

        AiToolAction back = assertInstanceOf(AiToolAction.class, restored.actions.get(0));
        assertEquals("find", back.tool);
        assertEquals("oak_log", back.args);
        assertTrue(back.allowDangerous);
        assertEquals("Tool find oak_log", back.getDisplayName());
        assertEquals(MacroActionType.AI_TOOL, back.getType());
    }

    @Test
    void missingFieldsTakeDefaults() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "AI_TOOL");
        AiToolAction back = assertInstanceOf(AiToolAction.class, DihMacro.createActionFromTag(tag));
        assertEquals("", back.tool);
        assertEquals("", back.args);
        assertFalse(back.allowDangerous);
        assertEquals("Tool (none)", back.getDisplayName());
    }

    @Test
    void raceActionsCanHoldIt() {
        assertInstanceOf(AiToolAction.class, RaceAction.createBodyAction("AI_TOOL"));
    }
}
