package dihclient.util;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * A decoded still or animated image (GIF / PNG / JPEG) bound to GPU textures, one per frame.
 * {@link #load(byte[])} must run on the render thread because it uploads textures;
 * {@link #frameId(long)} returns the frame to draw for a given elapsed time.
 */
public final class DihGifImage {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final Identifier[] frameIds;
    private final int[] frameDelaysMs;
    private final int totalDurationMs;
    private final int width;
    private final int height;
    private boolean released;

    private DihGifImage(Identifier[] frameIds, int[] frameDelaysMs, int width, int height) {
        this.frameIds = frameIds;
        this.frameDelaysMs = frameDelaysMs;
        this.width = width;
        this.height = height;
        int total = 0;
        for (int delay : frameDelaysMs) total += delay;
        this.totalDurationMs = total;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int frameCount() {
        return frameIds.length;
    }

    /** One full playback, in milliseconds; 0 for a still image. */
    public int durationMs() {
        return totalDurationMs;
    }

    public boolean animated() {
        return frameIds.length > 1 && totalDurationMs > 0;
    }

    /** The texture for the frame shown at {@code elapsedMs} into a looping playback. */
    public Identifier frameId(long elapsedMs) {
        if (!animated()) return frameIds[0];
        long t = Math.floorMod(elapsedMs, totalDurationMs);
        long accumulated = 0;
        for (int i = 0; i < frameIds.length; i++) {
            accumulated += frameDelaysMs[i];
            if (t < accumulated) return frameIds[i];
        }
        return frameIds[frameIds.length - 1];
    }

    public void release() {
        if (released) return;
        released = true;
        for (Identifier id : frameIds) {
            try {
                MC.getTextureManager().release(id);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Decodes the bytes and uploads the frames. Call on the render thread. Returns null on failure. */
    public static DihGifImage load(byte[] data) {
        return load(data, null);
    }

    /**
     * Like {@link #load(byte[])}, but passes every decoded frame through {@code frameTransform} before upload
     * (e.g. to lay it out as a cape or shield texture). The transform may return its input or a new image;
     * a replaced input is closed here.
     */
    public static DihGifImage load(byte[] data, UnaryOperator<NativeImage> frameTransform) {
        if (data == null || data.length == 0) return null;
        try {
            List<NativeImage> frames = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            if (isGif(data)) {
                decodeGif(data, frames, delays);
            }
            if (frames.isEmpty()) {
                NativeImage still = DihImageCodec.decode(data);
                if (still == null) return null;
                frames.clear();
                delays.clear();
                frames.add(still);
                delays.add(0);
            }
            if (frameTransform != null) {
                for (int i = 0; i < frames.size(); i++) {
                    NativeImage source = frames.get(i);
                    NativeImage transformed = frameTransform.apply(source);
                    if (transformed != source) source.close();
                    frames.set(i, transformed);
                }
            }

            int width = frames.get(0).getWidth();
            int height = frames.get(0).getHeight();
            int sequence = SEQUENCE.getAndIncrement();
            Identifier[] ids = new Identifier[frames.size()];
            int[] frameDelays = new int[frames.size()];
            for (int i = 0; i < frames.size(); i++) {
                Identifier id = Identifier.fromNamespaceAndPath("dihclient", "dynamic/hud/gif/" + sequence + "/" + i);
                MC.getTextureManager().register(id, new FrameTexture("DIH GIF " + sequence + ":" + i, frames.get(i)));
                ids[i] = id;
                frameDelays[i] = Math.max(0, delays.get(i));
            }
            return new DihGifImage(ids, frameDelays, width, height);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isGif(byte[] data) {
        return data.length > 3 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8';
    }

    private static void decodeGif(byte[] data, List<NativeImage> outFrames, List<Integer> outDelays) throws Exception {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                int count = reader.getNumImages(true);
                if (count <= 0) return;

                int[] screen = logicalScreenSize(reader);
                BufferedImage canvas = new BufferedImage(screen[0], screen[1], BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = canvas.createGraphics();
                BufferedImage previous = null;
                for (int i = 0; i < count; i++) {
                    BufferedImage frame = reader.read(i);
                    GifFrameMeta meta = frameMeta(reader.getImageMetadata(i));
                    if ("restoreToPrevious".equals(meta.disposal)) previous = copy(canvas);

                    graphics.setComposite(AlphaComposite.SrcOver);
                    graphics.drawImage(frame, meta.x, meta.y, null);
                    outFrames.add(toNativeImage(canvas));
                    outDelays.add(Math.max(20, meta.delayMs));

                    if ("restoreToBackgroundColor".equals(meta.disposal)) {
                        graphics.setComposite(AlphaComposite.Clear);
                        graphics.fillRect(meta.x, meta.y, frame.getWidth(), frame.getHeight());
                    } else if ("restoreToPrevious".equals(meta.disposal) && previous != null) {
                        graphics.setComposite(AlphaComposite.Src);
                        graphics.drawImage(previous, 0, 0, null);
                    }
                }
                graphics.dispose();
            } finally {
                reader.dispose();
            }
        }
    }

    private static int[] logicalScreenSize(ImageReader reader) {
        try {
            IIOMetadata stream = reader.getStreamMetadata();
            if (stream != null) {
                IIOMetadataNode root = (IIOMetadataNode) stream.getAsTree("javax_imageio_gif_stream_1.0");
                IIOMetadataNode descriptor = child(root, "LogicalScreenDescriptor");
                if (descriptor != null) {
                    int w = intAttr(descriptor, "logicalScreenWidth", 0);
                    int h = intAttr(descriptor, "logicalScreenHeight", 0);
                    if (w > 0 && h > 0) return new int[]{w, h};
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            return new int[]{Math.max(1, reader.getWidth(0)), Math.max(1, reader.getHeight(0))};
        } catch (Throwable t) {
            return new int[]{1, 1};
        }
    }

    private static GifFrameMeta frameMeta(IIOMetadata meta) {
        GifFrameMeta out = new GifFrameMeta();
        try {
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");
            IIOMetadataNode descriptor = child(root, "ImageDescriptor");
            if (descriptor != null) {
                out.x = intAttr(descriptor, "imageLeftPosition", 0);
                out.y = intAttr(descriptor, "imageTopPosition", 0);
            }
            IIOMetadataNode control = child(root, "GraphicControlExtension");
            if (control != null) {
                out.delayMs = intAttr(control, "delayTime", 10) * 10;
                String disposal = control.getAttribute("disposalMethod");
                out.disposal = disposal == null || disposal.isEmpty() ? "none" : disposal;
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
        if (parent == null) return null;
        for (int i = 0; i < parent.getLength(); i++) {
            if (parent.item(i) instanceof IIOMetadataNode node && node.getNodeName().equalsIgnoreCase(name)) {
                return node;
            }
        }
        return null;
    }

    private static int intAttr(IIOMetadataNode node, String name, int fallback) {
        try {
            String value = node.getAttribute(name);
            return value == null || value.isEmpty() ? fallback : Integer.parseInt(value.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = out.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return out;
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        NativeImage out = new NativeImage(image.getWidth(), image.getHeight(), true);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                out.setPixel(x, y, image.getRGB(x, y));
            }
        }
        return out;
    }

    private static final class GifFrameMeta {
        private int x;
        private int y;
        private int delayMs = 100;
        private String disposal = "none";
    }

    private static final class FrameTexture extends AbstractTexture {
        private final NativeImage pixels;

        private FrameTexture(String label, NativeImage pixels) {
            this.pixels = pixels;
            this.texture = RenderSystem.getDevice().createTexture(label, 5, GpuFormat.RGBA8_UNORM,
                pixels.getWidth(), pixels.getHeight(), 1, 1);
            this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
            this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, pixels);
        }

        @Override
        public void close() {
            this.pixels.close();
            super.close();
        }
    }
}
