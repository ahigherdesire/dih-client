package dihclient.ducks;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface DihExternalButtonScreen {
    void dih$renderExternalButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks);
}
