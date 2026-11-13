package dihclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihProfileMatchingTest {
    @Test
    void exactHostsRespectExplicitPorts() throws Exception {
        assertTrue(matches("play.example.com", 25566, "play.example.com:25566"));
        assertFalse(matches("play.example.com", 25565, "play.example.com:25566"));
        assertTrue(matches("play.example.com", 25566, "PLAY.EXAMPLE.COM"));
    }

    @Test
    void wildcardDomainsRespectExplicitPorts() throws Exception {
        assertTrue(matches("play.example.com", 25566, "*.example.com:25566"));
        assertTrue(matches("example.com", 25566, "*.example.com:25566"));
        assertFalse(matches("play.example.com", 25565, "*.example.com:25566"));
        assertFalse(matches("otherexample.com", 25566, "*.example.com:25566"));
    }

    @Test
    void anyHostWildcardRespectsExplicitPorts() throws Exception {
        assertTrue(matches("play.example.com", 25566, "*:25566"));
        assertFalse(matches("play.example.com", 25565, "*:25566"));
        assertTrue(matches("play.example.com", 25565, "*"));
    }

    @Test
    void wildcardDomainsWithoutPortsStillMatch() throws Exception {
        assertTrue(matches("play.example.com", 25566, "*.example.com"));
        assertTrue(matches("example.com", 25565, "*.example.com"));
        assertFalse(matches("otherexample.com", 25565, "*.example.com"));
    }

    private static boolean matches(String host, int port, String pattern) throws Exception {
        var method = DihProfileManager.class.getDeclaredMethod("matchesPattern", String.class, int.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, host, port, pattern);
    }
}
