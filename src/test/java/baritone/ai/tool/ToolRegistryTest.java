package baritone.ai.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ToolRegistryTest {

    private static AiTool tool(String name, ToolCategory category) {
        return AiTool.builder(name, category)
                .summary("Does " + name + ".")
                .handler((ctx, args) -> ToolResult.ok(name + " ran"))
                .build();
    }

    private static List<String> names(JsonArray definitions) {
        List<String> names = new ArrayList<>();
        for (JsonElement element : definitions) {
            names.add(element.getAsJsonObject().getAsJsonObject("function").get("name").getAsString());
        }
        return names;
    }

    @Test
    void duplicateNamesAreRejected() {
        ToolRegistry registry = new ToolRegistry().register(tool("strike", ToolCategory.COMBAT));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> registry.register(tool("strike", ToolCategory.MINING)));
        assertTrue(e.getMessage().contains("strike"));
    }

    @Test
    void namesAreSnakeCase() {
        assertThrows(IllegalArgumentException.class, () -> tool("Strike", ToolCategory.COMBAT));
        assertThrows(IllegalArgumentException.class, () -> tool("go-to", ToolCategory.MOVEMENT));
        assertThrows(IllegalArgumentException.class, () -> AiTool.builder("x", ToolCategory.WORLD)
                .handler((ctx, args) -> ToolResult.ok("")).build(), "a summary is required");
    }

    @Test
    void listsByCategoryInRegistrationOrder() {
        ToolRegistry registry = new ToolRegistry()
                .register(tool("strike", ToolCategory.COMBAT))
                .register(tool("dig", ToolCategory.MINING))
                .register(tool("block", ToolCategory.COMBAT));
        assertEquals(List.of("strike", "block"), registry.inCategory(ToolCategory.COMBAT).stream().map(AiTool::name).toList());
        assertEquals(List.of("strike", "dig", "block"), registry.all().stream().map(AiTool::name).toList());
        assertEquals("dig", registry.get("DIG").name(), "lookup ignores case");
        assertEquals(null, registry.get("nope"));
    }

    @Test
    void definitionsKeepAStableOrder() {
        ToolRegistry registry = new ToolRegistry()
                .register(tool("strike", ToolCategory.COMBAT))
                .register(tool("dig", ToolCategory.MINING))
                .register(tool("block", ToolCategory.COMBAT));
        JsonArray first = registry.definitions(tool -> tool.category() == ToolCategory.COMBAT);
        assertEquals(List.of("strike", "block"), names(first));
        assertEquals(first, registry.definitions(tool -> tool.category() == ToolCategory.COMBAT),
                "byte-identical every time so providers can cache the prefix");
        assertEquals(List.of("strike", "dig", "block"), names(registry.definitions(tool -> true)));
    }

    @Test
    void theStandardRegistryIsWellFormed() {
        ToolRegistry registry = ToolRegistry.standard();
        Set<String> seen = new HashSet<>();
        for (AiTool tool : registry.all()) {
            assertTrue(seen.add(tool.name()), "duplicate " + tool.name());
            assertFalse(tool.summary().isBlank(), tool.name());
            assertFalse(tool.description().isBlank(), tool.name());
        }
        assertTrue(registry.get("load_tools").job());
        assertTrue(registry.get("list_tools").job());
    }

    private static ToolRegistry callRegistry(AtomicInteger runs) {
        return new ToolRegistry()
                .register(AiTool.builder("count_to", ToolCategory.WORLD)
                        .summary("Counts.")
                        .schema(ToolSchema.builder().integer("n", "How far").required().range(1, 10).build())
                        .handler((ctx, args) -> {
                            runs.incrementAndGet();
                            return ToolResult.ok("counted to " + args.integer("n")).fact("n", args.integer("n"));
                        })
                        .build())
                .register(AiTool.builder("nuke", ToolCategory.CLIENT)
                        .summary("Deletes things.")
                        .dangerous()
                        .handler((ctx, args) -> {
                            runs.incrementAndGet();
                            return ToolResult.ok("gone");
                        })
                        .build())
                .register(AiTool.builder("explode", ToolCategory.WORLD)
                        .summary("Throws.")
                        .handler((ctx, args) -> {
                            throw new IllegalStateException("boom");
                        })
                        .build());
    }

    @Test
    void callValidatesRunsAndReports() {
        AtomicInteger runs = new AtomicInteger();
        ToolRegistry registry = callRegistry(runs);
        ToolContext chat = ToolContext.of(null, ToolContext.Source.CHAT);

        JsonObject args = new JsonObject();
        args.addProperty("n", "4");
        ToolResult result = registry.call(chat, "count_to", args);
        assertEquals(ToolResult.Status.OK, result.status());
        assertEquals("counted to 4", result.text());
        assertEquals(4, result.facts().get("n"));
        assertTrue(result.forModel().contains("\"n\":4"), result.forModel());

        ToolResult unknown = registry.call(chat, "count_up", new JsonObject());
        assertEquals(ToolResult.Status.FAILED, unknown.status());
        assertTrue(unknown.text().contains("No such tool"));

        JsonObject tooFar = new JsonObject();
        tooFar.addProperty("n", 99);
        ToolResult invalid = registry.call(chat, "count_to", tooFar);
        assertEquals(ToolResult.Status.FAILED, invalid.status());
        assertTrue(invalid.text().contains("at most 10"), invalid.text());

        JsonObject malformed = new JsonObject();
        malformed.addProperty("__malformed", "{n:");
        assertTrue(registry.call(chat, "count_to", malformed).text().contains("not valid JSON"));

        ToolResult crashed = registry.call(chat, "explode", new JsonObject());
        assertEquals(ToolResult.Status.FAILED, crashed.status());
        assertTrue(crashed.text().contains("boom"));
        assertEquals(1, runs.get());
    }

    @Test
    void dangerousToolsNeedConfirmation() {
        AtomicInteger runs = new AtomicInteger();
        ToolRegistry registry = callRegistry(runs);

        ToolResult refused = registry.call(ToolContext.of(null, ToolContext.Source.AI), "nuke", new JsonObject());
        assertEquals(ToolResult.Status.FAILED, refused.status());
        assertEquals("needs confirmation", refused.text());
        assertEquals(true, refused.facts().get("needs_confirmation"));
        assertEquals(0, runs.get(), "never ran");

        ToolResult confirmed = registry.call(ToolContext.of(null, ToolContext.Source.CHAT).confirmed(true), "nuke", new JsonObject());
        assertEquals(ToolResult.Status.OK, confirmed.status());
        assertEquals(1, runs.get());
    }

    @Test
    void resultsRenderForTheModel() {
        assertEquals("Needs 3 iron_ingot.", ToolResult.needs("iron_ingot", 3).text());
        assertEquals("iron_ingot", ToolResult.needs("iron_ingot", 3).facts().get("item"));
        ToolResult running = ToolResult.running("acquire", "Started.");
        assertEquals(ToolResult.Status.RUNNING, running.status());
        assertEquals("acquire", running.facts().get("job"));
        assertEquals("running", running.status().id());
        assertEquals("plain", ToolResult.ok("plain").forModel(), "no facts, no noise");
    }
}
