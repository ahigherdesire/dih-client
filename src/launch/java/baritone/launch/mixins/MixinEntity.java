/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.launch.mixins;

import baritone.api.BaritoneAPI;
import baritone.api.event.events.RotationMoveEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class MixinEntity {

    @Shadow
    private float yRot;

    @Shadow
    private float xRot;

    @Unique
    private RotationMoveEvent motionUpdateRotationEvent;

    @Inject(
            method = "moveRelative",
            at = @At("HEAD")
    )
    private void moveRelativeHead(CallbackInfo info) {
        // noinspection ConstantConditions
        if (!LocalPlayer.class.isInstance(this) || BaritoneAPI.getProvider().getBaritoneForPlayer((LocalPlayer) (Object) this) == null) {
            return;
        }
        this.motionUpdateRotationEvent = new RotationMoveEvent(RotationMoveEvent.Type.MOTION_UPDATE, this.yRot, this.xRot);
        BaritoneAPI.getProvider().getBaritoneForPlayer((LocalPlayer) (Object) this).getGameEventHandler().onPlayerRotationMove(motionUpdateRotationEvent);
        this.yRot = this.motionUpdateRotationEvent.getYaw();
        this.xRot = this.motionUpdateRotationEvent.getPitch();
    }

    @Inject(
            method = "moveRelative",
            at = @At("RETURN")
    )
    private void moveRelativeReturn(CallbackInfo info) {
        if (this.motionUpdateRotationEvent != null) {
            this.yRot = this.motionUpdateRotationEvent.getOriginal().getYaw();
            this.xRot = this.motionUpdateRotationEvent.getOriginal().getPitch();
            this.motionUpdateRotationEvent = null;
        }
    }

    // ── Player ESP: force the vanilla glowing outline on other players ──────────
    //    espPlayers draws no box; instead we make Minecraft think the target is
    //    glowing, so the game renders its real silhouette through walls (the same
    //    effect as a spectral arrow), tinted with colorEspPlayer.

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void baritone$espGlow(CallbackInfoReturnable<Boolean> cir) {
        if (baritone$espTarget()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void baritone$espColor(CallbackInfoReturnable<Integer> cir) {
        if (baritone$espTarget()) {
            cir.setReturnValue(BaritoneAPI.getSettings().colorEspPlayer.value.getRGB() & 0xFFFFFF);
        }
    }

    /** True when this entity is another player that player-ESP should outline. */
    @Unique
    private boolean baritone$espTarget() {
        try {
            if (!BaritoneAPI.getSettings().espPlayers.value) {
                return false;
            }
            if (!(((Object) this) instanceof Player)) {
                return false;
            }
            LocalPlayer me = Minecraft.getInstance().player;
            if (me == null || ((Object) this) == me) {
                return false; // never outline ourselves
            }
            Entity self = (Entity) (Object) this;
            double range = BaritoneAPI.getSettings().espPlayerRange.value;
            return self.distanceToSqr(me) <= range * range;
        } catch (Throwable t) {
            return false;
        }
    }
}
