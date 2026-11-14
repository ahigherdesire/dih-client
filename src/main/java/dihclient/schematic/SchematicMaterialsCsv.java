package dihclient.schematic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * A placement's material list as a CSV file, one row per item, for a spreadsheet or a shopping list. Same numbers as
 * {@code .schem materials}: rows come from {@link SchematicMaterials.Report} in its order, biggest shortfalls first.
 */
public final class SchematicMaterialsCsv {

    static final String HEADER = "item,name,needed,have,missing,note";
    private static final String NO_ITEM = "no item places it";

    private SchematicMaterialsCsv() {}

    /**
     * @param names the display name for an item or block id
     */
    public static String csv(SchematicMaterials.Report report, Function<String, String> names) {
        StringBuilder out = new StringBuilder(HEADER).append("\r\n");
        for (SchematicMaterials.Line line : report.lines()) {
            row(out, line.item(), names.apply(line.item()), line.needed(), line.have(), line.missing(), "");
        }
        report.unplaceable().forEach((block, count) -> row(out, block, names.apply(block), count, 0, count, NO_ITEM));
        return out.toString();
    }

    /**
     * Writes the CSV as {@code <schematic name>-materials.csv} in the folder, replacing an older export of the same
     * schematic so the file always holds the latest list.
     *
     * @return the file written
     */
    public static Path write(Path folder, String schematicName, String csv) throws IOException {
        Files.createDirectories(folder);
        Path file = folder.resolve(fileName(schematicName));
        Files.writeString(file, csv, StandardCharsets.UTF_8);
        return file;
    }

    /**
     * @return a file name for the schematic's export: its name without the extension, with characters Windows does
     * not allow in file names replaced
     */
    static String fileName(String schematicName) {
        String name = schematicName == null ? "" : schematicName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.replaceAll("[<>:\"|?*\\x00-\\x1f]", "_").strip();
        return (name.isEmpty() ? "schematic" : name) + "-materials.csv";
    }

    private static void row(StringBuilder out, String item, String name, int needed, int have, int missing, String note) {
        out.append(field(item)).append(',').append(field(name)).append(',')
            .append(needed).append(',').append(have).append(',').append(missing).append(',')
            .append(field(note)).append("\r\n");
    }

    /**
     * @return the text as one CSV field: quoted when it holds a comma, quote or line break, and with a leading
     * {@code = + - @} neutralised so a spreadsheet does not run it as a formula
     */
    static String field(String text) {
        String value = text == null ? "" : text;
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
