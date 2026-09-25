package baritone.util;

import baritone.api.utils.Helper;
import baritone.command.defaults.ClientStructureFinder;
import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * SeedCrackerX entrypoint (fabric.mod.json → {@code "seedcrackerx"}): fills in the server's seed
 * for {@code #seedmap}, {@code #structure}, {@code #where} and OreSim so it never has to be typed.
 *
 * <p>SeedCrackerX finds seeds two ways and we listen to both:
 * <ul>
 *   <li>a finished crack, which it pushes to {@link #pushWorldSeed(long)} (off-thread);</li>
 *   <li>its online seed database, which it only prints to chat — so on joining a server (and on
 *       every dimension change) we ask {@code Database.getSeed} ourselves.</li>
 * </ul>
 * Either way the seed is only stored if it matches the seed hash the server sent, so a wrong seed
 * can't replace a right one. Fabric only instantiates this class when SeedCrackerX asks for it,
 * so without SeedCrackerX installed nothing here loads.
 */
public final class DihSeedCrackerPlugin implements SeedCrackerAPI, Helper {

    private static final String DATABASE_CLASS = "kaptainwutax.seedcrackerX.util.Database";
    /** SeedCrackerX downloads its database in the background at startup, so keep asking for a while. */
    private static final int DATABASE_RETRY_TICKS = 100;
    private static final int DATABASE_MAX_TRIES = 12;

    private Method databaseGetSeed;
    private boolean databaseUnavailable;
    private String lookupKey;
    private int lookupTries;
    private int lookupCooldown;

    public DihSeedCrackerPlugin() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    @Override
    public void pushWorldSeed(long seed) {
        Minecraft.getInstance().execute(() -> offer(seed, "cracked"));
    }

    private void tick(Minecraft mc) {
        if (databaseUnavailable || mc.level == null || mc.getSingleplayerServer() != null) {
            lookupKey = null;
            return;
        }
        ClientPacketListener connection = mc.getConnection();
        Long hash = ClientStructureFinder.serverSeedHash();
        if (connection == null || hash == null) return;

        String address = connection.getConnection().getRemoteAddress().toString();
        String key = address + "|" + hash;
        if (!Objects.equals(key, lookupKey)) {
            lookupKey = key;
            lookupTries = 0;
            lookupCooldown = 0;
        }
        if (lookupTries >= DATABASE_MAX_TRIES || --lookupCooldown > 0) return;
        lookupTries++;
        lookupCooldown = DATABASE_RETRY_TICKS;

        Long seed = databaseSeed(address, hash);
        if (seed != null) {
            lookupTries = DATABASE_MAX_TRIES;
            offer(seed, "from SeedCrackerX's database");
        }
    }

    private Long databaseSeed(String address, long hash) {
        try {
            if (databaseGetSeed == null) {
                databaseGetSeed = Class.forName(DATABASE_CLASS).getMethod("getSeed", String.class, long.class);
            }
            return (Long) databaseGetSeed.invoke(null, address, hash);
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            // A SeedCrackerX version with a different internal layout: cracks still reach us.
            databaseUnavailable = true;
            return null;
        }
    }

    /** Game thread only. */
    private void offer(long seed, String how) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getSingleplayerServer() != null) return;
        if (ClientStructureFinder.check(seed) != ClientStructureFinder.Verdict.MATCH) return;
        if (ClientStructureFinder.hasSeed() && ClientStructureFinder.getSeed() == seed) return;

        ClientStructureFinder.setSeed(seed);
        logDirect("✔ Seed " + seed + " " + how + " — verified and saved for "
            + ClientStructureFinder.scope() + ". #seedmap is ready.", ChatFormatting.GREEN);
    }
}
