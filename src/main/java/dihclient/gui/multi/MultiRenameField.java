package dihclient.gui.multi;

import com.mojang.blaze3d.platform.InputConstants;

public final class MultiRenameField {
    private static final int MAX = 50;
    private final StringBuilder text = new StringBuilder();
    private boolean focused;

    public boolean focused() {
        return focused;
    }

    public void focus() {
        focused = true;
    }

    public void blur() {
        focused = false;
    }

    public String text() {
        return text.toString();
    }

    public void set(String value) {
        text.setLength(0);
        if (value != null) text.append(value.length() > MAX ? value.substring(0, MAX) : value);
    }

    public boolean charTyped(char c) {
        if (!focused) return false;
        if (c >= ' ' && c != 127 && text.length() < MAX) {
            text.append(c);
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (!focused) return false;
        switch (keyCode) {
            case InputConstants.KEY_BACKSPACE -> {
                if (text.length() > 0) text.deleteCharAt(text.length() - 1);
                return true;
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_ESCAPE -> {
                focused = false;
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
