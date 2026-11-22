/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.api.command;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Host-provided formatting for user-facing command references, independent of the client implementation. */
public final class CommandText {

    private static volatile UnaryOperator<String> formatter = UnaryOperator.identity();

    private CommandText() {
    }

    /**
     * Atomically replaces the formatter used by subsequent calls. The callback may be called concurrently
     * and must be thread-safe. Install {@link UnaryOperator#identity()} to restore standalone behavior.
     *
     * @param formatter non-null callback that returns formatted text
     */
    public static void formatter(UnaryOperator<String> formatter) {
        CommandText.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    /** Applies the current formatter once; defaults to returning the text unchanged. */
    public static String format(String text) {
        return formatter.apply(text);
    }

    /**
     * Resolves a component into detached literal runs with their effective styles. Formats visible text,
     * suggested commands and text hovers, while preserving executable click commands and other events.
     * One callback snapshot is used throughout the component and its nested text hovers.
     */
    public static Component format(Component component) {
        return formatComponent(component, formatter);
    }

    private static Component formatComponent(Component component, UnaryOperator<String> format) {
        MutableComponent output = Component.empty();
        component.visit((style, text) -> {
            if (text.isEmpty()) return Optional.empty();
            Style formattedStyle = style;
            if (style.getClickEvent() instanceof ClickEvent.SuggestCommand suggest) {
                formattedStyle = formattedStyle.withClickEvent(new ClickEvent.SuggestCommand(format.apply(suggest.command())));
            }
            if (style.getHoverEvent() instanceof HoverEvent.ShowText hover) {
                formattedStyle = formattedStyle.withHoverEvent(new HoverEvent.ShowText(formatComponent(hover.value(), format)));
            }
            output.append(Component.literal(format.apply(text)).setStyle(formattedStyle));
            return Optional.empty();
        }, Style.EMPTY);
        return output;
    }
}
