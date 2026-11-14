package dihclient.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Groups macros by {@link DihMacro#folder} for the macro lists and pickers: folders first (A-Z), each followed by its
 * macros unless collapsed, then macros without a folder in their original order. While searching, a folder is shown
 * open when anything in it matches, so search finds macros inside collapsed folders.
 */
public final class MacroFolderView {
    private MacroFolderView() {
    }

    public sealed interface Row permits FolderRow, MacroRow {
    }

    /** A folder heading; {@code count} is how many of its macros are listed (all of them unless searching). */
    public record FolderRow(String folder, int count, boolean collapsed) implements Row {
    }

    public record MacroRow(DihMacro macro, boolean inFolder) implements Row {
    }

    public static List<Row> build(List<DihMacro> macros, String filter, Collection<String> collapsedFolders) {
        String query = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        Map<String, String> displayNames = new TreeMap<>();
        Map<String, List<DihMacro>> byFolder = new LinkedHashMap<>();
        List<DihMacro> loose = new ArrayList<>();
        for (DihMacro macro : macros) {
            if (macro == null) continue;
            String folder = DihMacro.normalizeFolder(macro.folder);
            if (!folder.isEmpty()) displayNames.putIfAbsent(key(folder), folder);
            if (!matches(macro, folder, query)) continue;
            if (folder.isEmpty()) {
                loose.add(macro);
                continue;
            }
            byFolder.computeIfAbsent(key(folder), k -> new ArrayList<>()).add(macro);
        }
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, String> folder : displayNames.entrySet()) {
            List<DihMacro> inside = byFolder.get(folder.getKey());
            if (inside == null) continue;
            boolean collapsed = query.isEmpty() && isCollapsed(collapsedFolders, folder.getValue());
            rows.add(new FolderRow(folder.getValue(), inside.size(), collapsed));
            if (!collapsed) {
                for (DihMacro macro : inside) rows.add(new MacroRow(macro, true));
            }
        }
        for (DihMacro macro : loose) rows.add(new MacroRow(macro, false));
        return rows;
    }

    /** The macros in folder order (folders A-Z, then those without one), for lists that can't show headings. */
    public static List<DihMacro> ordered(List<DihMacro> macros) {
        List<DihMacro> out = new ArrayList<>();
        for (Row row : build(macros, "", List.of())) {
            if (row instanceof MacroRow macro) out.add(macro.macro());
        }
        return out;
    }

    /** "Folder › name", or just the name for a macro without a folder. */
    public static String label(DihMacro macro) {
        if (macro == null) return "";
        String folder = DihMacro.normalizeFolder(macro.folder);
        return folder.isEmpty() ? macro.name : folder + " › " + macro.name;
    }

    /** Every folder in use, A-Z, spelled as the first macro in it spells it. */
    public static List<String> folders(Collection<DihMacro> macros) {
        Map<String, String> names = new TreeMap<>();
        for (DihMacro macro : macros) {
            if (macro == null) continue;
            String folder = DihMacro.normalizeFolder(macro.folder);
            if (!folder.isEmpty()) names.putIfAbsent(key(folder), folder);
        }
        return new ArrayList<>(names.values());
    }

    public static boolean isCollapsed(Collection<String> collapsedFolders, String folder) {
        if (collapsedFolders == null || folder == null) return false;
        String key = key(folder);
        for (String collapsed : collapsedFolders) {
            if (collapsed != null && key(collapsed).equals(key)) return true;
        }
        return false;
    }

    /** Records a folder as collapsed or open in {@code collapsedFolders}; returns whether that changed anything. */
    public static boolean setCollapsed(List<String> collapsedFolders, String folder, boolean collapsed) {
        String clean = DihMacro.normalizeFolder(folder);
        if (clean.isEmpty() || isCollapsed(collapsedFolders, clean) == collapsed) return false;
        if (collapsed) {
            collapsedFolders.add(clean);
        } else {
            collapsedFolders.removeIf(existing -> existing == null || key(existing).equals(key(clean)));
        }
        return true;
    }

    private static boolean matches(DihMacro macro, String folder, String query) {
        if (query.isEmpty()) return true;
        String name = macro.name == null ? "" : macro.name.toLowerCase(Locale.ROOT);
        return name.contains(query) || folder.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String key(String folder) {
        return DihMacro.normalizeFolder(folder).toLowerCase(Locale.ROOT);
    }
}
