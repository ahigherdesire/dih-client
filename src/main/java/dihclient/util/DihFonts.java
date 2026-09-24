package dihclient.util;

import dihclient.mixin.accessor.DihFontAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * The client's UI typeface (Geist, {@code assets/dihclient/font/ui.json}).
 *
 * <p>Rather than restyling every string, the DIH UI draws with its own {@link Font} instance that
 * shares the vanilla glyph atlas and caches but resolves the <i>default</i> face to {@code dihclient:ui}.
 * Anything else (chat, vanilla screens, explicit fonts such as {@code minecraft:alt}) is untouched, and
 * glyphs Geist lacks fall back to the vanilla font. Turn it off with {@code modernFont = false}.
 */
public final class DihFonts {
    public static final Identifier UI_FONT = Identifier.fromNamespaceAndPath("dihclient", "ui");
    private static final FontDescription UI = new FontDescription.Resource(UI_FONT);

    private static volatile Font wrappedBase;
    private static volatile Font uiFont;

    private DihFonts() {
    }

    /** The UI font for the current client, or the vanilla font when the modern font is disabled. */
    public static Font ui() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : ui(mc.font);
    }

    /**
     * Maps the stock client font to the UI font. Any other font (already the UI font, a custom one,
     * or {@code null}) is returned as-is, so callers can pass whatever they were given.
     */
    public static Font ui(Font font) {
        if (font == null || !enabled()) return font;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || font != mc.font) return font;
        Font cached = uiFont;
        if (cached != null && wrappedBase == font) return cached;
        synchronized (DihFonts.class) {
            if (uiFont == null || wrappedBase != font) {
                Font.Provider base = ((DihFontAccessor) font).dih$provider();
                uiFont = new Font(new Font.Provider() {
                    @Override
                    public GlyphSource glyphs(FontDescription description) {
                        return base.glyphs(FontDescription.DEFAULT.equals(description) ? UI : description);
                    }

                    @Override
                    public EffectGlyph effect() {
                        return base.effect();
                    }
                });
                wrappedBase = font;
            }
            return uiFont;
        }
    }

    public static boolean enabled() {
        DihConfig config = DihConfig.getGlobal();
        return config == null || config.modernFont;
    }
}
