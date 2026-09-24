package dihclient.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihSpotifyParserTest {
    private static final String US = "\u001f";

    @TempDir
    static Path gameDir;

    @BeforeAll
    static void setUp() {
        ensureGameDir(gameDir);
    }

    private static synchronized void ensureGameDir(Path dir) {
        try {
            Object loader = net.fabricmc.loader.api.FabricLoader.getInstance();
            Class<?> impl = Class.forName("net.fabricmc.loader.impl.FabricLoaderImpl");
            Field gd = impl.getDeclaredField("gameDir");
            gd.setAccessible(true);
            if (gd.get(loader) == null) {
                Method m = impl.getDeclaredMethod("setGameDir", Path.class);
                m.setAccessible(true);
                m.invoke(loader, dir);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("could not initialize a test game dir", t);
        }
    }

    private static DihSpotify.Snapshot line(String status, String artist, String title) {
        return DihSpotify.parseLine(status + US + artist + US + title);
    }

    @Test
    void playingLineCarriesArtistAndTitle() {
        DihSpotify.Snapshot s = line("PLAYING", "The Artist", "The Title");
        assertEquals(DihSpotify.Status.PLAYING, s.status());
        assertEquals("The Artist", s.artist());
        assertEquals("The Title", s.title());
    }

    @Test
    void pausedLineKeepsItsTrack() {

        DihSpotify.Snapshot s = line("PAUSED", "The Artist", "The Title");
        assertEquals(DihSpotify.Status.PAUSED, s.status());
        assertEquals("The Title", s.title());
    }

    @Test
    void statusWordsAreCaseInsensitive() {

        assertEquals(DihSpotify.Status.PLAYING, line("Playing", "A", "T").status());
        assertEquals(DihSpotify.Status.PAUSED, line("paused", "A", "T").status());
    }

    @Test
    void titleMayContainPipes() {

        DihSpotify.Snapshot s = line("PLAYING", "Artist", "A | B | C");
        assertEquals("A | B | C", s.title());
    }

    @Test
    void cjkAndUnicodeSurviveIntact() {
        DihSpotify.Snapshot s = line("PLAYING", "米津玄師", "打上花火 ✿");
        assertEquals("米津玄師", s.artist());
        assertEquals("打上花火 ✿", s.title());
    }

    @Test
    void missingArtistParsesAsEmptyString() {
        DihSpotify.Snapshot s = line("PLAYING", "", "Solo Title");
        assertEquals(DihSpotify.Status.PLAYING, s.status());
        assertEquals("", s.artist());
        assertEquals("Solo Title", s.title());
    }

    @Test
    void bareStoppedLineIsStopped() {

        DihSpotify.Snapshot s = DihSpotify.parseLine("STOPPED");
        assertEquals(DihSpotify.Status.STOPPED, s.status());
        assertEquals("", s.artist());
        assertEquals("", s.title());
    }

    @Test
    void bareUnavailableLineIsUnavailable() {
        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseLine("UNAVAILABLE").status());
    }

    @Test
    void emptyTitleMeansStopped() {

        DihSpotify.Snapshot s = line("PLAYING", "Artist", "");
        assertEquals(DihSpotify.Status.STOPPED, s.status());
    }

    @Test
    void lineWithoutTitleFieldMeansStopped() {
        assertEquals(DihSpotify.Status.STOPPED, DihSpotify.parseLine("PAUSED" + US + "Artist").status());
    }

    @Test
    void unknownStatusWordIsUnavailable() {

        assertEquals(DihSpotify.Status.UNAVAILABLE, line("CHANGING", "A", "T").status());
    }

    @Test
    void garbageBlankAndNullNeverThrowAndMeanUnavailable() {
        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseLine("not a protocol line").status());
        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseLine("").status());
        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseLine("   ").status());
        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseLine(null).status());
        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseLine(US + US).status());
    }

    @Test
    void snapshotsAreNeverNullAndAreStamped() {
        DihSpotify.Snapshot s = DihSpotify.parseLine("garbage");
        assertNotNull(s);
        assertNotNull(s.artist());
        assertNotNull(s.title());
        assertTrue(s.updatedAtMs() > 0, "a parsed snapshot must carry its observation time");
    }

    private static String getAll(String playbackStatus, String metadata) {
        return "({'PlaybackStatus': <'" + playbackStatus + "'>, 'LoopStatus': <'None'>, 'Rate': <1.0>, "
            + "'Shuffle': <false>, 'Metadata': <{" + metadata + "}>, 'Volume': <0.8>, "
            + "'Position': <int64 61516000>, 'CanGoNext': <true>, 'CanPlay': <true>, "
            + "'CanPause': <true>, 'CanSeek': <true>, 'CanControl': <true>},)";
    }

    private static String metadata(String artistArray, String titleValue) {
        return "'mpris:trackid': <objectpath '/org/mpris/MediaPlayer2/Track/4uLU6hMCjMI75M1A2tKUQC'>, "
            + "'mpris:length': <uint64 200066000>, "
            + "'mpris:artUrl': <'https://i.scdn.co/image/ab67616d0000b273'>, "
            + "'xesam:album': <'Some Album'>, "
            + (artistArray == null ? "" : "'xesam:artist': <[" + artistArray + "]>, ")
            + "'xesam:discNumber': <1>, "
            + (titleValue == null ? "" : "'xesam:title': <" + titleValue + ">, ")
            + "'xesam:url': <'https://open.spotify.com/track/xyz'>";
    }

    @Test
    void fullGetAllOutputParses() {
        DihSpotify.Snapshot s = DihSpotify.parseGdbus(
            getAll("Playing", metadata("'The Artist'", "'The Title'")));
        assertEquals(DihSpotify.Status.PLAYING, s.status());
        assertEquals("The Artist", s.artist());
        assertEquals("The Title", s.title());
    }

    @Test
    void firstArtistOfTheArrayWins() {

        DihSpotify.Snapshot s = DihSpotify.parseGdbus(
            getAll("Playing", metadata("'First', 'Second', 'Third'", "'Title'")));
        assertEquals("First", s.artist());
    }

    @Test
    void emptyArtistArrayMeansEmptyArtist() {
        DihSpotify.Snapshot s = DihSpotify.parseGdbus(
            getAll("Playing", metadata("", "'Title'")));
        assertEquals(DihSpotify.Status.PLAYING, s.status());
        assertEquals("", s.artist());
    }

    @Test
    void pausedAndStoppedVariants() {
        assertEquals(DihSpotify.Status.PAUSED, DihSpotify.parseGdbus(
            getAll("Paused", metadata("'A'", "'T'"))).status());
        assertEquals(DihSpotify.Status.STOPPED, DihSpotify.parseGdbus(
            getAll("Stopped", metadata("'A'", "'T'"))).status());
    }

    @Test
    void missingTitleMeansStopped() {

        DihSpotify.Snapshot s = DihSpotify.parseGdbus(getAll("Playing", metadata("'A'", null)));
        assertEquals(DihSpotify.Status.STOPPED, s.status());
    }

    @Test
    void missingArtistKeyMeansEmptyArtist() {
        DihSpotify.Snapshot s = DihSpotify.parseGdbus(getAll("Playing", metadata(null, "'Title'")));
        assertEquals(DihSpotify.Status.PLAYING, s.status());
        assertEquals("", s.artist());
        assertEquals("Title", s.title());
    }

    @Test
    void missingPlaybackStatusIsUnavailable() {

        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseGdbus(
            "({'Metadata': <{" + metadata("'A'", "'T'") + "}>},)").status());
    }

    @Test
    void unknownPlaybackStatusIsUnavailable() {
        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseGdbus(
            getAll("Buffering", metadata("'A'", "'T'"))).status());
    }

    @Test
    void doubleQuotedTitleWithApostropheParses() {

        DihSpotify.Snapshot s = DihSpotify.parseGdbus(
            getAll("Playing", metadata("'Queen'", "\"Don't Stop Me Now\"")));
        assertEquals("Don't Stop Me Now", s.title());
    }

    @Test
    void doubleQuotedArtistArrayElementWithApostropheParses() {

        DihSpotify.Snapshot s = DihSpotify.parseGdbus(
            getAll("Playing", metadata("\"Sinéad O'Connor\", 'Other'", "'Nothing Compares 2 U'")));
        assertEquals("Sinéad O'Connor", s.artist());
        assertEquals("Nothing Compares 2 U", s.title());
    }

    @Test
    void escapedBackslashBeforeTheClosingQuoteParses() {

        DihSpotify.Snapshot s = DihSpotify.parseGdbus(
            getAll("Playing", metadata("'A'", "'Trail\\\\'")));
        assertEquals("Trail\\", s.title());
    }

    @Test
    void gdbusGarbageBlankAndNullMeanUnavailable() {

        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseGdbus("").status());
        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseGdbus("  \n ").status());
        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseGdbus(null).status());
        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseGdbus("gdbus: error").status());
    }

    private static DihSpotify.Snapshot osa(String status, String artist, String title) {

        return DihSpotify.parseOsascript(status + US + artist + US + title + "\n");
    }

    @Test
    void osascriptLowercaseStatusesParse() {

        assertEquals(DihSpotify.Status.PLAYING, osa("playing", "A", "T").status());
        assertEquals(DihSpotify.Status.PAUSED, osa("paused", "A", "T").status());
        assertEquals(DihSpotify.Status.STOPPED, osa("stopped", "A", "T").status());
    }

    @Test
    void osascriptLineCarriesArtistAndTitleAndDropsTheTrailingNewline() {
        DihSpotify.Snapshot s = osa("playing", "The Artist", "The Title");
        assertEquals("The Artist", s.artist());
        assertEquals("The Title", s.title());
    }

    @Test
    void osascriptUnicodeAndApostrophesSurviveIntact() {

        DihSpotify.Snapshot s = osa("playing", "Sinéad O'Connor", "Nothing Compares 2 U ～ 打上花火");
        assertEquals("Sinéad O'Connor", s.artist());
        assertEquals("Nothing Compares 2 U ～ 打上花火", s.title());
    }

    @Test
    void osascriptSilenceMeansUnavailableNotStopped() {

        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseOsascript("").status());
        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseOsascript("\n").status());
        assertEquals(DihSpotify.Status.UNAVAILABLE, DihSpotify.parseOsascript(null).status());
    }

    @Test
    void osascriptEmptyTitleMeansStopped() {
        assertEquals(DihSpotify.Status.STOPPED, osa("playing", "Artist", "").status());
    }

    @Test
    void positionOnlyPollsNeverMoveTheContentStamp() {

        DihSpotify.Snapshot first = DihSpotify.parseLine(
            "PLAYING" + US + "Artist" + US + "Title" + US + "12.0" + US + "200.0" + US + "0" + US + "OFF" + US + "80" + US + "");
        DihSpotify.store(first);
        long stamp = first.updatedAtMs();
        DihSpotify.Snapshot later = DihSpotify.parseLine(
            "PLAYING" + US + "Artist" + US + "Title" + US + "13.0" + US + "200.0" + US + "1" + US + "ALL" + US + "55" + US + "/tmp/art.png");
        DihSpotify.store(later);
        assertEquals(stamp, DihSpotify.snapshot().updatedAtMs(),
            "position/volume/shuffle/art movement must not re-stamp");
        assertEquals(13.0, DihSpotify.snapshot().positionSec(), "but the new position still lands");
        assertEquals(55, DihSpotify.snapshot().volume(), "and the new volume lands");
        assertEquals(DihSpotify.Repeat.ALL, DihSpotify.snapshot().repeat());
        assertEquals("/tmp/art.png", DihSpotify.snapshot().artworkPath());
    }

    @Test
    void aRealContentChangeReplacesTheSnapshot() {
        DihSpotify.store(line("PLAYING", "Artist", "Old"));
        DihSpotify.Snapshot next = line("PLAYING", "Artist", "New");
        DihSpotify.store(next);
        assertSame(next, DihSpotify.snapshot());
        assertEquals("New", DihSpotify.snapshot().title());
    }

    @Test
    void aStatusChangeCountsAsContentChange() {

        DihSpotify.store(line("PLAYING", "Artist", "Title"));
        DihSpotify.Snapshot paused = line("PAUSED", "Artist", "Title");
        DihSpotify.store(paused);
        assertSame(paused, DihSpotify.snapshot());
    }

    @Test
    void extendedLineParsesAllFields() {
        DihSpotify.Snapshot s = DihSpotify.parseLine(
            "PLAYING" + US + "The Artist" + US + "The Title" + US + "61.5" + US + "213.0" + US + "1"
                + US + "ALL" + US + "72" + US + "C:\\Temp\\art.png");
        assertEquals(DihSpotify.Status.PLAYING, s.status());
        assertEquals(61.5, s.positionSec(), 1e-9);
        assertEquals(213.0, s.durationSec(), 1e-9);
        assertTrue(s.shuffle());
        assertEquals(DihSpotify.Repeat.ALL, s.repeat());
        assertEquals(72, s.volume());
        assertEquals("C:\\Temp\\art.png", s.artworkPath());
    }

    @Test
    void missingTrailingFieldsGetDefaults() {

        DihSpotify.Snapshot s = line("PAUSED", "A", "T");
        assertEquals(DihSpotify.Status.PAUSED, s.status());
        assertEquals(0.0, s.positionSec());
        assertEquals(0.0, s.durationSec());
        assertTrue(!s.shuffle());
        assertEquals(DihSpotify.Repeat.UNKNOWN, s.repeat());
        assertEquals(-1, s.volume());
        assertEquals("", s.artworkPath());
    }

    @Test
    void malformedTrailingFieldsGetDefaults() {
        DihSpotify.Snapshot s = DihSpotify.parseLine(
            "PLAYING" + US + "A" + US + "T" + US + "abc" + US + "" + US + "maybe" + US + "LOOPS"
                + US + "loud" + US + "");
        assertEquals(DihSpotify.Status.PLAYING, s.status(), "garbage in trailing fields must not kill the line");
        assertEquals(0.0, s.positionSec());
        assertEquals(0.0, s.durationSec());
        assertTrue(!s.shuffle());
        assertEquals(DihSpotify.Repeat.UNKNOWN, s.repeat());
        assertEquals(-1, s.volume());
        assertEquals("", s.artworkPath());
    }

    @Test
    void playerctlLineConvertsUnits() {
        DihSpotify.Snapshot s = DihSpotify.parsePlayerctl(
            "Playing" + US + "Artist" + US + "Title" + US + "61500000" + US + "213000000" + US + "On"
                + US + "Playlist" + US + "0.42" + US + "file:///home/u/art%20work.png");
        assertEquals(DihSpotify.Status.PLAYING, s.status());
        assertEquals(61.5, s.positionSec(), 1e-6);
        assertEquals(213.0, s.durationSec(), 1e-6);
        assertTrue(s.shuffle());
        assertEquals(DihSpotify.Repeat.ALL, s.repeat());
        assertEquals(42, s.volume());
        assertEquals("/home/u/art work.png", s.artworkPath(), "file:// passes through as a local path");
        DihSpotify.Snapshot http = DihSpotify.parsePlayerctl(
            "Playing" + US + "A" + US + "T" + US + "0" + US + "0" + US + "Off" + US + "None"
                + US + "1.0" + US + "https://i.scdn.co/image/abc");
        assertEquals("https://i.scdn.co/image/abc", http.artworkPath(),
            "http(s) urls pass through for the reader-side bounded download");
        assertEquals(100, http.volume());
        assertEquals(DihSpotify.Repeat.OFF, http.repeat());
        assertTrue(!http.shuffle());
    }

    @Test
    void playerctlMissingFieldsGetDefaults() {
        DihSpotify.Snapshot s = DihSpotify.parsePlayerctl("Paused" + US + "A" + US + "T");
        assertEquals(DihSpotify.Status.PAUSED, s.status());
        assertEquals(-1, s.volume());
        assertEquals(DihSpotify.Repeat.UNKNOWN, s.repeat());
        assertEquals("", s.artworkPath());
    }

    @Test
    void osascriptExtendedLineConvertsMilliseconds() {
        DihSpotify.Snapshot s = DihSpotify.parseOsascript(
            "playing" + US + "Artist" + US + "Title" + US + "61500" + US + "213000" + US + "true"
                + US + "false" + US + "65" + US + "/var/tmp/art.png\n");
        assertEquals(DihSpotify.Status.PLAYING, s.status());
        assertEquals(61.5, s.positionSec(), 1e-6);
        assertEquals(213.0, s.durationSec(), 1e-6);
        assertTrue(s.shuffle());
        assertEquals(DihSpotify.Repeat.OFF, s.repeat(), "macOS repeating=false maps to OFF");
        assertEquals(65, s.volume());
        assertEquals("/var/tmp/art.png", s.artworkPath());
        DihSpotify.Snapshot rep = DihSpotify.parseOsascript(
            "playing" + US + "A" + US + "T" + US + "0" + US + "0" + US + "false" + US + "true" + US + "50" + US + "");
        assertEquals(DihSpotify.Repeat.ALL, rep.repeat(), "macOS repeating=true maps to ALL (no single-track repeat)");
    }

    @Test
    void repeatCycleOrderIsOffAllOneOff() {
        assertEquals(DihSpotify.Repeat.ALL, DihSpotify.nextRepeat(DihSpotify.Repeat.OFF));
        assertEquals(DihSpotify.Repeat.ONE, DihSpotify.nextRepeat(DihSpotify.Repeat.ALL));
        assertEquals(DihSpotify.Repeat.OFF, DihSpotify.nextRepeat(DihSpotify.Repeat.ONE));
        assertEquals(DihSpotify.Repeat.OFF, DihSpotify.nextRepeat(DihSpotify.Repeat.UNKNOWN));
        assertEquals(DihSpotify.Repeat.OFF, DihSpotify.nextRepeat(null));
    }

    @Test
    void volumeClampsBeforeTheWire() {
        assertEquals(0, DihSpotify.clampVolume(-5));
        assertEquals(100, DihSpotify.clampVolume(150));
        assertEquals(42, DihSpotify.clampVolume(42));
    }

    @Test
    void sourceFlagRoundTrips() {
        try {
            DihSpotify.setSourceAnywhere(true);
            assertTrue(DihSpotify.sourceAnywhere());
            assertTrue(!String.join(" ", DihSpotify.playerctlArgv()).contains("--player=spotify"),
                "ANY mode must drop --player=spotify from the follow command");
            DihSpotify.setSourceAnywhere(false);
            assertTrue(!DihSpotify.sourceAnywhere());
            assertTrue(String.join(" ", DihSpotify.playerctlArgv()).contains("--player=spotify"),
                "SPOTIFY mode must keep --player=spotify");
        } finally {
            DihSpotify.setSourceAnywhere(false);
        }
    }

    @Test
    void playerctlActionMapsCommands() {
        try {
            DihSpotify.setSourceAnywhere(false);
            String[] vol = DihSpotify.playerctlAction("VOLUME=42");
            assertEquals("volume", vol[vol.length - 2]);
            assertEquals("0.42", vol[vol.length - 1]);
            assertEquals("--player=spotify", vol[1]);
            String[] loop = DihSpotify.playerctlAction("REPEAT=ONE");
            assertEquals("loop", loop[loop.length - 2]);
            assertEquals("Track", loop[loop.length - 1]);
            assertEquals("play-pause", DihSpotify.playerctlAction("PLAY_PAUSE")[2]);
            DihSpotify.setSourceAnywhere(true);
            assertEquals("playerctl", DihSpotify.playerctlAction("NEXT")[0]);
            assertEquals("next", DihSpotify.playerctlAction("NEXT")[1],
                "ANY mode commands drop --player too");
        } finally {
            DihSpotify.setSourceAnywhere(false);
        }
    }

    @Test
    void osascriptRepeatOneDegradesToAll() {
        String[] one = DihSpotify.osascriptAction("REPEAT=ONE");
        assertTrue(one[one.length - 1].contains("set repeating to true"),
            "AppleScript has no single-track repeat: ONE degrades to repeating on");
        String[] off = DihSpotify.osascriptAction("REPEAT=OFF");
        assertTrue(off[off.length - 1].contains("set repeating to false"));
        assertTrue(DihSpotify.osascriptAction("PLAY_PAUSE")[2].contains("playpause"));
        assertTrue(DihSpotify.osascriptAction("VOLUME=65")[2].contains("set sound volume to 65"));
    }

    @Test
    void windowsScriptExposesCommandsAndSourceFilter() {
        String script = DihSpotify.windowsScriptText();
        assertTrue(script.contains("DIH_SPOTIFY_SOURCE"), "script must read the source env var");
        assertTrue(script.contains("GetCurrentSession"), "ANY mode must use GSMTC's current session");
        assertTrue(script.contains("TryTogglePlayPauseAsync"), "play/pause command");
        assertTrue(script.contains("TrySkipNextAsync"), "next command");
        assertTrue(script.contains("TrySkipPreviousAsync"), "previous command");
        assertTrue(script.contains("TryChangeShuffleActiveAsync"), "shuffle commands");
        assertTrue(script.contains("TryChangeRepeatModeAsync"), "repeat commands");
        assertTrue(script.contains("ReadLineAsync"), "stdin commands must be polled non-blocking");
        assertTrue(script.contains("87CE5498-68D6-44E5-9215-6DA47EF883D8"), "ISimpleAudioVolume present for session volume");
    }

    @Test
    void volumeSentinelStaysUnsupported() {

        assertEquals(-1, DihSpotify.parseVolume("-1"));
        assertEquals(0, DihSpotify.parseVolume("0"));
        assertEquals(50, DihSpotify.parseVolume("50"));
        assertEquals(100, DihSpotify.parseVolume("150"), "out-of-range clamps to 100");
        assertEquals(-1, DihSpotify.parseVolume(""));
        assertEquals(-1, DihSpotify.parseVolume("loud"));
    }

    @Test
    void playerctlFormatUsesTheRealLoopVariable() {

        String argv = String.join(" ", DihSpotify.playerctlArgv());
        assertTrue(argv.contains("{{loop}}"), "must use the real {{loop}} variable");
        assertTrue(!argv.contains("{{loopStatus}}"), "{{loopStatus}} must be gone");
        assertEquals(DihSpotify.Repeat.ONE, DihSpotify.parseRepeat("Track"));
        assertEquals(DihSpotify.Repeat.ALL, DihSpotify.parseRepeat("Playlist"));
        assertEquals(DihSpotify.Repeat.OFF, DihSpotify.parseRepeat("None"));
    }

    @Test
    void playerctlArtUsesOneStablePath() throws Exception {

        java.nio.file.Path a = java.nio.file.Files.createTempFile("art-src-a", ".bin");
        java.nio.file.Path b = java.nio.file.Files.createTempFile("art-src-b", ".bin");

        java.nio.file.Files.write(a, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4});
        java.awt.image.BufferedImage realImage = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream jpegBytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(realImage, "jpeg", jpegBytes);
        java.nio.file.Files.write(b, jpegBytes.toByteArray());
        String first = DihSpotify.downloadArt(a.toUri().toString());
        String second = DihSpotify.downloadArt(b.toUri().toString());
        assertTrue(!first.isEmpty(), "a file: download must succeed");
        assertEquals(first, second, "ONE stable temp path rewritten per track, not per-track churn");
        byte[] onDisk = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(second));
        assertTrue(DihImageCodec.isPng(onDisk) && onDisk.length != 8,
            "the second download overwrote the first with the JPEG->PNG re-encode");
        java.nio.file.Files.deleteIfExists(a);
        java.nio.file.Files.deleteIfExists(b);
    }

    @Test
    void windowsScriptDeletesStaleArtAndHasNoBogusSlotComment() {
        String script = DihSpotify.windowsScriptText();
        assertTrue(script.contains("Remove-Item $artPath"),
            "a failed save must delete the PREVIOUS track's art before the reuse branch");
        assertTrue(!script.contains("GetResults (slot 15)"),
            "the B6 comment claimed a GetResults call that does not exist");
    }

    @Test
    void itunesArtworkUrlExtraction() {
        String json = "{\"resultCount\":1,\"results\":[{\"wrapperType\":\"track\",\"artistName\":\"The Artist\","
            + "\"trackName\":\"The Title\",\"artworkUrl100\":\"https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/ab/abcd/100x100bb.jpg\",\"trackId\":123}]}";
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/ab/abcd/100x100bb.jpg",
            DihSpotify.extractArtworkUrl(json));
        assertEquals("", DihSpotify.extractArtworkUrl("{\"resultCount\":0,\"results\":[]}"),
            "no results means empty, never a placeholder");
        assertEquals("", DihSpotify.extractArtworkUrl(null));
        assertEquals("", DihSpotify.extractArtworkUrl("garbage"));
    }

    @Test
    void artworkUrlUpgradeSwaps100For600() {
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/ab/abcd/600x600bb.jpg",
            DihSpotify.upgradeArtworkUrl("https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/ab/abcd/100x100bb.jpg"));
        assertEquals("https://example.com/other.png",
            DihSpotify.upgradeArtworkUrl("https://example.com/other.png"),
            "non-iTunes-shaped urls pass through untouched");
        assertEquals("", DihSpotify.upgradeArtworkUrl(null));
    }

    @Test
    void storePreservesFetchedArtWhenLinesStayEmpty() {

        DihSpotify.Snapshot withArt = DihSpotify.parseLine(
            "PLAYING" + US + "Artist" + US + "Title" + US + "1.0" + US + "200.0" + US + "0" + US + "OFF" + US + "80" + US + "/tmp/fetched.png");
        DihSpotify.store(withArt);
        long stamp = withArt.updatedAtMs();
        DihSpotify.store(DihSpotify.parseLine(
            "PLAYING" + US + "Artist" + US + "Title" + US + "2.0" + US + "200.0" + US + "0" + US + "OFF" + US + "80" + US + ""));
        assertEquals("/tmp/fetched.png", DihSpotify.snapshot().artworkPath(),
            "an empty line art field must not erase the fetched path");
        assertEquals(stamp, DihSpotify.snapshot().updatedAtMs());
        DihSpotify.Snapshot newTrack = DihSpotify.parseLine(
            "PLAYING" + US + "Artist" + US + "Other Song" + US + "0.0" + US + "180.0" + US + "0" + US + "OFF" + US + "80" + US + "");
        DihSpotify.store(newTrack);
        assertEquals("", DihSpotify.snapshot().artworkPath(),
            "a new track starts art-empty again so the fallback re-fetches");
        assertEquals("Other Song", DihSpotify.snapshot().title());
    }

    @Test
    void artContentSniffingRejectsNonImages() throws Exception {
        assertTrue(DihSpotify.isImageBytes(new byte[]{(byte) 0xFF, (byte) 0xD8, 0x10}), "JPEG magic passes");
        assertTrue(DihSpotify.isImageBytes(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}), "PNG magic passes");
        assertTrue(!DihSpotify.isImageBytes("HTTP/1.1 200 OK".getBytes()), "a text body is not an image");
        assertTrue(!DihSpotify.isImageBytes(new byte[]{1, 2}), "too short is not an image");
        assertTrue(!DihSpotify.isImageBytes(null), "null is not an image");

        java.nio.file.Path png = java.nio.file.Files.createTempFile("art-png", ".bin");
        java.nio.file.Files.write(png, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3});
        String good = DihSpotify.downloadArt(png.toUri().toString());
        assertTrue(!good.isEmpty(), "a real PNG downloads");

        java.nio.file.Path text = java.nio.file.Files.createTempFile("art-txt", ".bin");
        java.nio.file.Files.write(text, "<html>error</html>".getBytes());
        assertEquals("", DihSpotify.downloadArt(text.toUri().toString()),
            "a 200-OK text body must be a failure, not a served image");
        assertTrue(java.nio.file.Files.size(java.nio.file.Path.of(good)) > 0,
            "and the rejected download must NOT overwrite the previous good art");
        java.nio.file.Files.deleteIfExists(png);
        java.nio.file.Files.deleteIfExists(text);
    }

    @Test
    void transientArtFailuresAreNotCached() {

        String key = "no-such-artist-xyz|no-such-track-xyz";
        try {
            assertNull(DihSpotify.queryArtFallback(key, (source, term) -> null),
                "a connection failure is transient, not a definitive no-result");
            assertTrue(!DihSpotify.ART_FALLBACK_CACHE.containsKey(key),
                "transient misses must stay OUT of the cache so a replay re-tries");
        } finally {
            DihSpotify.ART_FALLBACK_CACHE.remove(key);
        }
    }

    @Test
    void artFallbackConsultsBothSourcesEveryLookup() throws Exception {

        java.nio.file.Path itunesPng = java.nio.file.Files.createTempFile("art-mtx-i", ".png");
        java.nio.file.Path deezerPng = java.nio.file.Files.createTempFile("art-mtx-d", ".png");
        java.nio.file.Files.write(itunesPng, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4});
        java.nio.file.Files.write(deezerPng, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 5, 6, 7, 8, 9});
        try {
            String iUrl = itunesPng.toUri().toString();
            String dUrl = deezerPng.toUri().toString();
            String iHit = "{\"results\":[{\"artistName\":\"A\",\"trackName\":\"B\",\"artworkUrl100\":\"" + iUrl + "\"}]}";
            String dHit = "{\"data\":[{\"title\":\"B\",\"artist\":{\"name\":\"A\"},\"album\":{\"cover_big\":\"" + dUrl + "\"}}]}";
            String iEmpty = "{\"results\":[]}";
            String dEmpty = "{\"data\":[]}";

            assertArrayEquals(java.nio.file.Files.readAllBytes(itunesPng),
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    DihSpotify.queryArtFallback("A|B", (s, t) -> s.equals("itunes") ? iHit : dHit))),
                "an iTunes strict hit wins outright (iTunes first within the rung)");
            assertArrayEquals(java.nio.file.Files.readAllBytes(deezerPng),
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    DihSpotify.queryArtFallback("A|B", (s, t) -> s.equals("itunes") ? iEmpty : dHit))),
                "an iTunes definitive miss falls through to Deezer in the same cycle");
            assertArrayEquals(java.nio.file.Files.readAllBytes(deezerPng),
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(
                    DihSpotify.queryArtFallback("A|B", (s, t) -> s.equals("itunes") ? null : dHit))),
                "an iTunes TRANSIENT failure must still consult Deezer in the same cycle");
            assertEquals("", DihSpotify.queryArtFallback("A|B", (s, t) -> s.equals("itunes") ? iEmpty : dEmpty),
                "both sources definitively empty at every rung is a cacheable no-result");
            assertNull(DihSpotify.queryArtFallback("A|B", (s, t) -> s.equals("itunes") ? null : dEmpty),
                "iTunes transient + Deezer definitive-empty stays retryable");
            assertNull(DihSpotify.queryArtFallback("A|B", (s, t) -> s.equals("itunes") ? iEmpty : null),
                "Deezer transient + iTunes definitive-empty stays retryable");
            assertNull(DihSpotify.queryArtFallback("A|B", (s, t) -> null),
                "both transient is a plain transient failure");
        } finally {
            java.nio.file.Files.deleteIfExists(itunesPng);
            java.nio.file.Files.deleteIfExists(deezerPng);
        }
    }

    @Test
    void positiveArtCacheServesOnlyTheLatestKey() {

        String a = "cache-test-a-" + System.nanoTime() + "|x";
        String b = "cache-test-b-" + System.nanoTime() + "|x";
        try {
            assertNull(DihSpotify.artCacheLookup(a), "a cold cache has no opinion: run the query");
            DihSpotify.artCacheStore(a, "/tmp/a.img");
            assertEquals("/tmp/a.img", DihSpotify.artCacheLookup(a),
                "the file answers the key whose image it currently holds");
            DihSpotify.artCacheStore(b, "/tmp/b.img");
            assertNull(DihSpotify.artCacheLookup(a),
                "once B owns the file, A's positive entry is stale and must re-query");
            assertEquals("/tmp/b.img", DihSpotify.artCacheLookup(b));
            DihSpotify.artCacheStore(a, "");
            assertEquals("", DihSpotify.artCacheLookup(a),
                "definitive negatives survive positive overwrites");
            assertNull(DihSpotify.artCacheLookup(b + "-other"), "unknown keys stay cacheless");
            DihSpotify.artCacheStore(b + "-other", null);
            assertNull(DihSpotify.artCacheLookup(b + "-other"), "transient results are never stored");
        } finally {
            DihSpotify.ART_FALLBACK_CACHE.remove(a);
            DihSpotify.ART_FALLBACK_CACHE.remove(b);
        }
    }

    @Test
    void fallbackCacheEvictsEldestBeyondCap() {
        java.util.LinkedHashMap<String, String> seen = new java.util.LinkedHashMap<>();
        try {
            int n = DihSpotify.ART_CACHE_MAX_ENTRIES + 5;
            for (int i = 0; i < n; i++) {
                String key = "evict-artist-" + i + "|track-" + i;
                DihSpotify.ART_FALLBACK_CACHE.computeIfAbsent(key, k -> "cached-" + k);
                seen.put(key, "cached-" + key);
            }
            assertTrue(DihSpotify.ART_FALLBACK_CACHE.size() <= DihSpotify.ART_CACHE_MAX_ENTRIES,
                "the cache must stay bounded at " + DihSpotify.ART_CACHE_MAX_ENTRIES);
            assertTrue(!DihSpotify.ART_FALLBACK_CACHE.containsKey("evict-artist-0|track-0"),
                "the eldest entry must be the first evicted");
            assertTrue(DihSpotify.ART_FALLBACK_CACHE.containsKey("evict-artist-" + (n - 1) + "|track-" + (n - 1)),
                "the newest entry must survive");
        } finally {
            for (String key : seen.keySet()) DihSpotify.ART_FALLBACK_CACHE.remove(key);
        }
    }

    @Test
    void windowsScriptSubscribesByAumidAndPublishesArtAtomically() {
        String script = DihSpotify.windowsScriptText();
        assertTrue(script.contains("$subscribedAumid"),
            "event re-subscription must key on the AUMID string, not RCW object identity");
        assertTrue(!script.contains("$subscribedSession"), "object-identity subscription must be gone");
        assertTrue(script.contains("$tmp = $path + '.part'"),
            "art must be published via a temp sibling, never a direct rewrite");
        assertTrue(script.contains("::Copy($tmp, $path, $true)"),
            "and copied over the stable path atomically-ish");
    }

    @Test
    void artStageLogSilentInProduction() {

        String stage = "test-stage-" + System.nanoTime();
        DihSpotify.logArt(stage, "first");
        assertNull(DihSpotify.ART_LOG_LAST.get(stage),
            "with DEBUG shipped false the stage logger must record nothing");
    }

    @Test
    void normalizeMusicTextStripsMarkers() {
        assertEquals("the perfect girl", DihSpotify.normalizeMusicText("The Perfect Girl (The Motion Retrowave Remix)"),
            "parentheticals and the remix marker must both go");
        assertEquals("who is she x the perfect girl", DihSpotify.normalizeMusicText("Who Is She x The Perfect Girl - Slowed & Reverb"),
            "the slowed/reverb markers must go");
        assertEquals("train ride", DihSpotify.normalizeMusicText("Train Ride (Iris)"));
        assertEquals("1000 eyes", DihSpotify.normalizeMusicText("1000 Eyes"));
        assertEquals("song title", DihSpotify.normalizeMusicText("Song Title [Official Audio]"));
        assertEquals("alive", DihSpotify.normalizeMusicText("Alive (feat. Someone)"));
        assertEquals("alive", DihSpotify.normalizeMusicText("Alive (ft. Someone)"));
        assertEquals("alive", DihSpotify.normalizeMusicText("Alive (featuring Someone)"));
        assertEquals("dash", DihSpotify.normalizeMusicText("Dash (Sped Up)"));
        assertEquals("dash", DihSpotify.normalizeMusicText("Dash (Nightcore)"));
        assertEquals("a b c d e f", DihSpotify.normalizeMusicText("A-B/C_D'E\"F"),
            "every non-alphanumeric run collapses to one space");
        assertEquals("", DihSpotify.normalizeMusicText(null));
    }

    @Test
    void artworkCandidateValidationRules() {

        assertTrue(DihSpotify.validatesArtworkCandidate("1000 Eyes", "Train Ride", "1000 Eyes", "Train Ride (Iris)"),
            "exact artist + title-with-parenthetical must MATCH");
        assertTrue(!DihSpotify.validatesArtworkCandidate("Myongz", "Who Is She x The Perfect Girl - Slowed & Reverb",
                "Mareux", "The Perfect Girl"),
            "title match with artist mismatch must REJECT (no shared artist token)");
        assertTrue(!DihSpotify.validatesArtworkCandidate("Myongz", "Who Is She x The Perfect Girl - Slowed & Reverb",
                "I Monster", "Who Is She?"),
            "another artist mismatch must REJECT");
        assertTrue(DihSpotify.validatesArtworkCandidate("Mareux", "The Perfect Girl - Slowed & Reverb",
                "Mareux", "The Perfect Girl (The Motion Retrowave Remix)"),
            "rip-decorated snapshot vs remix-decorated store entry must MATCH");
        assertTrue(!DihSpotify.validatesArtworkCandidate("Mareux", "The Perfect Girl", "Mareux", "A Different Song"),
            "same artist, unrelated title must REJECT (no title containment)");
    }

    @Test
    void itunesCandidateSelectionPrefersFirstValidating() {
        String json = "{\"resultCount\":3,\"results\":["
            + "{\"artistName\":\"Mareux\",\"trackName\":\"The Perfect Girl\",\"artworkUrl100\":\"https://img/100x100bb-1.jpg\"},"
            + "{\"artistName\":\"Myongz\",\"trackName\":\"Who Is She x The Perfect Girl\",\"artworkUrl100\":\"https://img/100x100bb-2.jpg\"},"
            + "{\"artistName\":\"Myongz\",\"trackName\":\"Who Is She\",\"artworkUrl100\":\"https://img/100x100bb-3.jpg\"}]}";
        java.util.List<DihSpotify.ArtCandidate> candidates = DihSpotify.itunesCandidates(json);
        assertEquals(3, candidates.size(), "all three results must parse as candidates");
        assertEquals("Mareux", candidates.get(0).artist());
        assertEquals("The Perfect Girl", candidates.get(0).title());
        DihSpotify.ArtCandidate picked = DihSpotify.firstValidatingCandidate(candidates,
            "Myongz", "Who Is She x The Perfect Girl - Slowed & Reverb");
        assertNotNull(picked, "one of the three must validate");
        assertEquals("https://img/100x100bb-2.jpg", picked.imageUrl(),
            "the FIRST validating candidate wins, not the fuzzy first result");
    }

    @Test
    void deezerResponseParsingAndValidation() {
        String json = "{\"data\":["
            + "{\"title\":\"The Perfect Girl\",\"artist\":{\"name\":\"Mareux\"},\"album\":{\"cover_xl\":\"https://img/xl-1.jpg\"}},"
            + "{\"title\":\"Who Is She x The Perfect Girl\",\"artist\":{\"name\":\"Myongz\"},\"album\":{\"cover_xl\":\"https://img/xl-2.jpg\"}}],\"total\":2}";
        java.util.List<DihSpotify.ArtCandidate> candidates = DihSpotify.deezerCandidates(json);
        assertEquals(2, candidates.size(), "both data items must parse as candidates");
        assertEquals("The Perfect Girl", candidates.get(0).title());
        assertEquals("Mareux", candidates.get(0).artist());
        DihSpotify.ArtCandidate picked = DihSpotify.firstValidatingCandidate(candidates,
            "Myongz", "Who Is She x The Perfect Girl - Slowed & Reverb");
        assertNotNull(picked, "one of the two must validate");
        assertEquals("https://img/xl-2.jpg", picked.imageUrl(),
            "the validating Deezer candidate wins over the fuzzy first");
    }

    @Test
    void ladderDegradesButNeverCollapsesWhileCandidatesExist() throws Exception {

        java.util.List<DihSpotify.ArtCandidate> junk = java.util.List.of(
            new DihSpotify.ArtCandidate("Mareux", "The Perfect Girl", "https://img/1.jpg"),
            new DihSpotify.ArtCandidate("I Monster", "Who Is She?", "https://img/2.jpg"));
        assertNull(DihSpotify.firstValidatingCandidate(junk, "Myongz", "Who Is She x The Perfect Girl - Slowed & Reverb"),
            "the strict rung still rejects every wrong-artist candidate");

        String key = "Myongz|Who Is She x The Perfect Girl - Slowed & Reverb";
        java.util.List<DihSpotify.ArtCandidate> titleOnly = java.util.List.of(
            new DihSpotify.ArtCandidate("Mareux", "The Perfect Girl", "u1"));
        DihSpotify.Selection byTitle = DihSpotify.selectBest(key, titleOnly, java.util.List.of());
        assertEquals("u1", byTitle.candidate().imageUrl(), "a title-only match still serves");
        assertEquals(1, byTitle.rung(), "...at the title rung");

        java.util.List<DihSpotify.ArtCandidate> unrelated = java.util.List.of(
            new DihSpotify.ArtCandidate("Someone Else", "Completely Different", "u2"));
        DihSpotify.Selection topHit = DihSpotify.selectBest(key, unrelated, java.util.List.of());
        assertEquals("u2", topHit.candidate().imageUrl(), "even a fully unrelated candidate beats an empty frame");
        assertEquals(3, topHit.rung(), "...at the last-resort rung");

        assertEquals("", DihSpotify.queryArtFallback("A|B",
                (s, t) -> s.equals("itunes") ? "{\"results\":[]}" : "{\"data\":[]}"),
            "\"\" only when zero candidates exist at every rung on both sources");
        assertEquals("", DihSpotify.queryArtFallback("A|B",
                (s, t) -> s.equals("itunes") ? "{\"resultCount\":0,\"results\":[]}" : "{\"data\":[],\"total\":0}"),
            "same for explicit empty-result payloads");

        java.nio.file.Path good = java.nio.file.Files.createTempFile("art-valid", ".png");
        java.nio.file.Files.write(good, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4});
        try {
            String goodItunes = "{\"results\":[{\"artistName\":\"Myongz\",\"trackName\":\"Who Is She x The Perfect Girl\",\"artworkUrl100\":\"" + good.toUri().toString() + "\"}]}";
            assertTrue(!DihSpotify.queryArtFallback(key, (s, t) -> s.equals("itunes") ? goodItunes : "{\"data\":[]}").isEmpty(),
                "a validating candidate's image must download");
        } finally {
            java.nio.file.Files.deleteIfExists(good);
        }
    }

    @Test
    void artCandidateLadderOrdersStrictThenTitleThenArtistThenAny() {
        java.util.List<DihSpotify.ArtCandidate> candidates = java.util.List.of(
            new DihSpotify.ArtCandidate("Totally Other", "Unrelated Song", "u1"),
            new DihSpotify.ArtCandidate("DVRST", "Some Other Track", "u2"),
            new DihSpotify.ArtCandidate("Someone Else", "Bloody Morning", "u3"),
            new DihSpotify.ArtCandidate("DVRST", "Bloody Morning", "u4"));
        DihSpotify.Selection strict = DihSpotify.selectBest("DVRST|Bloody Morning", candidates, java.util.List.of());
        assertEquals("u4", strict.candidate().imageUrl(), "the strict match wins regardless of list position");
        assertEquals(0, strict.rung());
        DihSpotify.Selection title = DihSpotify.selectBest("Nobody|Bloody Morning (Slowed)", candidates, java.util.List.of());
        assertEquals("u3", title.candidate().imageUrl(), "no strict match: the title-only rung beats artist-only and any");
        assertEquals(1, title.rung());
        DihSpotify.Selection artist = DihSpotify.selectBest("DVRST|Unheard Track", candidates, java.util.List.of());
        assertEquals("u2", artist.candidate().imageUrl(), "no title match: the artist-only rung beats the top hit");
        assertEquals(2, artist.rung());
        DihSpotify.Selection any = DihSpotify.selectBest("Nobody|Nothing Here", candidates, java.util.List.of());
        assertEquals("u1", any.candidate().imageUrl(), "nothing matches at all: the search's top hit beats an empty frame");
        assertEquals(3, any.rung());
        assertNull(DihSpotify.selectBest("DVRST|Bloody Morning", java.util.List.of(), java.util.List.of()).candidate(),
            "zero candidates is the ONLY no-pick (source has literally nothing)");
    }

    @Test
    void artFallbackWidensToArtistOnlyQueryAsLastRung() throws Exception {
        java.nio.file.Path png = java.nio.file.Files.createTempFile("art-widen", ".png");
        java.nio.file.Files.write(png, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4});
        try {
            String url = png.toUri().toString();
            java.util.List<String> seen = new java.util.ArrayList<>();
            java.util.function.BiFunction<String, String, String> trackMissArtistHit = (s, t) -> {
                seen.add(s + ":" + t);
                if (t.equals("a")) {
                    return s.equals("itunes")
                        ? "{\"results\":[{\"artistName\":\"a\",\"trackName\":\"anything\",\"artworkUrl100\":\"" + url + "\"}]}"
                        : "{\"data\":[]}";
                }
                return s.equals("itunes") ? "{\"results\":[]}" : "{\"data\":[]}";
            };
            assertTrue(!DihSpotify.queryArtFallback("a|b", trackMissArtistHit).isEmpty(),
                "track-level definitive-empty on both sources widens to the artist-only query");
            assertTrue(seen.contains("itunes:a"), "the widened rung queries the artist alone");
            java.util.function.BiFunction<String, String, String> empty = (s, t) -> s.equals("itunes") ? "{\"results\":[]}" : "{\"data\":[]}";
            assertEquals("", DihSpotify.queryArtFallback("a|", empty),
                "an already artist-only key never re-widens (one rung deep, no loop)");
            assertEquals("", DihSpotify.queryArtFallback("|b", empty),
                "no artist means there is nothing to widen to");
        } finally {
            java.nio.file.Files.deleteIfExists(png);
        }
    }

    @Test
    void coverJunkDemotionRules() {

        assertTrue(DihSpotify.isJunkCover("21 Savage", "ball w/o you",
            new DihSpotify.ArtCandidate("8-Bit Arcade", "Ball w/o You (8-Bit 21 Savage Emulation)", "u")),
            "chiptune emulation products are junk");
        assertTrue(DihSpotify.isJunkCover("21 Savage", "ball w/o you",
            new DihSpotify.ArtCandidate("Arcade Player", "Ball w/o You (16-Bit 21 Savage Emulation)", "u")));
        assertTrue(DihSpotify.isJunkCover("21 Savage", "ball w/o you",
            new DihSpotify.ArtCandidate("Sunday Without You", "Ball W/O You (Lofi Version)", "u")),
            "lofi-cover versions are junk");
        assertTrue(DihSpotify.isJunkCover("Artist", "Song",
            new DihSpotify.ArtCandidate("The Karaoke Crew", "Song (Karaoke Version)", "u")));
        assertTrue(DihSpotify.isJunkCover("Artist", "Song",
            new DihSpotify.ArtCandidate("Tribute Band", "Song - A Tribute", "u")));

        assertTrue(!DihSpotify.isJunkCover("21 Savage", "ball w/o you",
            new DihSpotify.ArtCandidate("21 Savage", "ball w/o You", "u")),
            "the exact track is never junk");

        assertTrue(!DihSpotify.isJunkCover("Artist", "Song (Lofi Version)",
            new DihSpotify.ArtCandidate("Artist", "Song (Lofi Version)", "u")),
            "a snapshot carrying the marker itself must not junk itself");

        java.util.List<DihSpotify.ArtCandidate> onlyJunk = java.util.List.of(
            new DihSpotify.ArtCandidate("8-Bit Arcade", "Real Song (8-Bit Emulation)", "u"));
        DihSpotify.Selection lastResort = DihSpotify.selectBest("Real Artist|Real Song", onlyJunk, java.util.List.of());
        assertEquals("u", lastResort.candidate().imageUrl(), "junk still serves as the last resort (never nothing)");
        assertEquals(3, lastResort.rung(), "...but only at rung 3, never earlier");
    }

    @Test
    void ballWithoutYouSelectsTheRealCoverOverJunkVersions() throws Exception {

        java.nio.file.Path realPng = java.nio.file.Files.createTempFile("art-real", ".png");
        java.nio.file.Path junkPng = java.nio.file.Files.createTempFile("art-junk", ".png");
        java.nio.file.Files.write(realPng, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 1, 1, 1, 1});
        java.nio.file.Files.write(junkPng, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 2, 2, 2, 2, 2});
        try {
            String junk = junkPng.toUri().toString();
            String real = realPng.toUri().toString();
            String itunes = "{\"resultCount\":5,\"results\":["
                + "{\"artistName\":\"Sunday Without You\",\"trackName\":\"Ball W/O You (Lofi Version)\",\"artworkUrl100\":\"" + junk + "\"},"
                + "{\"artistName\":\"21 Savage\",\"trackName\":\"ball w/o You\",\"artworkUrl100\":\"" + real + "\"},"
                + "{\"artistName\":\"8-Bit Arcade\",\"trackName\":\"Ball w/o You (8-Bit 21 Savage Emulation)\",\"artworkUrl100\":\"" + junk + "\"},"
                + "{\"artistName\":\"Lloyd\",\"trackName\":\"You (Edited)\",\"artworkUrl100\":\"" + junk + "\"},"
                + "{\"artistName\":\"Arcade Player\",\"trackName\":\"Ball w/o You (16-Bit 21 Savage Emulation)\",\"artworkUrl100\":\"" + junk + "\"}]}";
            String path = DihSpotify.queryArtFallback("21 Savage|ball w/o you",
                (s, t) -> s.equals("itunes") ? itunes : "{\"data\":[]}");
            assertTrue(!path.isEmpty(), "the real track's art must download");
            assertArrayEquals(java.nio.file.Files.readAllBytes(realPng),
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path)),
                "the strict rung must pick the REAL 21 Savage cover, never a junk version");
        } finally {
            java.nio.file.Files.deleteIfExists(realPng);
            java.nio.file.Files.deleteIfExists(junkPng);
        }
    }

    @Test
    void poundsAndShroomsPrefersDeezerStrictOverItunesArtistRung() throws Exception {

        java.nio.file.Path realPng = java.nio.file.Files.createTempFile("art-real2", ".png");
        java.nio.file.Path albumPng = java.nio.file.Files.createTempFile("art-album2", ".png");
        java.nio.file.Files.write(realPng, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 3, 3, 3, 3});
        java.nio.file.Files.write(albumPng, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 4, 4, 4, 4, 4});
        try {
            String album = albumPng.toUri().toString();
            String real = realPng.toUri().toString();
            String itunes = "{\"resultCount\":4,\"results\":["
                + "{\"artistName\":\"Wiz Khalifa\",\"trackName\":\"Dirty Laundry\",\"artworkUrl100\":\"" + album + "\"},"
                + "{\"artistName\":\"Wiz Khalifa\",\"trackName\":\"A Helping Hand\",\"artworkUrl100\":\"" + album + "\"},"
                + "{\"artistName\":\"Wiz Khalifa\",\"trackName\":\"Make a Play (feat. J.R. Donato)\",\"artworkUrl100\":\"" + album + "\"},"
                + "{\"artistName\":\"Fetty Wap\",\"trackName\":\"Like A Taylor (feat. Wiz Khalifa)\",\"artworkUrl100\":\"" + album + "\"}]}";
            String deezer = "{\"data\":[{\"title\":\"Pounds and Shrooms\",\"artist\":{\"name\":\"Wiz Khalifa\"},"
                + "\"album\":{\"cover_big\":\"" + real + "\"}}],\"total\":1}";
            String path = DihSpotify.queryArtFallback("Wiz Khalifa|Pounds And Shrooms",
                (s, t) -> s.equals("itunes") ? itunes : deezer);
            assertTrue(!path.isEmpty(), "the exact song's art must download");
            assertArrayEquals(java.nio.file.Files.readAllBytes(realPng),
                java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path)),
                "Deezer's strict match must beat iTunes's artist-rung album cover");
        } finally {
            java.nio.file.Files.deleteIfExists(realPng);
            java.nio.file.Files.deleteIfExists(albumPng);
        }
    }

}
