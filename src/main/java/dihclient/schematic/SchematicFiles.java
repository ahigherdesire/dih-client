package dihclient.schematic;

import baritone.utils.schematic.format.SchematicLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Getting schematic files into the schematics folder from elsewhere: a file dropped on the game window, a path typed
 * in chat, or a download in the Downloads folder. No game types, so it is unit tested.
 */
public final class SchematicFiles {

    private SchematicFiles() {}

    public static boolean isSchematic(Path file) {
        Path name = file.getFileName();
        return name != null && SchematicLoader.EXTENSIONS.contains(extension(name.toString())) && Files.isRegularFile(file);
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Copies a schematic into the folder, keeping its name. A file of that name with the same bytes is reused; a
     * different one gets a {@code " (2)"}-style suffix instead of being overwritten. A file already inside the folder
     * is not copied.
     *
     * @return the file's path relative to the folder, with forward slashes
     */
    public static String importInto(Path folder, Path source) throws IOException {
        Path root = folder.toAbsolutePath().normalize();
        Path from = source.toAbsolutePath().normalize();
        if (from.startsWith(root)) {
            return relative(root, from);
        }
        Files.createDirectories(root);
        String name = from.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String ext = dot < 0 ? "" : name.substring(dot);
        for (int n = 1; ; n++) {
            Path target = root.resolve(n == 1 ? name : base + " (" + n + ")" + ext);
            if (!Files.exists(target)) {
                Files.copy(from, target, StandardCopyOption.COPY_ATTRIBUTES);
                return relative(root, target);
            }
            if (sameBytes(from, target)) {
                return relative(root, target);
            }
        }
    }

    private static boolean sameBytes(Path a, Path b) throws IOException {
        return Files.size(a) == Files.size(b) && Arrays.equals(Files.readAllBytes(a), Files.readAllBytes(b));
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /**
     * @return the schematic files directly in a folder, newest first, at most {@code limit}; empty if it is missing
     */
    public static List<Path> newest(Path folder, int limit) {
        if (folder == null || !Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(folder)) {
            return list.filter(SchematicFiles::isSchematic)
                .sorted(Comparator.comparing(SchematicFiles::modified).reversed())
                .limit(limit)
                .toList();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private static FileTime modified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    /**
     * @return {@code <home>/Downloads}, where browsers save files by default
     */
    public static Path downloadsFolder() {
        return Path.of(System.getProperty("user.home", "."), "Downloads");
    }
}
