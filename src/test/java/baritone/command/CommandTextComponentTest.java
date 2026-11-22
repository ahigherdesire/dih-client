package baritone.command;

import baritone.api.command.CommandText;
import baritone.api.command.IBaritoneChatControl;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

final class CommandTextComponentTest {

    @AfterEach
    void resetFormatter() {
        CommandText.formatter(UnaryOperator.identity());
    }

    @Test
    void identityPreservesRenderedTextAndEffectiveStyles() {
        Style parent = Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true)
                .withInsertion("#mine").withClickEvent(new ClickEvent.SuggestCommand("#mine diamond_ore"));
        Component input = Component.literal("Use ").setStyle(parent)
                .append(Component.literal("#mine").withStyle(ChatFormatting.ITALIC));

        Component output = CommandText.format(input);

        assertNotSame(input, output);
        assertEquals(runs(input), runs(output));
        assertEquals("Use #mine", output.getString());
    }

    @Test
    void formatsSuggestionPayloadAndNestedHoverTextWithoutChangingInput() {
        CommandText.formatter(text -> text.replace("#mine", ".mine"));
        Component nested = Component.literal("Nested #mine").withStyle(ChatFormatting.GREEN);
        Component hover = Component.literal("Try #mine").setStyle(Style.EMPTY
                .withClickEvent(new ClickEvent.SuggestCommand("#mine gold_ore"))
                .withHoverEvent(new HoverEvent.ShowText(nested)));
        Component input = Component.literal("#mine").setStyle(Style.EMPTY
                .withClickEvent(new ClickEvent.SuggestCommand("#mine diamond_ore"))
                .withHoverEvent(new HoverEvent.ShowText(hover)));

        Component output = CommandText.format(input);
        Style style = runs(output).getFirst().style();
        assertEquals(".mine", output.getString());
        assertEquals(".mine diamond_ore", ((ClickEvent.SuggestCommand) style.getClickEvent()).command());
        Component formattedHover = ((HoverEvent.ShowText) style.getHoverEvent()).value();
        assertEquals("Try .mine", formattedHover.getString());
        Style hoverStyle = runs(formattedHover).getFirst().style();
        assertEquals(".mine gold_ore", ((ClickEvent.SuggestCommand) hoverStyle.getClickEvent()).command());
        Component formattedNested = ((HoverEvent.ShowText) hoverStyle.getHoverEvent()).value();
        assertEquals("Nested .mine", formattedNested.getString());
        assertEquals(runs(nested).getFirst().style(), runs(formattedNested).getFirst().style());
        assertEquals("#mine", input.getString());
        assertEquals("Try #mine", hover.getString());
        assertEquals("#mine diamond_ore", ((ClickEvent.SuggestCommand) input.getStyle().getClickEvent()).command());
    }

    @Test
    void forcedRunCommandsAndOtherClickActionsStayUntouched() {
        CommandText.formatter(text -> text.replace("#mine", ".mine"));
        ClickEvent run = new ClickEvent.RunCommand(IBaritoneChatControl.FORCE_COMMAND_PREFIX + "mine #mine");
        ClickEvent copy = new ClickEvent.CopyToClipboard("#mine");
        Component input = Component.literal("#mine").setStyle(Style.EMPTY.withClickEvent(run))
                .append(Component.literal("#mine").setStyle(Style.EMPTY.withClickEvent(copy)));

        List<Run> output = runs(CommandText.format(input));

        assertEquals(".mine", output.get(0).text());
        assertSame(run, output.get(0).style().getClickEvent());
        assertSame(copy, output.get(1).style().getClickEvent());
    }

    @Test
    void outputRunsAreDetachedAndEachTextRunIsFormattedOnce() {
        CommandText.formatter(text -> "[" + text + "]");
        MutableComponent child = Component.literal("child");
        MutableComponent input = Component.literal("root").append(child);

        Component output = CommandText.format(input);
        child.append(" changed");
        input.setStyle(Style.EMPTY.withBold(true));

        assertEquals("[root][child]", output.getString());
        assertFalse(runs(output).getFirst().style().isBold());
    }

    private static List<Run> runs(Component component) {
        List<Run> runs = new ArrayList<>();
        component.visit((style, text) -> {
            if (!text.isEmpty()) runs.add(new Run(text, style));
            return Optional.empty();
        }, Style.EMPTY);
        return runs;
    }

    private record Run(String text, Style style) { }
}
