package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCommandParsingTest {
    @Test
    void clickSlotAcceptsCountsAboveTheOldOneThousandLimit() {
        CommandDispatcher<DihCommandSource> dispatcher = dispatcher("click-slot", new ClickSlotCommand());

        assertParses(dispatcher, "click-slot gui1 left 1001");
        assertParses(dispatcher, "click-slot gui1 shift-left " + ItemClickCommandSupport.MAX_REPEAT_COUNT);
        assertDoesNotParse(dispatcher, "click-slot gui1 left " + (ItemClickCommandSupport.MAX_REPEAT_COUNT + 1));
    }

    @Test
    void syncCommandAcceptsServerCommandsMessagesAndQuotedMacroNames() {
        CommandDispatcher<DihCommandSource> dispatcher = dispatcher("sync", new SyncCommand());

        assertParses(dispatcher, "sync send /ver");
        assertParses(dispatcher, "sync send hello from every client");
        assertParses(dispatcher, "sync macro spark");
        assertParses(dispatcher, "sync macro \"Spark Run\"");
    }

    private static CommandDispatcher<DihCommandSource> dispatcher(
        String rootName,
        dihclient.commands.Command command
    ) {
        CommandDispatcher<DihCommandSource> dispatcher = new CommandDispatcher<>();
        LiteralArgumentBuilder<DihCommandSource> root = LiteralArgumentBuilder.literal(rootName);
        command.build(root);
        dispatcher.register(root);
        return dispatcher;
    }

    private static void assertParses(CommandDispatcher<DihCommandSource> dispatcher, String input) {
        ParseResults<DihCommandSource> result = dispatcher.parse(input, DihCommandSource.INSTANCE);
        assertFalse(result.getReader().canRead(), () -> "Unread command input: "
            + result.getReader().getRemaining());
        assertTrue(result.getExceptions().isEmpty(), () -> "Parse exceptions: " + result.getExceptions());
    }

    private static void assertDoesNotParse(CommandDispatcher<DihCommandSource> dispatcher, String input) {
        ParseResults<DihCommandSource> result = dispatcher.parse(input, DihCommandSource.INSTANCE);
        assertTrue(result.getReader().canRead() || !result.getExceptions().isEmpty(),
            "Out-of-range count unexpectedly parsed");
    }
}
