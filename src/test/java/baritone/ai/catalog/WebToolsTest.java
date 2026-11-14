package baritone.ai.catalog;

import baritone.ai.AiConfig;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WebToolsTest {

    private static final String RESULTS = """
            <div class="result"><h2><a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fminecraft.wiki%2Fw%2FBeacon&amp;rut=abc">Beacon &ndash; <b>Minecraft</b> Wiki</a></h2>
            <a class="result__snippet" href="x">A beacon is a block that projects a light beam &amp; gives effects.</a></div>
            <div class="result"><h2><a rel="nofollow" class="result__a" href="https://example.com/page">Second</a></h2>
            <a class="result__snippet" href="y">Another <b>snippet</b>.</a></div>
            """;

    private final List<String> fetched = new ArrayList<>();

    private ToolResult call(String tool, String key, String value, boolean enabled, WebTools.Page page) {
        ToolRegistry registry = new ToolRegistry();
        WebTools.register(registry, url -> {
            fetched.add(url);
            return page;
        });
        AiConfig config = new AiConfig();
        config.webTools = enabled;
        JsonObject args = new JsonObject();
        args.addProperty(key, value);
        return registry.call(ToolContext.of(null, ToolContext.Source.AI).withConfig(config), tool, args);
    }

    @Test
    void offByDefault() {
        assertFalse(new AiConfig().webTools);
        ToolResult result = call("web_search", "query", "beacon", false, null);
        assertEquals(ToolResult.Status.FAILED, result.status());
        assertTrue(result.text().contains("Web tools are off"), result.text());
        assertTrue(fetched.isEmpty(), "nothing fetched");
    }

    @Test
    void searchReturnsTitlesRealLinksAndSnippets() {
        ToolResult result = call("web_search", "query", "minecraft beacon", true, new WebTools.Page("text/html", RESULTS));
        assertEquals(List.of("https://html.duckduckgo.com/html/?q=minecraft+beacon"), fetched);
        assertTrue(result.ok());
        assertEquals("1. Beacon \u2013 Minecraft Wiki - https://minecraft.wiki/w/Beacon\n"
                + "   A beacon is a block that projects a light beam & gives effects.\n"
                + "2. Second - https://example.com/page\n"
                + "   Another snippet.", result.text());
        assertEquals(2, result.facts().get("results"));
        assertEquals(List.of("https://minecraft.wiki/w/Beacon", "https://example.com/page"), result.facts().get("urls"));
    }

    @Test
    void fetchReturnsReadableTextCapped() {
        String html = "<html><head><title>t</title><script>var x=1;</script></head><body><h1>Title</h1><p>One &amp; two.</p>"
                + "<p>" + "word ".repeat(2000) + "</p></body></html>";
        ToolResult result = call("web_fetch", "url", "https://1.1.1.1/page", true, new WebTools.Page("text/html; charset=utf-8", html));
        assertTrue(result.ok(), result.text());
        assertTrue(result.text().startsWith("Title\nOne & two.\nword word"), result.text().substring(0, 40));
        assertFalse(result.text().contains("var x"));
        assertEquals(WebTools.MAX_TEXT + 1, result.text().length());
        assertEquals(Boolean.TRUE, result.facts().get("truncated"));
    }

    @Test
    void fetchRefusesLocalAddressesOtherSchemesAndBinaryPages() {
        for (String url : List.of("file:///C:/secret.txt", "ftp://example.com/x", "http://localhost:11434/api",
                "http://127.0.0.1/", "http://192.168.1.1/", "http://10.0.0.5/", "http://[::1]/", "http://printer.local/")) {
            ToolResult result = call("web_fetch", "url", url, true, new WebTools.Page("text/html", "x"));
            assertEquals(ToolResult.Status.FAILED, result.status(), url);
        }
        assertTrue(fetched.isEmpty(), "nothing fetched: " + fetched);
        ToolResult binary = call("web_fetch", "url", "https://1.1.1.1/x.png", true, new WebTools.Page("image/png", "\u0089PNG"));
        assertEquals("Not a text page (image/png).", binary.text());
        assertNull(WebTools.refusal("https://1.1.1.1/"));
    }
}
