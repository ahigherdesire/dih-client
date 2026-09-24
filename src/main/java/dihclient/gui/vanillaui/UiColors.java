package dihclient.gui.vanillaui;

import dihclient.util.DihTheme;
import dihclient.util.DihTheme.Channel;

public final class UiColors {
    public int screenScrim;
    public int window;
    public int windowStrong;
    public int header;
    public int headerHover;
    public int row;
    public int rowAlt;
    public int rowHover;
    public int field;
    public int fieldFocused;
    public int border;
    public int borderSoft;
    public int buttonBorder;
    public int hairline;
    public int hoverVeil;
    public int accent;
    public int accentDark;
    public int accentSoft;
    public int success;
    public int successSoft;
    public int text;
    public int muted;
    public int disabled;
    public int bad;

    public UiColors() {
        recompute();
    }

    public void recompute() {
        screenScrim = 0x66000000;
        window = 0xD80B0C10;
        windowStrong = 0xEE0A0A0D;
        header = 0xF0181A20;
        headerHover = 0xFF242832;

        row = 0xB012141A;
        rowAlt = 0x94161A21;
        rowHover = 0xC91D222B;
        field = 0xD9121419;
        fieldFocused = 0xE81A1E26;
        border = DihTheme.recolor(0xFFCE4949, Channel.OUTLINE);
        borderSoft = DihTheme.recolor(0xCC9F3A3A, Channel.OUTLINE);

        buttonBorder = DihTheme.recolor(0xE0A85E5E, Channel.OUTLINE);
        hairline = 0x2BFFFFFF;
        hoverVeil = 0x10FFFFFF;
        accent = DihTheme.recolor(0xFFFF3B3B, Channel.ACCENT);
        accentDark = DihTheme.recolor(0xFF8F1F24, Channel.ACCENT);
        accentSoft = DihTheme.recolor(0x44FF3B3B, Channel.ACCENT);
        success = DihTheme.recolor(0xFF3FE87E, Channel.SUCCESS);
        successSoft = DihTheme.recolor(0x663FE87E, Channel.SUCCESS);
        text = DihTheme.recolor(0xFFF3ECE7, Channel.TEXT);
        muted = DihTheme.recolor(0xFFB79E9E, Channel.TEXT);
        disabled = DihTheme.recolor(0xFF766A6A, Channel.TEXT);
        bad = DihTheme.recolor(0xFFFF5555, Channel.DANGER);
    }
}
