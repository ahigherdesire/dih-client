package dihclient.addons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonJanitorTest {
    @TempDir
    Path dir;

    @Test
    void deletesOldJarThenMovesTheNewOneIn() throws Exception {
        Path old = Files.writeString(dir.resolve("addon-1.0.jar"), "old");
        Path part = Files.writeString(dir.resolve("addon-1.1.jar.part"), "new");
        Path target = dir.resolve("addon-1.1.jar");
        Path plan = dir.resolve("plan.txt");
        Files.write(plan, List.of("delete\t" + old, "move\t" + part + "\t" + target));

        assertTrue(AddonJanitor.run(plan));
        assertFalse(Files.exists(old));
        assertFalse(Files.exists(part));
        assertEquals("new", Files.readString(target));
        assertFalse(Files.exists(plan));
    }

    @Test
    void stopsAtTheFirstFailureAndKeepsTheRest() throws Exception {
        Path notEmpty = Files.createDirectories(dir.resolve("locked.jar"));
        Files.writeString(notEmpty.resolve("x"), "x"); // a non-empty directory cannot be deleted
        Path part = Files.writeString(dir.resolve("a.jar.part"), "new");
        Path plan = dir.resolve("plan.txt");
        Files.write(plan, List.of("delete\t" + notEmpty, "move\t" + part + "\t" + dir.resolve("a.jar")));

        assertFalse(AddonJanitor.run(plan));
        assertTrue(Files.exists(part), "the new jar must not land while the old one is still there");
        assertEquals(2, Files.readAllLines(plan).size());
    }

    @Test
    void missingFilesCountAsDone() throws Exception {
        Path plan = dir.resolve("plan.txt");
        Files.write(plan, List.of("delete\t" + dir.resolve("gone.jar"), "move\t" + dir.resolve("gone.part") + "\t" + dir.resolve("x.jar")));
        assertTrue(AddonJanitor.run(plan));
    }
}
