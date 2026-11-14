package dihclient.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroFolderViewTest {

    private static DihMacro macro(String name, String folder) {
        DihMacro macro = new DihMacro(name);
        macro.folder = folder;
        return macro;
    }

    private static List<String> describe(List<MacroFolderView.Row> rows) {
        List<String> out = new ArrayList<>();
        for (MacroFolderView.Row row : rows) {
            if (row instanceof MacroFolderView.FolderRow folder) {
                out.add("[" + folder.folder() + " " + folder.count() + (folder.collapsed() ? " closed" : "") + "]");
            } else if (row instanceof MacroFolderView.MacroRow macro) {
                out.add((macro.inFolder() ? "  " : "") + macro.macro().name);
            }
        }
        return out;
    }

    private final List<DihMacro> macros = List.of(
        macro("home", ""),
        macro("kit pvp", "PvP"),
        macro("sell", "skyblock"),
        macro("kit tank", "pvp"),
        macro("afk", ""),
        macro("farm", "Skyblock"));

    @Test
    void foldersComeFirstAToZThenLooseMacrosInOrder() {
        assertEquals(List.of(
            "[PvP 2]", "  kit pvp", "  kit tank",
            "[skyblock 2]", "  sell", "  farm",
            "home", "afk"), describe(MacroFolderView.build(macros, "", Set.of())));
    }

    @Test
    void collapsedFoldersHideTheirMacrosButKeepTheCount() {
        assertEquals(List.of(
            "[PvP 2 closed]",
            "[skyblock 2]", "  sell", "  farm",
            "home", "afk"), describe(MacroFolderView.build(macros, "", Set.of("pvp"))));
    }

    @Test
    void searchFindsMacrosInsideCollapsedFolders() {
        assertEquals(List.of("[PvP 1]", "  kit tank"), describe(MacroFolderView.build(macros, "tank", Set.of("PvP"))));
    }

    @Test
    void searchingAFolderNameListsEverythingInIt() {
        assertEquals(List.of("[skyblock 2]", "  sell", "  farm"), describe(MacroFolderView.build(macros, "SKY", Set.of())));
    }

    @Test
    void flatOrderAndLabelsForListsWithoutHeadings() {
        assertEquals(List.of("kit pvp", "kit tank", "sell", "farm", "home", "afk"),
            MacroFolderView.ordered(macros).stream().map(m -> m.name).toList());
        assertEquals("PvP › kit pvp", MacroFolderView.label(macros.get(1)));
        assertEquals("home", MacroFolderView.label(macros.get(0)));
    }

    @Test
    void folderListIsDistinctIgnoringCase() {
        assertEquals(List.of("PvP", "skyblock"), MacroFolderView.folders(macros));
    }

    @Test
    void collapsingIsCaseInsensitiveAndReportsChanges() {
        List<String> collapsed = new ArrayList<>();
        assertTrue(MacroFolderView.setCollapsed(collapsed, "PvP", true));
        assertFalse(MacroFolderView.setCollapsed(collapsed, "pvp", true));
        assertTrue(MacroFolderView.isCollapsed(collapsed, "PVP"));
        assertTrue(MacroFolderView.setCollapsed(collapsed, "pvp", false));
        assertTrue(collapsed.isEmpty());
        assertFalse(MacroFolderView.setCollapsed(collapsed, "  ", true));
    }
}
