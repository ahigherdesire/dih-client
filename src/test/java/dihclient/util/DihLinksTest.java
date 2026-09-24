package dihclient.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DihLinksTest {
    @Test
    void allowsOnlyWebAndMailSchemes() {
        assertTrue(DihLinks.isOpenableUrl("https://dupedb.net/exploit/1"));
        assertTrue(DihLinks.isOpenableUrl("http://example.com"));
        assertTrue(DihLinks.isOpenableUrl("HTTPS://Example.com"));
        assertTrue(DihLinks.isOpenableUrl("mailto:a@b.com"));
    }

    @Test
    void rejectsLocalAndCodeSchemes() {
        assertFalse(DihLinks.isOpenableUrl("file:///etc/passwd"));
        assertFalse(DihLinks.isOpenableUrl("file:///C:/Windows/System32/calc.exe"));
        assertFalse(DihLinks.isOpenableUrl("javascript:alert(1)"));
        assertFalse(DihLinks.isOpenableUrl("data:text/html,<script>"));
        assertFalse(DihLinks.isOpenableUrl("steam://run/12345"));
        assertFalse(DihLinks.isOpenableUrl("  file://x"));
        assertFalse(DihLinks.isOpenableUrl(""));
        assertFalse(DihLinks.isOpenableUrl(null));
    }
}
