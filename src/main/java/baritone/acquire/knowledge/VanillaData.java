package baritone.acquire.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads the vanilla data files straight from the Minecraft jar (or resource directory) on the
 * classpath, without the integrated server, so it works on multiplayer. The jar is found through
 * {@code data/.mcassetsroot}, the marker vanilla itself uses, falling back to a known recipe file.
 * Works the same in {@code runClient}, production Fabric and unit tests.
 */
final class VanillaData {
    private static final Logger LOGGER = LoggerFactory.getLogger("Baritone/Acquire");

    static final String LANG = "assets/minecraft/lang/en_us.json";
    private static final String MARKER = "data/.mcassetsroot";
    private static final String ANCHOR = "data/minecraft/recipe/stick.json";
    private static final List<String> DIRS = List.of(
            "data/minecraft/recipe/",
            "data/minecraft/loot_table/blocks/",
            "data/minecraft/loot_table/entities/",
            "data/minecraft/tags/item/",
            "data/minecraft/tags/block/",
            // Named loot conditions (26.3 loot tables refer to them by id).
            "data/minecraft/predicate/");

    private VanillaData() {
    }

    static boolean wanted(String path) {
        if (path.equals(LANG)) return true;
        if (!path.endsWith(".json")) return false;
        for (String dir : DIRS) if (path.startsWith(dir)) return true;
        return false;
    }

    /** Path in the jar -> file text for every wanted file, or an empty map if no usable root was found. */
    static Map<String, String> fromClasspath() {
        for (Root root : roots()) {
            try {
                Map<String, String> files = root.read();
                if (looksComplete(files)) {
                    if (!files.containsKey(LANG)) {
                        String lang = resourceText(LANG);
                        if (lang != null) files.put(LANG, lang);
                    }
                    return files;
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.debug("Acquire knowledge: could not read {}", root, e);
            }
        }
        return new HashMap<>();
    }

    /** At least a plausible amount of recipes and loot tables: a mod jar that overrides one recipe is not the vanilla root. */
    static boolean looksComplete(Map<String, String> files) {
        int recipes = 0;
        int loot = 0;
        for (String path : files.keySet()) {
            if (path.startsWith(DIRS.get(0))) recipes++;
            else if (path.startsWith("data/minecraft/loot_table/")) loot++;
        }
        return recipes >= 100 && loot >= 100;
    }

    private static List<Root> roots() {
        Set<Root> roots = new LinkedHashSet<>();
        for (String anchor : List.of(MARKER, ANCHOR)) {
            for (ClassLoader loader : loaders()) {
                try {
                    Enumeration<URL> urls = loader.getResources(anchor);
                    while (urls.hasMoreElements()) {
                        Root root = Root.of(urls.nextElement(), anchor);
                        if (root != null) roots.add(root);
                    }
                } catch (IOException | RuntimeException ignored) {
                }
            }
        }
        return new ArrayList<>(roots);
    }

    private static List<ClassLoader> loaders() {
        Set<ClassLoader> out = new LinkedHashSet<>();
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) out.add(context);
        if (VanillaData.class.getClassLoader() != null) out.add(VanillaData.class.getClassLoader());
        out.add(ClassLoader.getSystemClassLoader());
        return new ArrayList<>(out);
    }

    static String resourceText(String path) {
        for (ClassLoader loader : loaders()) {
            try (InputStream in = loader.getResourceAsStream(path)) {
                if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    /** A jar file or a directory that holds {@code data/} and usually {@code assets/}. */
    private record Root(Path path, boolean jar) {
        static Root of(URL url, String anchor) {
            try {
                if (url.getProtocol().equals("jar")) {
                    URL jarFile = ((JarURLConnection) url.openConnection()).getJarFileURL();
                    return new Root(Path.of(jarFile.toURI()), true);
                }
                // file: or any scheme with an installed FileSystemProvider; walk up from the anchor to the root
                Path p = Path.of(url.toURI());
                for (int i = anchor.split("/").length; i > 0 && p != null; i--) p = p.getParent();
                return p == null ? null : new Root(p, false);
            } catch (Exception e) {
                return null;
            }
        }

        Map<String, String> read() throws IOException {
            Map<String, String> out = new HashMap<>();
            if (jar) {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (entry.isDirectory() || !wanted(entry.getName())) continue;
                        try (InputStream in = zip.getInputStream(entry)) {
                            out.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                        }
                    }
                }
                return out;
            }
            List<String> starts = new ArrayList<>(DIRS);
            starts.add(LANG);
            for (String start : starts) {
                Path dir = path.resolve(start);
                if (!Files.exists(dir)) continue;
                try (Stream<Path> walk = Files.walk(dir)) {
                    for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                        String rel = path.relativize(file).toString().replace('\\', '/');
                        if (wanted(rel)) out.put(rel, Files.readString(file, StandardCharsets.UTF_8));
                    }
                }
            }
            return out;
        }

        @Override
        public String toString() {
            return path.toString();
        }
    }
}
