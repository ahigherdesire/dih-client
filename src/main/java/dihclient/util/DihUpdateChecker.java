package dihclient.util;

import dihclient.DihClientAddon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;

/**
 * Asks GitHub for the latest release once per session and says so when the running jar is behind.
 *
 * <p>Two ways to be behind: the release has a higher version, or it has the same version but a
 * different jar (a release asset re-uploaded with fixes). The second is caught by comparing this
 * jar's SHA-256 with the asset {@code digest} GitHub publishes. Dev runs (no jar) and the lite
 * variant skip the digest check, since they are never the published file.
 *
 * <p>Only {@code api.github.com} is contacted. Off with {@link DihConfig#updateCheck}.
 */
public final class DihUpdateChecker {
    public static final String REPO = "ahigherdesire/dih-client";
    private static final String LATEST_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";

    public enum State { NOT_CHECKED, CHECKING, UP_TO_DATE, UPDATE_AVAILABLE, FAILED, DISABLED }

    /** {@code newerBuild}: same version number, different jar. */
    public record Result(State state, String current, String latest, boolean newerBuild, String downloadUrl, String pageUrl) {
        public boolean updateAvailable() { return state == State.UPDATE_AVAILABLE; }
    }

    private static volatile Result result = new Result(State.NOT_CHECKED, currentVersion(), "", false, "", DihLinks.WEBSITE);
    private static volatile int generation;
    private static volatile boolean announced;

    private DihUpdateChecker() {
    }

    public static Result result() {
        return result;
    }

    /** Bumped whenever {@link #result()} changes, so screens know to re-layout. */
    public static int generation() {
        return generation;
    }

    /** Starts the first check of the session; later calls do nothing. */
    public static void checkOnce() {
        if (result.state() == State.NOT_CHECKED) check();
    }

    /** Starts a check on a background thread, even if one already finished. */
    public static synchronized void check() {
        if (result.state() == State.CHECKING) return;
        if (!DihConfig.getGlobal().updateCheck) {
            publish(new Result(State.DISABLED, currentVersion(), "", false, "", DihLinks.WEBSITE));
            return;
        }
        publish(new Result(State.CHECKING, currentVersion(), result.latest(), false, "", DihLinks.WEBSITE));
        Thread thread = new Thread(DihUpdateChecker::run, "DIH update check");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        String current = currentVersion();
        try {
            JsonObject release = DihHttp.getJson(LATEST_URL, null, Map.of(
                "Accept", "application/vnd.github+json",
                "X-GitHub-Api-Version", "2022-11-28"));
            if (release == null || !release.has("tag_name")) {
                publish(new Result(State.FAILED, current, "", false, "", DihLinks.WEBSITE));
                return;
            }
            String latest = stripTag(release.get("tag_name").getAsString());
            String page = string(release, "html_url", DihLinks.WEBSITE);
            JsonObject asset = pickJarAsset(release.getAsJsonArray("assets"));
            String download = asset == null ? page : string(asset, "browser_download_url", page);

            int cmp = compareVersions(latest, current);
            boolean newerVersion = cmp > 0;
            boolean newerBuild = false;
            if (cmp == 0 && asset != null && !DihLiteVariant.enabled()) {
                String published = string(asset, "digest", "");
                String mine = ownJarSha256();
                newerBuild = published.startsWith("sha256:") && mine != null
                    && !published.substring("sha256:".length()).equalsIgnoreCase(mine);
            }
            State state = newerVersion || newerBuild ? State.UPDATE_AVAILABLE : State.UP_TO_DATE;
            publish(new Result(state, current, latest, newerBuild && !newerVersion, download, page));
            DihClientAddon.LOG.info("[DIH] Update check: running {}, latest {} -> {}{}", current, latest, state,
                newerBuild && !newerVersion ? " (newer build of the same version)" : "");
        } catch (Throwable t) {
            DihClientAddon.LOG.debug("[DIH] Update check failed: {}", t.toString());
            publish(new Result(State.FAILED, current, "", false, "", DihLinks.WEBSITE));
        }
    }

    private static void publish(Result next) {
        result = next;
        generation++;
        if (next.updateAvailable()) {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.player != null) announceOnJoin();
                });
            } catch (Throwable ignored) {
                // no client yet; the join hook announces it later
            }
        }
    }

    /** Chat line with a download link, once per session, the first time you join a world after a check found an update. */
    public static void announceOnJoin() {
        Result r = result;
        if (!r.updateAvailable() || announced) return;
        announced = true;
        DihClientMessaging.send(Component.empty()
            .append(DihClientMessaging.themedTag("DIH"))
            .append(summary(r).copy().withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" "))
            .append(link("[Download]", r.downloadUrl()))
            .append(Component.literal(" "))
            .append(link("[Release notes]", r.pageUrl())));
    }

    /** One line describing the result, for chat and the title screen. */
    public static Component summary(Result r) {
        return Component.literal(switch (r.state()) {
            case UPDATE_AVAILABLE -> r.newerBuild()
                ? "A newer build of " + r.latest() + " is out. Your jar is outdated."
                : "Update available: " + r.current() + " -> " + r.latest() + ".";
            case UP_TO_DATE -> "You're on the latest release (" + r.current() + ").";
            case CHECKING -> "Checking GitHub for updates...";
            case FAILED -> "Couldn't reach GitHub to check for updates.";
            case DISABLED -> "Update checks are off.";
            case NOT_CHECKED -> "Not checked yet.";
        });
    }

    private static MutableComponent link(String label, String url) {
        MutableComponent text = Component.literal(label).withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE);
        if (!DihLinks.isOpenableUrl(url)) return text;
        try {
            return text.withStyle(style -> style
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(url))));
        } catch (IllegalArgumentException e) {
            return text;
        }
    }

    /** The jar asset built for this Minecraft version, else the first jar. */
    private static JsonObject pickJarAsset(JsonArray assets) {
        if (assets == null) return null;
        String mc = minecraftVersion();
        JsonObject firstJar = null;
        for (JsonElement e : assets) {
            if (!e.isJsonObject()) continue;
            JsonObject asset = e.getAsJsonObject();
            String name = string(asset, "name", "").toLowerCase(Locale.ROOT);
            if (!name.endsWith(".jar") || name.contains("sources") || name.contains("lite")) continue;
            if (firstJar == null) firstJar = asset;
            if (!mc.isEmpty() && name.contains("-" + mc.toLowerCase(Locale.ROOT) + ".jar")) return asset;
        }
        return firstJar;
    }

    /** Mod version without the Minecraft suffix: {@code 5.0-26.2} -> {@code 5.0}. */
    public static String currentVersion() {
        String full;
        try {
            full = FabricLoader.getInstance().getModContainer("dih")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("0");
        } catch (Throwable t) {
            full = "0";
        }
        String mc = minecraftVersion();
        if (!mc.isEmpty() && full.endsWith("-" + mc)) full = full.substring(0, full.length() - mc.length() - 1);
        return full;
    }

    private static String minecraftVersion() {
        try {
            return SharedConstants.getCurrentVersion().name();
        } catch (Throwable t) {
            return "";
        }
    }

    static String stripTag(String tag) {
        String t = tag == null ? "" : tag.trim();
        return t.startsWith("v") || t.startsWith("V") ? t.substring(1) : t;
    }

    /**
     * Numeric, dot by dot: 5.0.1 > 5.0, 5.10 > 5.9. A pre-release comes before its release
     * (5.1-beta.2 < 5.1 < 5.1.1), and pre-releases compare part by part: beta.2 < beta.10, beta < rc.
     */
    static int compareVersions(String a, String b) {
        String[] x = a.trim().split("-", 2), y = b.trim().split("-", 2);
        int core = compareParts(x[0].split("\\."), y[0].split("\\."));
        if (core != 0) return core;
        boolean preX = x.length > 1, preY = y.length > 1;
        if (preX != preY) return preX ? -1 : 1;
        return preX ? compareParts(x[1].split("[.-]"), y[1].split("[.-]")) : 0;
    }

    /** Part by part; numbers numerically, words alphabetically (a missing part counts as 0). */
    private static int compareParts(String[] x, String[] y) {
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i] : "0", q = i < y.length ? y[i] : "0";
            boolean numP = !p.isEmpty() && Character.isDigit(p.charAt(0)), numQ = !q.isEmpty() && Character.isDigit(q.charAt(0));
            int c = numP && numQ ? Integer.compare(leadingInt(p), leadingInt(q))
                : numP != numQ ? (numP ? -1 : 1) : p.compareToIgnoreCase(q);
            if (c != 0) return c;
        }
        return 0;
    }

    private static int leadingInt(String s) {
        int n = 0, i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i)) && i < 9) n = n * 10 + (s.charAt(i++) - '0');
        return n;
    }

    private static String ownJarSha256() {
        try {
            Path jar = FabricLoader.getInstance().getModContainer("dih")
                .flatMap(c -> c.getOrigin().getPaths().stream().findFirst())
                .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .filter(Files::isRegularFile)
                .orElse(null);
            if (jar == null) return null;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(jar)) {
                byte[] buf = new byte[1 << 16];
                for (int n; (n = in.read(buf)) > 0; ) digest.update(buf, 0, n);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest()) hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return hex.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String string(JsonObject o, String key, String fallback) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
