package dihclient.security;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DihItemNbtSanityTest {
    @Test
    void tooltipUnderCapIsUntouched() {
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < 10; i++) lines.add(Component.literal("line " + i));
        List<Component> snapshot = List.copyOf(lines);

        assertFalse(DihItemNbtSanity.trimTooltipLines(lines));
        assertEquals(snapshot, lines);
    }

    @Test
    void tooltipAtCapIsUntouched() {
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < DihItemNbtSanity.MAX_TOOLTIP_LINES; i++) lines.add(Component.literal("line " + i));

        assertFalse(DihItemNbtSanity.trimTooltipLines(lines));
        assertEquals(DihItemNbtSanity.MAX_TOOLTIP_LINES, lines.size());
    }

    @Test
    void tooltipOverCapIsTrimmedWithNotice() {
        int total = DihItemNbtSanity.MAX_TOOLTIP_LINES + 100;
        int expectedHidden = total - (DihItemNbtSanity.MAX_TOOLTIP_LINES - 1);
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < total; i++) lines.add(Component.literal("line " + i));

        assertTrue(DihItemNbtSanity.trimTooltipLines(lines));
        assertEquals(DihItemNbtSanity.MAX_TOOLTIP_LINES, lines.size());

        assertEquals("line 0", lines.get(0).getString());
        assertEquals("line " + (DihItemNbtSanity.MAX_TOOLTIP_LINES - 2),
            lines.get(DihItemNbtSanity.MAX_TOOLTIP_LINES - 2).getString());
        String notice = lines.get(lines.size() - 1).getString();
        assertTrue(notice.contains(String.valueOf(expectedHidden)));
        assertTrue(notice.contains("tooltip lines hidden"));
    }

    @Test
    void unsafeTooltipLineIsReplaced() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("safe line"));
        lines.add(Component.literal("x".repeat(20_000)));
        lines.add(Component.literal("another safe line"));

        assertEquals(1, DihItemNbtSanity.scrubUnsafeTooltipLines(lines));
        assertEquals("safe line", lines.get(0).getString());
        assertEquals("[unsafe tooltip line removed]", lines.get(1).getString());
        assertEquals("another safe line", lines.get(2).getString());
    }

    @Test
    void safeTooltipLinesAreNotScrubbed() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Firework Rocket"));
        lines.add(Component.literal("Bytes: 4096 (4.0 KiB)"));

        assertEquals(0, DihItemNbtSanity.scrubUnsafeTooltipLines(lines));
        assertEquals("Firework Rocket", lines.get(0).getString());
    }
}
