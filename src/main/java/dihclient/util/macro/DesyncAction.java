package dihclient.util.macro;

import dihclient.util.DihGuiActions;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class DesyncAction implements MacroAction {
    private boolean enabled = true;

    public DesyncAction() {}

    @Override
    public void execute(Minecraft mc) {
        DihGuiActions.desyncCurrentScreen(mc, false);
    }

    @Override
    public MacroActionType getType() { return MacroActionType.DESYNC; }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "DESYNC");
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        if (tag.contains("enabled")) this.enabled = tag.getBooleanOr("enabled", true);
    }

    @Override public String getDisplayName() { return "Desync"; }
    @Override public String getIcon()        { return "~"; }
    @Override public boolean isEnabled()     { return enabled; }
    @Override public void setEnabled(boolean e) { this.enabled = e; }
}
