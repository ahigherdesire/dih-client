package dihclient.modules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpamSplitTest {
    @Test
    void shortMessagesStayWhole() {
        assertEquals(List.of("hello there"), BuiltinModules.SpamModule.splitIntoParts("hello there", 256));
    }

    @Test
    void longMessagesAreSentInPartsAtSpacesWithNothingLost() {
        String msg = "the quick brown fox jumps over the lazy dog";
        List<String> parts = BuiltinModules.SpamModule.splitIntoParts(msg, 16);
        assertEquals(List.of("the quick brown", "fox jumps over", "the lazy dog"), parts);
        for (String part : parts) assertTrue(part.length() <= 16, part);
    }

    @Test
    void wordsLongerThanAPartAreCut() {
        assertEquals(List.of("abcdefgh", "ijklmnop", "qr"), BuiltinModules.SpamModule.splitIntoParts("abcdefghijklmnopqr", 8));
    }
}
