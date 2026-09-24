package dihclient.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanoramaRecolorTest {
    private static final String FACE = "/assets/dihclient/textures/gui/title/background/panorama_0.png";

    private static BufferedImage face() throws Exception {
        try (InputStream in = PanoramaRecolorTest.class.getResourceAsStream(FACE)) {
            assertNotNull(in, "panorama_0.png missing from resources");
            return ImageIO.read(in);
        }
    }

    private static double meanHue(int[] pixels) {
        double x = 0, y = 0, weight = 0;
        for (int argb : pixels) {
            float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
            if (hsb[1] < 0.05f) continue;
            double angle = hsb[0] * 2 * Math.PI;
            x += Math.cos(angle) * hsb[1];
            y += Math.sin(angle) * hsb[1];
            weight += hsb[1];
        }
        if (weight == 0) return -1;
        double deg = Math.toDegrees(Math.atan2(y, x));
        return deg < 0 ? deg + 360 : deg;
    }

    private static boolean isRedish(double hueDeg) {
        return hueDeg >= 0 && (hueDeg <= 40 || hueDeg >= 320);
    }

    private static double meanSaturationOfChanged(int[] before, int[] after) {
        double total = 0;
        int n = 0;
        for (int i = 0; i < before.length; i++) {
            if (before[i] == after[i]) continue;
            float[] hsb = java.awt.Color.RGBtoHSB((after[i] >> 16) & 0xFF, (after[i] >> 8) & 0xFF, after[i] & 0xFF, null);
            total += hsb[1];
            n++;
        }
        return n == 0 ? 0 : total / n;
    }

    @Test
    void aRedBackdropMapsTheBlueArtOntoRed() throws Exception {
        DihConfig.ThemeColors red = new DihConfig.ThemeColors();
        red.master = 0xFFFF3B3B; // the original stock red; simple mode: every accent channel follows master (DIH's stock theme is blue)
        DihTheme.State stock = DihTheme.State.from(red);
        float hue = stock.hueOf(DihTheme.Channel.BACKDROP);
        float sat = stock.satOf(DihTheme.Channel.BACKDROP);

        BufferedImage img = face();
        int w = img.getWidth(), h = img.getHeight();
        int[] before = img.getRGB(0, 0, w, h, null, 0, w);
        int[] after = new int[before.length];
        int changed = 0;
        for (int i = 0; i < before.length; i++) {
            after[i] = DihTheme.recolorImagePixelTo(before[i], hue, sat);
            if (after[i] != before[i]) changed++;
        }

        double sourceHue = meanHue(before);
        double resultHue = meanHue(after);
        assertTrue(!isRedish(sourceHue),
            "source art should NOT already be red, else this test proves nothing (was " + sourceHue + " deg)");
        assertTrue(changed > before.length / 4,
            "expected a large share of the art to be remapped, changed=" + changed + "/" + before.length);
        assertTrue(isRedish(resultHue),
            "a red backdrop must leave the panorama red, but mean hue was " + resultHue + " deg");

        double resultSat = meanSaturationOfChanged(before, after);
        assertTrue(resultSat > 0.45,
            "recolored panorama is washed out: mean saturation " + resultSat + " (target " + sat + ")");
        assertTrue(resultSat <= sat + 0.001,
            "recolor must not exceed the target saturation: " + resultSat + " > " + sat);
    }

    @Test
    void aDesaturatedTargetStillDesaturatesTheArt() throws Exception {

        BufferedImage img = face();
        int w = img.getWidth(), h = img.getHeight();
        int[] before = img.getRGB(0, 0, w, h, null, 0, w);
        int[] after = new int[before.length];
        for (int i = 0; i < before.length; i++) {
            after[i] = DihTheme.recolorImagePixelTo(before[i], 0.0f, 0.05f);
        }
        double resultSat = meanSaturationOfChanged(before, after);
        assertTrue(resultSat < 0.10, "a near-gray target must stay near-gray, got " + resultSat);
    }

    @Test
    void aCustomBackdropMapsTheArtOntoThatColourInstead() throws Exception {

        float hue = 120.0f / 360.0f;
        BufferedImage img = face();
        int w = img.getWidth(), h = img.getHeight();
        int[] before = img.getRGB(0, 0, w, h, null, 0, w);
        int[] after = new int[before.length];
        for (int i = 0; i < before.length; i++) {
            after[i] = DihTheme.recolorImagePixelTo(before[i], hue, 0.8f);
        }
        double resultHue = meanHue(after);
        assertTrue(resultHue > 80 && resultHue < 170, "expected a green-dominant result, got " + resultHue + " deg");
    }

    @Test
    void stockThemeRecolorsTheUiBlue() {
        DihTheme.State stock = DihTheme.State.from(new DihConfig.ThemeColors());
        assertTrue(stock.isActive(DihTheme.Channel.ACCENT), "stock theme must recolor the red source art");
        float hueDeg = stock.hueOf(DihTheme.Channel.ACCENT) * 360.0f;
        assertTrue(hueDeg > 200 && hueDeg < 250, "stock accent should be blue, was " + hueDeg + " deg");
    }
}
