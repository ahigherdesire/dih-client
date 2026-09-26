package dihclient.util;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
//? if >=26.3 {
/*import dihclient.DihClientAddon;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;

import java.nio.ByteBuffer;
import java.util.ArrayList;
*///?} else {
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.ByteBuffer;
import java.util.ArrayList;
//?}

import java.util.List;
import java.util.function.Consumer;

/**
 * The system "open file" dialog. 26.2 ships tinyfd (a blocking call); 26.3 dropped it for SDL3's dialog, which
 * answers later through a callback, so this API is callback-based on both.
 */
public final class DihFileDialogs {

    private DihFileDialogs() {
    }

    /**
     * Asks for one file. {@code onPicked} gets its path on the game thread, and is not called when the dialog is
     * cancelled or fails.
     *
     * @param extensions file extensions to offer ("txt", "nbt.txt"), or empty for any file
     * @param description what the extensions are ("Text files"), or null
     */
    public static void openFile(String title, @Nullable String defaultPath, List<String> extensions,
                                @Nullable String description, Consumer<String> onPicked) {
        //? if >=26.3 {
        /*Minecraft mc = Minecraft.getInstance();
        List<ByteBuffer> strings = new ArrayList<>();
        SDL_DialogFileFilter.Buffer filters = null;
        if (!extensions.isEmpty()) {
            filters = SDL_DialogFileFilter.calloc(1);
            ByteBuffer name = MemoryUtil.memUTF8(description == null ? String.join(", ", extensions) : description);
            ByteBuffer pattern = MemoryUtil.memUTF8(String.join(";", extensions));
            strings.add(name);
            strings.add(pattern);
            filters.get(0).name(name).pattern(pattern);
        }
        SDL_DialogFileFilter.Buffer ownedFilters = filters;
        SDL_DialogFileCallback[] self = new SDL_DialogFileCallback[1];
        self[0] = SDL_DialogFileCallback.create((userdata, fileList, filter) -> {
            String path = null;
            if (fileList != 0L) {
                long first = MemoryUtil.memGetAddress(fileList);
                if (first != 0L) path = MemoryUtil.memUTF8(first);
            }
            String picked = path;
            // Free on the game thread, after this upcall has returned.
            mc.execute(() -> {
                if (ownedFilters != null) ownedFilters.free();
                strings.forEach(MemoryUtil::memFree);
                self[0].free();
                if (picked != null && !picked.isBlank()) onPicked.accept(picked);
            });
        });
        try {
            SDLDialog.SDL_ShowOpenFileDialog(self[0], 0L, mc.getWindow().handle(), filters,
                defaultPath == null || defaultPath.isBlank() ? null : defaultPath, false);
        } catch (Throwable error) {
            DihClientAddon.LOG.warn("[Dih] Couldn't open the file dialog", error);
            if (ownedFilters != null) ownedFilters.free();
            strings.forEach(MemoryUtil::memFree);
            self[0].free();
        }
        *///?} else {
        List<ByteBuffer> patterns = new ArrayList<>();
        PointerBuffer filters = null;
        try {
            if (!extensions.isEmpty()) {
                filters = MemoryUtil.memAllocPointer(extensions.size());
                for (String extension : extensions) {
                    ByteBuffer pattern = MemoryUtil.memASCII("*." + extension);
                    patterns.add(pattern);
                    filters.put(pattern);
                }
                filters.rewind();
            }
            String selected = TinyFileDialogs.tinyfd_openFileDialog(title,
                defaultPath == null || defaultPath.isBlank() ? null : defaultPath, filters, description, false);
            if (selected != null && !selected.isBlank()) onPicked.accept(selected);
        } finally {
            if (filters != null) MemoryUtil.memFree(filters);
            patterns.forEach(MemoryUtil::memFree);
        }
        //?}
    }
}
