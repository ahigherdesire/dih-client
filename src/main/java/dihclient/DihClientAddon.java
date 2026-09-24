package dihclient;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public final class DihClientAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final String MOD_ID = "dih";

    public static final boolean DEBUG = false;

    public static final java.io.File FOLDER = FabricLoader.getInstance().getConfigDir().resolve("dih").toFile();

    static {
        FOLDER.mkdirs();
    }

    private DihClientAddon() {}
}
