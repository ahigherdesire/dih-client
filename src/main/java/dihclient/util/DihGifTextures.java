package dihclient.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Optional;

/**
 * Lays a GIF frame out as an entity texture so the vanilla models can draw it with their own UVs:
 * a 64x32 cape sheet (outer face at 1,1 10x16, inner face at 12,1) and a 64x64 shield sheet
 * (plate front at 1,1 12x22) on top of the vanilla wooden shield. Sheets are upscaled by a whole
 * factor (capped to keep per-frame memory small) so the picture keeps some resolution.
 */
public final class DihGifTextures {
    private static final int MAX_SCALE = 4;
    private static final Identifier SHIELD_BASE =
        Identifier.withDefaultNamespace("textures/entity/shield/shield_base_nopattern.png");
    private static final int SHIELD_FALLBACK = 0xFF6B4A2B;

    private DihGifTextures() {
    }

    /** A cape sheet for {@code frame}. A 2:1 image is taken to already be cape art and used as-is. */
    public static NativeImage capeSheet(NativeImage frame) {
        int w = frame.getWidth();
        int h = frame.getHeight();
        if (w == 2 * h && w >= 64) return frame;

        int scale = scaleFor(w, h, 10, 16);
        NativeImage out = new NativeImage(64 * scale, 32 * scale, true);
        int background = averageColor(frame);
        fill(out, background);
        drawCover(frame, out, scale, scale, 10 * scale, 16 * scale);
        drawCover(frame, out, 12 * scale, scale, 10 * scale, 16 * scale);
        return out;
    }

    /** A shield sheet: the vanilla undecorated shield with {@code frame} covering the plate front. */
    public static NativeImage shieldSheet(NativeImage frame) {
        int scale = scaleFor(frame.getWidth(), frame.getHeight(), 12, 22);
        NativeImage out = new NativeImage(64 * scale, 64 * scale, true);
        NativeImage base = readTexture(SHIELD_BASE);
        if (base == null) {
            fill(out, SHIELD_FALLBACK);
        } else {
            try {
                for (int y = 0; y < out.getHeight(); y++) {
                    int sy = Math.min(base.getHeight() - 1, y * base.getHeight() / out.getHeight());
                    for (int x = 0; x < out.getWidth(); x++) {
                        int sx = Math.min(base.getWidth() - 1, x * base.getWidth() / out.getWidth());
                        out.setPixel(x, y, base.getPixel(sx, sy));
                    }
                }
            } finally {
                base.close();
            }
        }
        drawCover(frame, out, scale, scale, 12 * scale, 22 * scale);
        return out;
    }

    private static int scaleFor(int width, int height, int faceWidth, int faceHeight) {
        int needed = (int) Math.ceil(Math.max(width / (double) faceWidth, height / (double) faceHeight));
        return Math.max(1, Math.min(MAX_SCALE, needed));
    }

    /** Scales {@code src} to cover the rectangle (cropping the overflow) and blends it over what is there. */
    private static void drawCover(NativeImage src, NativeImage dst, int x0, int y0, int width, int height) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        double scale = Math.max(width / (double) sw, height / (double) sh);
        double offsetX = (sw - width / scale) / 2.0;
        double offsetY = (sh - height / scale) / 2.0;
        for (int dy = 0; dy < height; dy++) {
            int sy = clamp((int) (offsetY + (dy + 0.5) / scale), sh);
            for (int dx = 0; dx < width; dx++) {
                int sx = clamp((int) (offsetX + (dx + 0.5) / scale), sw);
                int x = x0 + dx;
                int y = y0 + dy;
                dst.setPixel(x, y, blend(src.getPixel(sx, sy), dst.getPixel(x, y)));
            }
        }
    }

    /** {@code top} over an opaque {@code bottom}, both ARGB; the result is opaque (the models draw solid). */
    private static int blend(int top, int bottom) {
        int alpha = top >>> 24;
        if (alpha == 255) return top;
        if (alpha == 0) return bottom | 0xFF000000;
        int inv = 255 - alpha;
        int r = (((top >> 16) & 0xFF) * alpha + ((bottom >> 16) & 0xFF) * inv) / 255;
        int g = (((top >> 8) & 0xFF) * alpha + ((bottom >> 8) & 0xFF) * inv) / 255;
        int b = ((top & 0xFF) * alpha + (bottom & 0xFF) * inv) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Mean colour of the visible pixels, for the cape's edges and transparent areas. */
    private static int averageColor(NativeImage image) {
        long r = 0;
        long g = 0;
        long b = 0;
        long count = 0;
        int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 64);
        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                int argb = image.getPixel(x, y);
                if ((argb >>> 24) < 128) continue;
                r += (argb >> 16) & 0xFF;
                g += (argb >> 8) & 0xFF;
                b += argb & 0xFF;
                count++;
            }
        }
        if (count == 0) return 0xFF202020;
        return 0xFF000000 | (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
    }

    private static void fill(NativeImage image, int argb) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setPixel(x, y, argb);
        }
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    private static NativeImage readTexture(Identifier id) {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) return null;
            try (InputStream in = resource.get().open()) {
                return NativeImage.read(in);
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
