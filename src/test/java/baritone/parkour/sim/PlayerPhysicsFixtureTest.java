package baritone.parkour.sim;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Replays every trace recorded from the real client by {@code ParkourTraceGameTest} (in
 * {@code src/test/resources/parkour/}) through the simulator: position must match within 1e-6 blocks on every tick,
 * and the on-ground flag exactly.
 */
final class PlayerPhysicsFixtureTest {
    private static final double TOLERANCE = 1.0E-6;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @TestFactory
    Stream<DynamicTest> everyFixtureMatchesTheRealClient() throws IOException, URISyntaxException {
        List<Path> files = fixtures();
        assertFalse(files.isEmpty(), "no fixtures in src/test/resources/parkour/");
        List<DynamicTest> tests = new ArrayList<>();
        for (Path file : files) {
            tests.add(DynamicTest.dynamicTest(file.getFileName().toString(), () -> replay(TraceFixture.fromJson(Files.readString(file, StandardCharsets.UTF_8)))));
        }
        return tests.stream();
    }

    private static void replay(TraceFixture fixture) {
        BlockSource world = fixture.world();
        PlayerState s = fixture.start;
        for (int tick = 0; tick < fixture.inputs.size(); tick++) {
            s = PlayerPhysics.step(s, fixture.inputs.get(tick), world);
            double[] r = fixture.expected.get(tick);
            String where = fixture.name + " tick " + (tick + 1) + " input " + fixture.inputs.get(tick).compact();
            assertEquals(r[0], s.x, TOLERANCE, where + " x");
            assertEquals(r[1], s.y, TOLERANCE, where + " y");
            assertEquals(r[2], s.z, TOLERANCE, where + " z");
            assertEquals(r[6] == 1, s.onGround, where + " onGround");
        }
    }

    private static List<Path> fixtures() throws IOException, URISyntaxException {
        URL dir = PlayerPhysicsFixtureTest.class.getResource("/parkour");
        if (dir == null) return List.of();
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    @SuppressWarnings("unused")
    private static String read(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
