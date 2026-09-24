package dihclient.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DihProfile {

    public String id = "";

    public String displayName = "";

    public boolean autoSave = false;

    public boolean ownMacroLibrary = false;

    public boolean ownThemeColor = true;

    public List<String> serverPatterns = new ArrayList<>();
    public long createdAt = 0L;
    public long updatedAt = 0L;
    public int schemaVersion = 1;

    public DihConfig snapshot = new DihConfig();

    public DihProfile() {}

    public DihProfile(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public static String sanitizeId(String raw) {
        String base = (raw == null ? "" : raw).toLowerCase(Locale.ROOT).strip()
            .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return base.isBlank() ? "profile" : base;
    }

    public void normalize() {

        id = sanitizeId(id);
        if (displayName == null || displayName.isBlank()) displayName = id;
        if (serverPatterns == null) serverPatterns = new ArrayList<>();
        if (snapshot == null) snapshot = new DihConfig();
        snapshot.applyRuntimeDefaults();
        if (schemaVersion <= 0) schemaVersion = 1;
    }
}
