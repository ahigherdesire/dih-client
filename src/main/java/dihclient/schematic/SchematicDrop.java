package dihclient.schematic;

import baritone.utils.schematic.format.SchematicLoader;
import dihclient.gui.screen.DihSchematicsScreen;
import dihclient.modules.Module;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihFileDialogs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Schematic files dropped on the game window, on any screen or in game, or picked with the system "open file" dialog:
 * copied into the schematics folder, and in a world a single file is placed in front of the player with the mouse
 * placement screen open.
 */
public final class SchematicDrop {

    private SchematicDrop() {}

    /**
     * @return true if every dropped file was a schematic and was handled here; otherwise the game handles the drop
     *     as usual (resource packs, worlds)
     */
    public static boolean handle(Minecraft mc, List<Path> paths) {
        if (paths.isEmpty() || !paths.stream().allMatch(SchematicFiles::isSchematic)) {
            return false;
        }
        List<String> imported = new ArrayList<>();
        for (Path path : paths) {
            try {
                imported.add(Schematics.importFile(path));
            } catch (IllegalArgumentException e) {
                toast(mc, "Couldn't copy schematic", e.getMessage());
            }
        }
        if (imported.isEmpty()) {
            return true;
        }
        Screen current = mc.gui.screen();
        if (mc.level == null || mc.player == null) {
            toast(mc, imported.size() == 1 ? "Schematic saved" : imported.size() + " schematics saved",
                "Join a world and open Schematics to place " + (imported.size() == 1 ? imported.getFirst() : "them"));
            return true;
        }
        if (imported.size() > 1) {
            toast(mc, imported.size() + " schematics saved", "Click one to place it");
            mc.gui.setScreen(new DihSchematicsScreen(current instanceof DihSchematicsScreen ? null : current));
            return true;
        }
        try {
            SchematicPlacement placement = SchematicActions.place(mc, imported.getFirst(), null);
            Module module = ModuleRegistry.get("schematics");
            if (module != null && !module.isEnabled()) {
                module.setEnabled(true);
            }
            SchematicPlacing.start(placement);
            mc.gui.setScreen(null);
        } catch (IllegalArgumentException e) {
            toast(mc, "Couldn't place " + imported.getFirst(), e.getMessage());
        }
        return true;
    }

    /**
     * Opens the system file dialog (starting in Downloads) and handles the picked file like a dropped one.
     */
    public static void pick(Minecraft mc) {
        Path downloads = SchematicFiles.downloadsFolder();
        String start = Files.isDirectory(downloads) ? downloads + File.separator : null;
        DihFileDialogs.openFile("Open schematic", start, SchematicLoader.EXTENSIONS,
            "Schematics (.litematic, .schematic, .schem, .nbt)", picked -> {
                Path path;
                try {
                    path = Path.of(picked);
                } catch (java.nio.file.InvalidPathException e) {
                    return;
                }
                if (!handle(mc, List.of(path))) {
                    toast(mc, "Not a schematic", path.getFileName() + " isn't a .litematic, .schematic, .schem or .nbt file");
                }
            });
    }

    private static void toast(Minecraft mc, String title, String body) {
        SystemToast.addOrUpdate(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
            Component.literal(title), Component.literal(body));
    }
}
