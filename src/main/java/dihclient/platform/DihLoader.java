package dihclient.platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Mod-loader queries (Fabric Loader, NeoForge's FML or Forge's FML). No Minecraft types, so mixin plugins can call it
 * before the game's classes load. Client events are in {@link DihPlatform}.
 */
public final class DihLoader {

    private DihLoader() {
    }

    /** "fabric", "neoforge" or "forge". */
    public static String loaderName() {
        //? if fabric {
        return "fabric";
        //?} elif neoforge {
        /*return "neoforge";
        *///?} else {
        /*return "forge";
        *///?}
    }

    /** Whether a mod with this id is loaded. Safe from mixin plugins, before mods are constructed. */
    public static boolean isModLoaded(String id) {
        //? if fabric {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id);
        //?} elif neoforge {
        /*net.neoforged.fml.ModList mods = net.neoforged.fml.ModList.get();
        if (mods != null) return mods.isLoaded(id);
        net.neoforged.fml.loading.FMLLoader loader = net.neoforged.fml.loading.FMLLoader.getCurrentOrNull();
        return loader != null && loader.getLoadingModList().getModFileById(id) != null;
        *///?} else {
        /*return net.minecraftforge.fml.loading.LoadingModList.getModFileById(id) != null;
        *///?}
    }

    public static Path configDir() {
        //? if fabric {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        //?} elif neoforge {
        /*return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        *///?} else {
        /*return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
        *///?}
    }

    public static Path gameDir() {
        //? if fabric {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
        //?} elif neoforge {
        /*return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
        *///?} else {
        /*return net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
        *///?}
    }

    public static boolean isDevelopment() {
        //? if fabric {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();
        //?} elif neoforge {
        /*return !net.neoforged.fml.loading.FMLEnvironment.isProduction();
        *///?} else {
        /*return !net.minecraftforge.fml.loading.FMLLoader.isProduction();
        *///?}
    }

    /** A loaded mod's version string. */
    public static Optional<String> modVersion(String id) {
        //? if fabric {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(id)
            .map(m -> m.getMetadata().getVersion().getFriendlyString());
        //?} elif neoforge {
        /*return net.neoforged.fml.ModList.get().getModContainerById(id)
            .map(c -> c.getModInfo().getVersion().toString());
        *///?} else {
        /*return net.minecraftforge.fml.ModList.getModContainerById(id)
            .map(c -> c.getModInfo().getVersion().toString());
        *///?}
    }

    /** The jar (or directory) a loaded mod was loaded from. */
    public static Optional<Path> modPath(String id) {
        //? if fabric {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(id)
            .flatMap(c -> c.getOrigin().getPaths().stream().findFirst());
        //?} elif neoforge {
        /*var file = net.neoforged.fml.ModList.get().getModFileById(id);
        return file == null ? Optional.empty() : Optional.of(file.getFile().getFilePath());
        *///?} else {
        /*var file = net.minecraftforge.fml.ModList.getModFileById(id);
        return file == null ? Optional.empty() : Optional.of(file.getFile().getFilePath());
        *///?}
    }

    /** Ids of every loaded mod. */
    public static List<String> modIds() {
        List<String> ids = new ArrayList<>();
        //? if fabric {
        for (var mod : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) ids.add(mod.getMetadata().getId());
        //?} elif neoforge {
        /*for (var mod : net.neoforged.fml.ModList.get().getMods()) ids.add(mod.getModId());
        *///?} else {
        /*for (var mod : net.minecraftforge.fml.ModList.getMods()) ids.add(mod.getModId());
        *///?}
        return ids;
    }
}
