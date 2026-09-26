package baritone.acquire.knowledge;

import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback for {@link VanillaData}: lists the same files through the game's own vanilla data pack
 * ({@link ServerPacksSource#createVanillaPackSource()}, which OreSim also uses on multiplayer). Needs a
 * bootstrapped game, so it is only tried at runtime.
 */
final class VanillaPackData {
    private static final List<String> DIRS = List.of("recipe", "loot_table/blocks", "loot_table/entities", "tags/item", "tags/block");

    private VanillaPackData() {
    }

    static Map<String, String> read() {
        Map<String, String> out = new HashMap<>();
        //? if >=26.3 {
        /*{
            PackResources pack = ServerPacksSource.createVanillaPackSource().fullResources();
        *///?} else {
        try (VanillaPackResources pack = ServerPacksSource.createVanillaPackSource()) {
        //?}
            for (String dir : DIRS) {
                pack.listResources(PackType.SERVER_DATA, "minecraft", dir, (id, supplier) -> {
                    String path = "data/" + id.getNamespace() + "/" + id.getPath();
                    if (!VanillaData.wanted(path)) return;
                    try (InputStream in = supplier.get()) {
                        out.put(path, new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    } catch (IOException ignored) {
                    }
                });
            }
        }
        return out;
    }
}
