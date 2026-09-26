package dihclient;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class DihClientAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final String MOD_ID = "dih";

    public static final boolean DEBUG = false;

    public static final java.io.File FOLDER = dihclient.platform.DihLoader.configDir().resolve("dih").toFile();

    static {
        FOLDER.mkdirs();
    }

    private DihClientAddon() {}
}
