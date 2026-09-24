package dihclient.gui.vanillaui.direct;

import dihclient.gui.vanillaui.components.*;

import dihclient.gui.vanillaui.direct.DirectUiNode;
import dihclient.gui.vanillaui.direct.DirectRenderContext;

public class DirectViewportSlot extends DirectUiNode {
    private float preferredHeight = 64.0f;

    public DirectViewportSlot setPreferredHeight(float preferredHeight) {
        float next = Math.max(0.0f, preferredHeight);
        if (Float.compare(this.preferredHeight, next) != 0) {
            this.preferredHeight = next;
            markLayoutDirty();
        }
        return this;
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        return context.theme().scale(preferredHeight);
    }
}
