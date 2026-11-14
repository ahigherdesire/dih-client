package dihclient.modules;

import dihclient.api.module.*;
import dihclient.util.DihGifImage;
import dihclient.util.DihGifTextures;
import dihclient.util.DihUiScale;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

/**
 * Draws a user-supplied animated GIF (or still PNG/JPEG) as a movable HUD image, and can also use it
 * as your cape and as the front of your shield (client-side only: nobody else sees them).
 * Each use loads its file lazily on the render thread and caches it until the path changes.
 */
public final class GifModule extends Module {
    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;

    /** Set while the local player's own model or first-person hands are being submitted. */
    private static boolean renderingOwnItems;

    private final GifSlot hud = new GifSlot(null);
    private final GifSlot cape = new GifSlot(DihGifTextures::capeSheet);
    private final GifSlot shield = new GifSlot(DihGifTextures::shieldSheet);
    private Identifier capeFrame;
    private ClientAsset.Texture capeTexture;

    GifModule() {
        super("gif", "GIF", ModuleCategory.RENDER, "Shows an animated GIF or image on the HUD, as your cape or on your shield.");

        add(new StringSetting("image", "Image Path", "")
            .group("Image").description("GIF/PNG/JPEG path (absolute, or relative to the .minecraft folder).").build());
        add(new DoubleSetting("speed", "Speed", 1.0, 0.1, 5.0, 0.1)
            .group("Image").description("Animation speed multiplier.").build());
        add(new BoolSetting("paused", "Paused", false)
            .group("Image").description("Freezes the animation on the current frame.").build());
        add(new BoolSetting("play-once", "Play Once", false)
            .group("Image").description("Stops on the last frame instead of looping.").build());

        add(new BoolSetting("hud", "HUD", true)
            .group("HUD").description("Draw the image on the HUD.").build());
        add(new IntSetting("width", "Width", 64, 1, 1024, 1)
            .group("HUD").visibleWhen(() -> bool("hud")).description("On-screen width in pixels.").build());
        add(new BoolSetting("keep-aspect", "Keep Aspect", true)
            .group("HUD").visibleWhen(() -> bool("hud")).description("Scale height to match the image's aspect ratio.").build());
        add(new IntSetting("height", "Height", 64, 1, 1024, 1)
            .group("HUD").visibleWhen(() -> bool("hud") && !bool("keep-aspect")).description("On-screen height in pixels.").build());
        add(new IntSetting("opacity", "Opacity", 100, 0, 100, 1)
            .group("HUD").visibleWhen(() -> bool("hud")).description("Opacity percent.").build());
        add(new ColorSetting("tint", "Tint", 0xFFFFFFFF)
            .group("HUD").visibleWhen(() -> bool("hud")).description("Colour multiplied over the image; white keeps it as is.").build());
        add(new BoolSetting("hide-in-screens", "Hide In Menus", false)
            .group("HUD").visibleWhen(() -> bool("hud")).description("Hides the HUD image while a menu or inventory is open.").build());

        add(new ChoiceSetting("anchor", "Anchor", "Top Left",
            "Top Left", "Top Right", "Bottom Left", "Bottom Right", "Center")
            .group("Position").visibleWhen(() -> bool("hud")).description("Screen corner the offsets are measured from.").build());
        add(new IntSetting("x", "X Offset", 4, 0, 8000, 1)
            .group("Position").visibleWhen(() -> bool("hud")).description("Horizontal offset from the anchor.").build());
        add(new IntSetting("y", "Y Offset", 4, 0, 8000, 1)
            .group("Position").visibleWhen(() -> bool("hud")).description("Vertical offset from the anchor.").build());

        add(new BoolSetting("cape", "Cape", false)
            .group("Cosmetics").description("Wear the image as your cape. Only you see it; your skin settings must show capes.").build());
        add(new StringSetting("cape-image", "Cape Image", "")
            .group("Cosmetics").visibleWhen(() -> bool("cape"))
            .description("Separate cape file; blank uses Image Path. A 2:1 image is used as a full cape texture, anything else covers the back of the cape.").build());
        add(new BoolSetting("shield", "Shield", false)
            .group("Cosmetics").description("Paint the image on the front of shields you hold (first person and your own player model). Only you see it; banner patterns are hidden.").build());
        add(new StringSetting("shield-image", "Shield Image", "")
            .group("Cosmetics").visibleWhen(() -> bool("shield"))
            .description("Separate shield file; blank uses Image Path.").build());
    }

    public static GifModule get() {
        Module module = ModuleRegistry.get("gif");
        return module instanceof GifModule gif ? gif : null;
    }

    public static boolean shouldRender() {
        GifModule module = get();
        return module != null && module.isEnabled() && module.bool("hud") && !module.text("image").isBlank()
            && !(module.bool("hide-in-screens") && MC.gui.screen() != null);
    }

    public static void render(GuiGraphicsExtractor context) {
        GifModule module = get();
        if (module != null) module.renderInstance(context);
    }

    /** The cape texture to use for the player with this entity id, or null to keep the vanilla one. */
    public static ClientAsset.Texture capeTexture(int entityId) {
        GifModule module = get();
        if (module == null || !module.isEnabled() || !module.bool("cape")) return null;
        LocalPlayer player = MC.player;
        if (player == null || player.getId() != entityId) return null;
        Identifier frame = module.cape.frame(module.imagePath("cape-image"), module.speed(), module.bool("play-once"), module.bool("paused"));
        if (frame == null) return null;
        if (!frame.equals(module.capeFrame)) {
            module.capeFrame = frame;
            module.capeTexture = new ClientAsset.ResourceTexture(frame, frame);
        }
        return module.capeTexture;
    }

    /** The shield texture to draw for the shield being submitted now, or null to keep the vanilla one. */
    public static Identifier shieldFrame() {
        if (!renderingOwnItems) return null;
        GifModule module = get();
        if (module == null || !module.isEnabled() || !module.bool("shield")) return null;
        return module.shield.frame(module.imagePath("shield-image"), module.speed(), module.bool("play-once"), module.bool("paused"));
    }

    /** Marks whether the items being submitted belong to the local player (third-person model or first-person hands). */
    public static void setRenderingOwnItems(boolean own) {
        renderingOwnItems = own;
    }

    public static boolean isLocalPlayer(int entityId) {
        LocalPlayer player = MC.player;
        return player != null && player.getId() == entityId;
    }

    @Override
    public void onEnable() {
        hud.reset();
        cape.reset();
        shield.reset();
    }

    @Override
    public void onDisable() {
        hud.release();
        cape.release();
        shield.release();
    }

    @Override
    public void tick() {
        if (!bool("hud")) hud.release();
        if (!bool("cape")) cape.release();
        if (!bool("shield")) shield.release();
    }

    @Override
    protected void onOptionValueChanged(String settingId) {
        switch (settingId) {
            case "image" -> {
                hud.reset();
                cape.reset();
                shield.reset();
            }
            case "cape-image" -> cape.reset();
            case "shield-image" -> shield.reset();

            default -> {
            }
        }
    }

    private String imagePath(String overrideSetting) {
        String override = text(overrideSetting).trim();
        return override.isEmpty() ? text("image").trim() : override;
    }

    private double speed() {
        return Math.max(0.1, decimal("speed"));
    }

    private void renderInstance(GuiGraphicsExtractor context) {
        if (!isEnabled() || !bool("hud")) {
            hud.release();
            return;
        }
        Identifier frame = hud.frame(text("image").trim(), speed(), bool("play-once"), bool("paused"));
        DihGifImage image = hud.image;
        if (frame == null || image == null) return;

        int displayWidth = Math.max(1, integer("width"));
        int displayHeight = bool("keep-aspect")
            ? Math.max(1, Math.round(displayWidth * (image.height() / (float) image.width())))
            : Math.max(1, integer("height"));

        int[] pos = anchorPosition(displayWidth, displayHeight);
        float alpha = Math.max(0, Math.min(100, integer("opacity"))) / 100.0f;
        if (alpha <= 0.0f) return;

        int tint = ModuleRenderUtil.color(this, "tint", 0xFFFFFFFF);
        context.blit(RenderPipelines.GUI_TEXTURED, frame, pos[0], pos[1], 0.0F, 0.0F,
            displayWidth, displayHeight, image.width(), image.height(), image.width(), image.height(),
            ARGB.multiply(ARGB.white(alpha), tint | 0xFF000000));
    }

    private int[] anchorPosition(int width, int height) {
        int screenWidth = DihUiScale.getVirtualScreenWidth();
        int screenHeight = DihUiScale.getVirtualScreenHeight();
        int offsetX = integer("x");
        int offsetY = integer("y");
        return switch (choice("anchor")) {
            case "Top Right" -> new int[]{screenWidth - width - offsetX, offsetY};
            case "Bottom Left" -> new int[]{offsetX, screenHeight - height - offsetY};
            case "Bottom Right" -> new int[]{screenWidth - width - offsetX, screenHeight - height - offsetY};
            case "Center" -> new int[]{(screenWidth - width) / 2 + offsetX, (screenHeight - height) / 2 + offsetY};
            default -> new int[]{offsetX, offsetY};
        };
    }

    /** One loaded image for one use (HUD, cape or shield), with the frame layout that use needs. */
    private static final class GifSlot {
        private final UnaryOperator<NativeImage> frameTransform;
        private DihGifImage image;
        private String loadedPath;
        private long loadStartMs;
        /** Playback position held while paused, or -1 while playing. */
        private long pausedElapsed = -1L;

        private GifSlot(UnaryOperator<NativeImage> frameTransform) {
            this.frameTransform = frameTransform;
        }

        /** The frame to draw now, loading {@code path} first if it changed. Render thread only. */
        private Identifier frame(String path, double speed, boolean playOnce, boolean paused) {
            if (path.isEmpty()) {
                release();
                return null;
            }
            // A failed load keeps loadedPath, so a missing file is not re-read every frame.
            if (!path.equals(loadedPath)) load(path);
            if (image == null) return null;
            setPaused(paused, speed);
            long elapsed = pausedElapsed >= 0L ? pausedElapsed : elapsed(speed);
            if (playOnce && image.durationMs() > 0) elapsed = Math.min(elapsed, image.durationMs() - 1L);
            return image.frameId(elapsed);
        }

        private long elapsed(double speed) {
            return (long) ((System.currentTimeMillis() - loadStartMs) * speed);
        }

        /** Holds the current frame, or resumes from it. */
        private void setPaused(boolean paused, double speed) {
            if (paused) {
                if (pausedElapsed < 0L) pausedElapsed = elapsed(speed);
            } else if (pausedElapsed >= 0L) {
                loadStartMs = System.currentTimeMillis() - (long) (pausedElapsed / speed);
                pausedElapsed = -1L;
            }
        }

        private void reset() {
            loadedPath = null;
        }

        private void load(String path) {
            release();
            loadedPath = path;
            loadStartMs = System.currentTimeMillis();
            try {
                Path file = Path.of(path);
                if (!file.isAbsolute()) file = MC.gameDirectory.toPath().resolve(path);
                if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) return;
                image = DihGifImage.load(Files.readAllBytes(file), frameTransform);
            } catch (Throwable ignored) {
                image = null;
            }
        }

        private void release() {
            DihGifImage current = image;
            if (current == null) return;
            image = null;
            loadedPath = null;
            if (MC.isSameThread()) current.release();
            else MC.execute(current::release);
        }
    }
}
