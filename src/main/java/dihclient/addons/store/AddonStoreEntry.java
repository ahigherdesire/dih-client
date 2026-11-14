package dihclient.addons.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One listing from the addon registry ({@code website/addons.json}, built by {@code registry/registry.py}). A listing
 * has one jar per supported Minecraft version. The registry is reviewed, but the client re-checks every rule it relies
 * on before downloading anything.
 */
public record AddonStoreEntry(
    String id,
    String name,
    String version,
    String author,
    String summary,
    String description,
    int apiVersion,
    String license,
    List<String> categories,
    String source,
    String image,
    List<AddonFile> files
) {
    public static final long MAX_JAR_BYTES = 64L * 1024 * 1024;

    /** The jar for one Minecraft version, e.g. "26.2". */
    public record AddonFile(String minecraft, String jar, long size, String sha256) {}

    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9_-]{1,39}$");
    private static final Pattern VERSION = Pattern.compile("^\\d+\\.\\d+(?:\\.\\d+)?(?:[-+][0-9A-Za-z.-]+)?$");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern REPO = Pattern.compile("^https://github\\.com/([A-Za-z0-9._-]+)/([A-Za-z0-9._-]+)/?$");
    private static final Pattern MINECRAFT = Pattern.compile("^\\d+\\.\\d+(?:\\.\\d+)?$");

    /** Parses one registry object; returns null if a field is missing or has the wrong type. */
    public static AddonStoreEntry parse(JsonObject o) {
        if (o == null) return null;
        try {
            List<String> categories = new ArrayList<>();
            JsonElement cats = o.get("categories");
            if (cats != null && cats.isJsonArray()) {
                for (JsonElement e : (JsonArray) cats) categories.add(e.getAsString());
            }
            List<AddonFile> files = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("files")) {
                JsonObject f = e.getAsJsonObject();
                files.add(new AddonFile(str(f, "minecraft"), str(f, "jar"), f.get("size").getAsLong(), str(f, "sha256")));
            }
            String description = str(o, "description");
            String summary = o.has("summary") ? str(o, "summary") : description;
            String image = o.has("image") ? str(o, "image") : "";
            return new AddonStoreEntry(
                str(o, "id"), str(o, "name"), str(o, "version"), str(o, "author"), summary, description,
                o.get("apiVersion").getAsInt(), str(o, "license"), List.copyOf(categories), str(o, "source"),
                image, List.copyOf(files));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        String value = o.get(key).getAsString();
        if (value == null) throw new IllegalArgumentException(key);
        return value;
    }

    /** Problems that make this entry unsafe or impossible to install. Empty means it may be downloaded. */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        if (!ID.matcher(id).matches()) out.add("bad id");
        if (id.equals("dih") || id.startsWith("dih-") || id.startsWith("dih_")) out.add("id is reserved for DIH");
        if (!VERSION.matcher(version).matches()) out.add("bad version");
        if (apiVersion < 1) out.add("bad apiVersion");
        if (files.isEmpty()) out.add("no files");
        Matcher repo = REPO.matcher(source);
        String prefix = repo.matches()
            ? "https://github.com/" + repo.group(1) + "/" + repo.group(2) + "/releases/download/"
            : null;
        if (prefix == null) out.add("source is not a GitHub repo");
        if (!image.isEmpty() && !imageAllowed(repo)) out.add("image must be a PNG/JPG in the source repo or its releases");
        List<String> seen = new ArrayList<>();
        for (AddonFile f : files) {
            if (!MINECRAFT.matcher(f.minecraft()).matches()) out.add("bad minecraft version");
            if (seen.contains(f.minecraft())) out.add("two files for Minecraft " + f.minecraft());
            seen.add(f.minecraft());
            if (!SHA256.matcher(f.sha256()).matches()) out.add("bad sha256");
            if (f.size() <= 0 || f.size() > MAX_JAR_BYTES) out.add("bad size");
            if (prefix != null && (!f.jar().startsWith(prefix) || !f.jar().endsWith(".jar") || f.jar().contains(".."))) {
                out.add("jar is not a release asset of the source repo");
            }
        }
        return out;
    }

    /** The card image may only come from the addon's own repo: a raw file or a release asset. */
    private boolean imageAllowed(Matcher repo) {
        if (!repo.matches()) return false;
        String path = repo.group(1) + "/" + repo.group(2) + "/";
        String lower = image.toLowerCase(Locale.ROOT);
        boolean type = lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        boolean host = image.startsWith("https://raw.githubusercontent.com/" + path)
            || image.startsWith("https://github.com/" + path + "releases/download/");
        return type && host && !image.contains("..");
    }

    /** The jar for this game version (e.g. "26.2" or "26.2.1"), or null if the addon does not support it. */
    public AddonFile fileFor(String gameVersion) {
        if (gameVersion == null) return null;
        for (AddonFile f : files) {
            if (gameVersion.equals(f.minecraft()) || gameVersion.startsWith(f.minecraft() + ".")) return f;
        }
        return null;
    }

    public String minecraftVersions() {
        List<String> versions = new ArrayList<>();
        for (AddonFile f : files) versions.add(f.minecraft());
        return String.join(", ", versions);
    }

    /** The file name this addon is saved as in the mods folder. */
    public String fileName() {
        return id + "-" + version + ".jar";
    }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase(Locale.ROOT);
        String hay = (name + " " + author + " " + summary + " " + description + " " + String.join(" ", categories))
            .toLowerCase(Locale.ROOT);
        return hay.contains(q);
    }
}
