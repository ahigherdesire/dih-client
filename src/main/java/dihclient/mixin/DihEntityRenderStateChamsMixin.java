package dihclient.mixin;

import dihclient.util.DihChamsHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class DihEntityRenderStateChamsMixin implements DihChamsHolder {
    @Unique private boolean dih$active;
    @Unique private int dih$visible;
    @Unique private int dih$occluded;

    @Override
    public void dih$setChams(boolean active, int visibleColor, int occludedColor) {
        this.dih$active = active;
        this.dih$visible = visibleColor;
        this.dih$occluded = occludedColor;
    }

    @Override
    public boolean dih$chamsActive() {
        return dih$active;
    }

    @Override
    public int dih$chamsVisible() {
        return dih$visible;
    }

    @Override
    public int dih$chamsOccluded() {
        return dih$occluded;
    }
}
