package baritone.beat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Campaign files, one per world: {@code <dir>/<world key>.json}, written whole so a crash never leaves half a file. */
public final class CampaignStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dir;

    public CampaignStore(Path dir) {
        this.dir = dir;
    }

    public Path file(String world) {
        return dir.resolve(world + ".json");
    }

    /** The saved campaign for {@code world}, or empty when there is none (or it can't be read). */
    public Optional<Campaign> load(String world) {
        Path file = file(world);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.of(Campaign.fromJson(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject(), world));
        } catch (IOException | RuntimeException e) {
            System.err.println("[DIH] could not read the #beat campaign " + file + ": " + e);
            return Optional.empty();
        }
    }

    public void save(Campaign campaign, long now) throws IOException {
        Files.createDirectories(dir);
        Path file = file(campaign.world());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(campaign.toJson(now)), StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void delete(String world) throws IOException {
        Files.deleteIfExists(file(world));
    }
}
