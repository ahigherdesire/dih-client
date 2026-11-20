package dihclient.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dihclient.commands.DihCommandSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StorageCommandTest {
    @Test
    void acceptsNamesIdsAndMultiwordQueries() {
        var dispatcher = dispatcher();
        for (String query : new String[] {"rockets", "minecraft:iron_ingot", "Iron Ingot", "clear"}) {
            String input = "storage search " + query;
            var parsed = dispatcher.parse(input, DihCommandSource.INSTANCE);
            assertFalse(parsed.getReader().canRead());
            assertTrue(parsed.getExceptions().isEmpty());
            var context = parsed.getContext().build(input);
            assertNotNull(context.getCommand());
            assertEquals(query, StringArgumentType.getString(context, "query"));
        }
    }

    @Test
    void clearIsASeparateCommandAndSearchRequiresAnItem() {
        var dispatcher = dispatcher();
        var clear = dispatcher.parse("storage clear", DihCommandSource.INSTANCE);
        assertNotNull(clear.getContext().build("storage clear").getCommand());
        for (String input : new String[] {"storage search", "storage clear extra", "storage unknown"}) {
            var parsed = dispatcher.parse(input, DihCommandSource.INSTANCE);
            assertTrue(parsed.getReader().canRead() || !parsed.getExceptions().isEmpty()
                || parsed.getContext().build(input).getCommand() == null, input);
        }
    }

    private static CommandDispatcher<DihCommandSource> dispatcher() {
        var dispatcher = new CommandDispatcher<DihCommandSource>();
        LiteralArgumentBuilder<DihCommandSource> root = LiteralArgumentBuilder.literal("storage");
        new StorageCommand().build(root);
        dispatcher.register(root);
        return dispatcher;
    }
}
