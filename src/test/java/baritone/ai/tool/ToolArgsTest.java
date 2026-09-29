package baritone.ai.tool;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code .tools} and the AI_TOOL macro step type arguments as text; the model sends JSON. Both go through here. */
final class ToolArgsTest {

    private static final ToolSchema GOTO = ToolSchema.builder()
            .integer("x", "X").required()
            .integer("y", "Y").required()
            .integer("z", "Z").required()
            .enumOf("mode", "How", "walk", "sprint").defaultsTo("walk")
            .build();

    private static final ToolSchema SAY = ToolSchema.builder()
            .string("message", "What to say").required()
            .build();

    private static final ToolSchema WAIT = ToolSchema.builder()
            .integer("seconds", "How long").range(1, 30).defaultsTo(5)
            .bool("quiet", "Say nothing")
            .build();

    private static JsonObject ok(ToolArgs.Parsed parsed) {
        assertNull(parsed.error(), parsed.error());
        return parsed.args();
    }

    private static String error(ToolArgs.Parsed parsed) {
        assertNull(parsed.args(), "expected an error");
        return parsed.error();
    }

    @Test
    void positionalArgsFollowTheSchemaOrder() {
        JsonObject args = ok(ToolArgs.parseLine(GOTO, "100 64 -200"));
        assertEquals(100, args.get("x").getAsInt());
        assertEquals(64, args.get("y").getAsInt());
        assertEquals(-200, args.get("z").getAsInt());
        assertFalse(args.has("mode"), "defaults are not filled in");
    }

    @Test
    void keyValueWorksInAnyOrderAndMixesWithPositional() {
        JsonObject keyed = ok(ToolArgs.parseLine(GOTO, "z=-200 x=100 y=64 mode=sprint"));
        assertEquals(100, keyed.get("x").getAsInt());
        assertEquals(-200, keyed.get("z").getAsInt());
        assertEquals("sprint", keyed.get("mode").getAsString());

        JsonObject mixed = ok(ToolArgs.parseLine(GOTO, "100 z=-200 64"));
        assertEquals(100, mixed.get("x").getAsInt());
        assertEquals(64, mixed.get("y").getAsInt());
        assertEquals(-200, mixed.get("z").getAsInt());
    }

    @Test
    void enumValuesMatchLooselyAndComeBackCanonical() {
        assertEquals("sprint", ok(ToolArgs.parseLine(GOTO, "1 2 3 SPRINT")).get("mode").getAsString());
        String bad = error(ToolArgs.parseLine(GOTO, "1 2 3 fly"));
        assertTrue(bad.contains("mode") && bad.contains("walk, sprint"), bad);
    }

    @Test
    void quotedStringsKeepTheirSpacesAndTheLastStringTakesTheRest() {
        assertEquals("hello  world", ok(ToolArgs.parseLine(SAY, "\"hello  world\"")).get("message").getAsString());
        assertEquals("hello big world", ok(ToolArgs.parseLine(SAY, "hello big world")).get("message").getAsString());
        assertEquals("say \"hi\"", ok(ToolArgs.parseLine(SAY, "\"say \\\"hi\\\"\"")).get("message").getAsString());
        assertEquals("it is \"fine\"", ok(ToolArgs.parseLine(SAY, "'it is \"fine\"'")).get("message").getAsString());
        assertEquals("a=b", ok(ToolArgs.parseLine(SAY, "a=b")).get("message").getAsString(),
                "a key that isn't a param is just text");
        assertTrue(error(ToolArgs.parseLine(SAY, "\"unfinished")).contains("quote"));
    }

    @Test
    void missingRequiredParamGivesAReadableError() {
        String error = error(ToolArgs.parseLine(GOTO, "100 64"));
        assertTrue(error.contains("Missing z"), error);
        assertTrue(error.contains("goto_xyz <x> <y> <z> [mode]") || error.contains("<x> <y> <z> [mode]"), error);
    }

    @Test
    void badValuesSayWhatWasExpected() {
        assertTrue(error(ToolArgs.parseLine(GOTO, "a 64 1")).contains("x must be a whole number"));
        assertTrue(error(ToolArgs.parseLine(GOTO, "1.5 64 1")).contains("x must be a whole number"));
        assertTrue(error(ToolArgs.parseLine(WAIT, "45")).contains("seconds must be at most 30"));
        assertTrue(error(ToolArgs.parseLine(WAIT, "0")).contains("seconds must be at least 1"));
        assertTrue(error(ToolArgs.parseLine(WAIT, "5 maybe")).contains("quiet must be true or false"));
        assertTrue(error(ToolArgs.parseLine(GOTO, "1 2 3 walk extra")).contains("Too many arguments"));
        assertTrue(error(ToolArgs.parseLine(GOTO, "x=1 x=2 3 4")).contains("x is given twice"));
    }

    @Test
    void booleansTakeTheUsualWords() {
        assertTrue(ok(ToolArgs.parseLine(WAIT, "5 yes")).get("quiet").getAsBoolean());
        assertFalse(ok(ToolArgs.parseLine(WAIT, "quiet=off")).get("quiet").getAsBoolean());
        assertTrue(ok(ToolArgs.parseLine(WAIT, "quiet=ON")).get("quiet").getAsBoolean());
    }

    @Test
    void anEmptyLineIsFineWhenNothingIsRequired() {
        assertTrue(ok(ToolArgs.parseLine(WAIT, "")).isEmpty());
        assertTrue(ok(ToolArgs.parseLine(WAIT, null)).isEmpty());
        assertTrue(ok(ToolArgs.parseLine(ToolSchema.EMPTY, "  ")).isEmpty());
        assertTrue(error(ToolArgs.parseLine(ToolSchema.EMPTY, "stray")).contains("Too many arguments"));
    }

    @Test
    void modelJsonIsCoercedAndChecked() {
        JsonObject raw = new JsonObject();
        raw.addProperty("x", "100");
        raw.addProperty("y", 64.0);
        raw.addProperty("z", -200);
        raw.addProperty("mode", "Sprint");
        raw.addProperty("junk", "ignored");
        JsonObject args = ok(ToolArgs.validate(GOTO, raw));
        assertEquals(100, args.get("x").getAsInt());
        assertEquals("64", args.get("y").toString(), "whole numbers stay whole");
        assertEquals("sprint", args.get("mode").getAsString());
        assertFalse(args.has("junk"), "unknown keys are dropped");

        JsonObject missing = new JsonObject();
        missing.addProperty("x", 1);
        assertTrue(error(ToolArgs.validate(GOTO, missing)).contains("Missing y"));

        JsonObject number = new JsonObject();
        number.addProperty("message", 42);
        assertEquals("42", ok(ToolArgs.validate(SAY, number)).get("message").getAsString());

        JsonObject nulls = new JsonObject();
        nulls.add("seconds", com.google.gson.JsonNull.INSTANCE);
        assertFalse(ok(ToolArgs.validate(WAIT, nulls)).has("seconds"), "null means not given");
    }

    @Test
    void inputFallsBackToSchemaDefaults() {
        ToolInput input = new ToolInput(WAIT, ok(ToolArgs.parseLine(WAIT, "")));
        assertEquals(5, input.integer("seconds"));
        assertFalse(input.bool("quiet"));
        assertFalse(input.has("seconds"));
        ToolInput given = new ToolInput(GOTO, ok(ToolArgs.parseLine(GOTO, "1 2 3")));
        assertEquals("walk", given.string("mode"));
        assertEquals(3, given.integer("z"));
    }

    @Test
    void suggestsEnumValuesForTheParamBeingTyped() {
        assertEquals(java.util.List.of("walk", "sprint"), ToolArgs.suggestions(GOTO, "1 2 3 "));
        assertEquals(java.util.List.of("sprint"), ToolArgs.suggestions(GOTO, "1 2 3 sp"));
        assertEquals(java.util.List.of("sprint"), ToolArgs.suggestions(GOTO, "1 2 3 mode=sp"));
        assertEquals(java.util.List.of("true", "false"), ToolArgs.suggestions(WAIT, "5 "));
        assertTrue(ToolArgs.suggestions(GOTO, "1 ").isEmpty(), "integers have nothing to suggest");
    }
}
