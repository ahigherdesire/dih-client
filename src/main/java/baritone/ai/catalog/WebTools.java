package baritone.ai.catalog;

import baritone.ai.AiConfig;
import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Searching and reading the web, for questions the game data can't answer (a server's rules, a mod's recipe).
 * Off unless {@code webTools} is on in ai.json.
 */
final class WebTools {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_TEXT = 4000;
    static final int MAX_RESULTS = 5;
    private static final int TIMEOUT_MILLIS = 10_000;
    private static final String OFF = "Web tools are off. Turn on \"webTools\" in baritone/ai.json to use them.";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) DIH-Client";

    /** One search hit. */
    record Hit(String title, String url, String snippet) {
    }

    /** Fetches a page's bytes; swapped out in tests. */
    interface Http {
        Page get(String url) throws IOException;
    }

    /** A fetched page: its content type and body. */
    record Page(String contentType, String body) {
    }

    private static final Pattern RESULT = Pattern.compile(
            "<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>(.*?)(?=<a[^>]*class=\"result__a\"|$)",
            Pattern.DOTALL);
    private static final Pattern SNIPPET = Pattern.compile("class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.DOTALL);

    private WebTools() {
    }

    static void register(ToolRegistry registry) {
        register(registry, WebTools::fetch);
    }

    static void register(ToolRegistry registry, Http http) {
        registry.register(AiTool.builder("web_search", ToolCategory.WEB)
                .summary("Search the web: the top 5 titles, links and snippets.")
                .schema(ToolSchema.builder()
                        .string("query", "What to search for.").required()
                        .build())
                .handler((ctx, args) -> {
                    if (!enabled(ctx)) return ToolResult.failed(OFF);
                    String query = args.string("query").trim();
                    if (query.isEmpty()) return ToolResult.failed("Nothing to search for.");
                    Page page = http.get("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
                    List<Hit> hits = parseResults(page.body());
                    if (hits.isEmpty()) return ToolResult.ok("No results for \"" + query + "\".").fact("results", 0);
                    StringBuilder text = new StringBuilder();
                    for (int i = 0; i < hits.size(); i++) {
                        Hit hit = hits.get(i);
                        text.append(i + 1).append(". ").append(hit.title()).append(" - ").append(hit.url());
                        if (!hit.snippet().isEmpty()) text.append("\n   ").append(hit.snippet());
                        text.append('\n');
                    }
                    return ToolResult.ok(text.toString().trim()).fact("results", hits.size())
                            .fact("urls", hits.stream().map(Hit::url).toList());
                })
                .build());

        registry.register(AiTool.builder("web_fetch", ToolCategory.WEB)
                .summary("Read a web page as plain text (up to 4000 characters).")
                .schema(ToolSchema.builder()
                        .string("url", "An http or https address.").required()
                        .build())
                .handler((ctx, args) -> {
                    if (!enabled(ctx)) return ToolResult.failed(OFF);
                    String url = args.string("url").trim();
                    String refusal = refusal(url);
                    if (refusal != null) return ToolResult.failed(refusal);
                    Page page = http.get(url);
                    String type = page.contentType() == null ? "" : page.contentType().toLowerCase(Locale.ROOT);
                    if (!type.isEmpty() && !type.startsWith("text/") && !type.contains("json") && !type.contains("xml")) {
                        return ToolResult.failed("Not a text page (" + type + ").");
                    }
                    String text = type.contains("html") || page.body().trim().startsWith("<") ? readable(page.body()) : page.body().trim();
                    boolean cut = text.length() > MAX_TEXT;
                    if (cut) text = text.substring(0, MAX_TEXT) + "…";
                    return ToolResult.ok(text.isEmpty() ? "The page has no readable text." : text)
                            .fact("url", url).fact("truncated", cut);
                })
                .build());
    }

    private static boolean enabled(ToolContext ctx) {
        AiConfig config = ctx.config();
        return config != null && config.webTools;
    }

    /** Why {@code url} may not be fetched, or null: only http(s), and never this machine or the local network. */
    static String refusal(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return "Not a valid address: " + url;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) return "Only http and https addresses can be fetched.";
        String host = uri.getHost();
        if (host == null || host.isBlank()) return "The address has no host.";
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.endsWith(".localhost") || lower.endsWith(".local")) {
            return "Local addresses can't be fetched.";
        }
        try {
            // Every address the name resolves to: a public name can point at the local network.
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (local(address)) return "Local addresses can't be fetched.";
            }
        } catch (IOException e) {
            return "Can't find the host " + host + ".";
        }
        return null;
    }

    private static boolean local(InetAddress address) {
        return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress() || address.isMulticastAddress();
    }

    /** Titles, real links and snippets from DuckDuckGo's HTML results page. */
    static List<Hit> parseResults(String html) {
        List<Hit> hits = new ArrayList<>();
        Matcher m = RESULT.matcher(html == null ? "" : html);
        while (m.find() && hits.size() < MAX_RESULTS) {
            String url = realUrl(unescape(m.group(1)));
            String title = readable(m.group(2));
            Matcher snippet = SNIPPET.matcher(m.group(3));
            String text = snippet.find() ? readable(snippet.group(1)) : "";
            if (!url.isEmpty() && !title.isEmpty()) hits.add(new Hit(title, url, text));
        }
        return hits;
    }

    /** DuckDuckGo wraps links as //duckduckgo.com/l/?uddg=<encoded>; this returns the link itself. */
    static String realUrl(String href) {
        int at = href.indexOf("uddg=");
        if (at < 0) return href.startsWith("//") ? "https:" + href : href;
        String encoded = href.substring(at + 5);
        int amp = encoded.indexOf('&');
        if (amp >= 0) encoded = encoded.substring(0, amp);
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    /** Page text: scripts, styles and tags removed, entities decoded, whitespace collapsed. */
    static String readable(String html) {
        String text = html == null ? "" : html;
        text = text.replaceAll("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>", " ");
        text = text.replaceAll("(?is)<br\\s*/?>|</p>|</div>|</li>|</h[1-6]>|</tr>", "\n");
        // Inline tags sit inside words and sentences: drop them without a gap.
        text = text.replaceAll("(?i)</?(b|i|em|strong|span|a|code|small|sup|sub|u|mark|abbr)\\b[^>]*>", "");
        text = text.replaceAll("(?s)<[^>]+>", " ");
        text = unescape(text);
        text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" *\\n[ \\n]*", "\n");
        return text.trim();
    }

    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);");

    static String unescape(String s) {
        String text = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&ndash;", "\u2013").replace("&mdash;", "\u2014")
                .replace("&hellip;", "\u2026").replace("&rsquo;", "\u2019").replace("&lsquo;", "\u2018")
                .replace("&rdquo;", "\u201d").replace("&ldquo;", "\u201c");
        Matcher m = NUMERIC_ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String code = m.group(1);
            int value = code.startsWith("x") ? Integer.parseInt(code.substring(1), 16) : Integer.parseInt(code);
            m.appendReplacement(out, Matcher.quoteReplacement(Character.isValidCodePoint(value)
                    ? new String(Character.toChars(value)) : ""));
        }
        m.appendTail(out);
        return out.toString().replace("&amp;", "&");
    }

    private static Page fetch(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "text/html,text/plain;q=0.9,*/*;q=0.5");
        int status = connection.getResponseCode();
        if (status >= 400) throw new IOException("HTTP " + status);
        // A redirect may have landed somewhere local.
        String landed = connection.getURL().toString();
        String refusal = refusal(landed);
        if (refusal != null) throw new IOException(refusal);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) > 0 && total < MAX_BYTES) {
                int keep = Math.min(read, MAX_BYTES - total);
                out.write(buffer, 0, keep);
                total += keep;
            }
            return new Page(connection.getContentType(), out.toString(StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }
}
