package dihclient.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dihclient.commands.DihCommandSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShowRegionCommandTest {
    @Test
    void acceptsSixBlockCoordinatesAndClear() {
        var dispatcher = dispatcher();
        assertExecutable(dispatcher, "showregion -10 64 30 5 80 -20");
        assertExecutable(dispatcher, "showregion 0 0 0 0 0 0");
        assertExecutable(dispatcher, "showregion clear");
    }

    @Test
    void rejectsIncompleteExtraAndInvalidCoordinates() {
        var dispatcher = dispatcher();
        for (String input : new String[] {
            "showregion 1 2 3 4 5", "showregion 1 2 3 4 5 6 7",
            "showregion 1 2 nope 4 5 6", "showregion 1.5 2 3 4 5 6",
            "showregion 2147483648 2 3 4 5 6", "showregion clear extra"
        }) {
            var result = dispatcher.parse(input, DihCommandSource.INSTANCE);
            assertTrue(result.getReader().canRead() || !result.getExceptions().isEmpty()
                || result.getContext().build(input).getCommand() == null, input);
        }
    }

    private static CommandDispatcher<DihCommandSource> dispatcher() {
        var dispatcher = new CommandDispatcher<DihCommandSource>();
        LiteralArgumentBuilder<DihCommandSource> root = LiteralArgumentBuilder.literal("showregion");
        new ShowRegionCommand().build(root);
        dispatcher.register(root);
        return dispatcher;
    }

    private static void assertExecutable(CommandDispatcher<DihCommandSource> dispatcher, String input) {
        var result = dispatcher.parse(input, DihCommandSource.INSTANCE);
        assertFalse(result.getReader().canRead(), input);
        assertTrue(result.getExceptions().isEmpty(), input);
        assertNotNull(result.getContext().build(input).getCommand(), input);
    }
}
