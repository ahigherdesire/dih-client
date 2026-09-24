package dihclient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DihLegacyMigrationTest {

    @Test
    void renamesOldPrefixesOnly() {
        assertEquals("dih-accounts.nbt", DihLegacyMigration.rename("xinyuan-accounts.nbt"));
        assertEquals("dih_macros.nbt", DihLegacyMigration.rename("autism_macros.nbt"));
        assertEquals("dih", DihLegacyMigration.rename("XINYUAN"));
        assertNull(DihLegacyMigration.rename("options.txt"));
        assertNull(DihLegacyMigration.rename("dih-accounts.nbt"));
    }

    @Test
    void copiesDataWithoutOverwriting(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("xinyuan-accounts.nbt"), "old accounts");
        Files.createDirectories(dir.resolve("xinyuan"));
        Files.writeString(dir.resolve("xinyuan").resolve("xinyuan_macros.nbt"), "macros");
        Files.writeString(dir.resolve("xinyuan-proxies.nbt"), "old proxies");
        Files.writeString(dir.resolve("dih-proxies.nbt"), "new proxies");

        DihLegacyMigration.migrateDir(dir);
        DihLegacyMigration.migrateDir(dir); // idempotent

        assertEquals("old accounts", Files.readString(dir.resolve("dih-accounts.nbt")));
        assertEquals("macros", Files.readString(dir.resolve("dih").resolve("dih_macros.nbt")));
        assertEquals("new proxies", Files.readString(dir.resolve("dih-proxies.nbt")), "never overwrites");
        assertTrue(Files.exists(dir.resolve("xinyuan-accounts.nbt")), "old data kept for rollback");
    }
}
