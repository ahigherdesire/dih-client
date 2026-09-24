package dihclient.util.oresim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DihOreSimSeedInputTest {

    @Test
    void nullEmptyAndWhitespaceAreEmpty() {
        for (String input : new String[]{null, "", " ", "\t\r\n", "\u2003"}) {
            DihOreSimSeedInput.Result result = DihOreSimSeedInput.parse(input);
            assertEquals(DihOreSimSeedInput.Status.EMPTY, result.status());
            assertNull(result.value());
        }
    }

    @Test
    void acceptsTheEntireSignedLongRangeWithoutChangingTheValue() {
        assertValid("0", 0L);
        assertValid("-0", 0L);
        assertValid("+0", 0L);
        assertValid("42", 42L);
        assertValid("  -81234918234129384\n", -81234918234129384L);
        assertValid(Long.toString(Long.MIN_VALUE), Long.MIN_VALUE);
        assertValid(Long.toString(Long.MAX_VALUE), Long.MAX_VALUE);
    }

    @Test
    void malformedAndOverflowingValuesAreInvalid() {
        for (String input : new String[]{
            "seed", "+", "-", "12 34", "1.0", "9,223,372,036,854,775,807",
            "9223372036854775808", "-9223372036854775809"
        }) {
            DihOreSimSeedInput.Result result = DihOreSimSeedInput.parse(input);
            assertEquals(DihOreSimSeedInput.Status.INVALID, result.status(), input);
            assertNull(result.value(), input);
        }
    }

    private static void assertValid(String input, long expected) {
        DihOreSimSeedInput.Result result = DihOreSimSeedInput.parse(input);
        assertEquals(DihOreSimSeedInput.Status.VALID, result.status());
        assertTrue(result.isValid());
        assertEquals(expected, result.value());
    }
}
