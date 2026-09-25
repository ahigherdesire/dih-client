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
import baritone.api.IBaritone;
import baritone.api.event.events.RenderEvent;
import baritone.utils.BaritoneRenderBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires Baritone's {@link RenderEvent} once per frame and draws what it recorded.
 *
 * <p>MC 26.2 has no {@code LevelRenderer.renderLevel}; the old target silently failed to apply
 * (defaultRequire 0), so no Baritone overlay ever ran. This targets {@code render(...)} RETURN —
 * the same point and descriptor the client's in-game-proven tracer/waypoint overlays use — with
 * the frame's view matrix as the base pose, exactly like upstream's model-view stack.
 *
 * @author Brady
 * @since 2/13/2020
 */
@Mixin(LevelRenderer.class)
public class MixinWorldRenderer {

    @Inject(
            method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            at = @At("RETURN")
    )
    private void baritone$renderPass(final GraphicsResourceAllocator allocator, final DeltaTracker deltaTracker,
                                     final boolean renderBlockOutline, final CameraRenderState cameraState,
                                     final Matrix4fc positionMatrix, final GpuBufferSlice fog, final Vector4f fogColor,
                                     final boolean renderSky, final CallbackInfo ci) {
        if (cameraState == null || cameraState.pos == null) return;
        BaritoneRenderBuffer.begin();
        try {
            float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
            for (IBaritone ibaritone : BaritoneAPI.getProvider().getAllBaritones()) {
                PoseStack poseStack = new PoseStack();
                poseStack.mulPose(positionMatrix);
                ibaritone.getGameEventHandler().onRenderPass(new RenderEvent(partialTicks, poseStack, cameraState.projectionMatrix));
            }
        } finally {
            BaritoneRenderBuffer.flush();
        }
    }
}
