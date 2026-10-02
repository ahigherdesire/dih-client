package dihclient.util.macro;

import dihclient.util.DihMacro;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TradeActionTest {
    @Test
    void roundTripsThroughAMacro() {
        TradeAction action = new TradeAction();
        action.rule = "unbreaking3";
        action.maxPrice = 20;
        action.count = 2;
        action.timeoutMs = 1500;
        DihMacro macro = new DihMacro();
        macro.name = "trades";
        macro.actions = new java.util.ArrayList<>(List.of(action));

        DihMacro restored = new DihMacro().fromTag(macro.toTag());

        TradeAction back = assertInstanceOf(TradeAction.class, restored.actions.get(0));
        assertEquals("unbreaking3", back.rule);
        assertEquals(20, back.maxPrice);
        assertEquals(2, back.count);
        assertEquals(1500, back.timeoutMs);
        assertEquals("Trade 2x unbreaking3 ≤20e", back.getDisplayName());
    }

    @Test
    void missingFieldsTakeDefaults() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "TRADE");
        TradeAction back = assertInstanceOf(TradeAction.class, DihMacro.createActionFromTag(tag));
        assertEquals("any", back.rule);
        assertEquals(1, back.count);
        assertEquals(0, back.maxPrice);
    }

    @Test
    void macrosSavedBeforeTradeExistedStillLoad() {
        CompoundTag old = new CompoundTag();
        old.putString("name", "old");
        ListTag actions = new ListTag();
        CompoundTag chat = new CompoundTag();
        chat.putString("type", "SEND_CHAT");
        chat.putString("message", "hi");
        actions.add(chat);
        old.put("actions", actions);

        DihMacro restored = new DihMacro().fromTag(old);

        assertEquals(1, restored.actions.size());
        assertInstanceOf(SendChatAction.class, restored.actions.get(0));
    }
}
