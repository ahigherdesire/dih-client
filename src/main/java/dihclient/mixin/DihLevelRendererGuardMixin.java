package dihclient.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class DihLevelRendererGuardMixin {
    //? if >=26.2 {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    *///?}
    //? if >=26.3 {
    /*private void dih$skipRenderWithoutPlayer(GraphicsResourceAllocator graphicsResourceAllocator, boolean renderBlockOutline, CameraRenderState cameraRenderState, GpuBufferSlice fog, Vector4f clearColor, boolean renderSky, boolean renderWeather, CallbackInfo ci) {
    *///?} else if >=26.2 {
    private void dih$skipRenderWithoutPlayer(GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean renderBlockOutline, CameraRenderState cameraRenderState, Matrix4fc frustumMatrix, GpuBufferSlice fog, Vector4f clearColor, boolean renderSky, CallbackInfo ci) {
    //?} else if >=26.1 {
    /*private void dih$skipRenderWithoutPlayer(GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean renderBlockOutline, CameraRenderState cameraRenderState, Matrix4fc frustumMatrix, GpuBufferSlice fog, Vector4f clearColor, boolean renderSky, net.minecraft.client.renderer.chunk.ChunkSectionsToRender sections, CallbackInfo ci) {
    *///?} else {
    /*// 1.21.11: renderLevel takes the camera and its matrices directly.
    private void dih$skipRenderWithoutPlayer(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline,
        net.minecraft.client.Camera camera, org.joml.Matrix4f positionMatrix, org.joml.Matrix4f projectionMatrix,
        org.joml.Matrix4f cullingProjectionMatrix, GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
    *///?}
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            ci.cancel();
            return;
        }

        LevelRenderer renderer = (LevelRenderer) (Object) this;
        //? if >=26.2 {
        if (renderer.viewArea() == null || renderer.sectionRenderDispatcher() == null) {
        //?} else {
        /*if (renderer.getSectionRenderDispatcher() == null) {
        *///?}
            dihclient.modules.ModuleRenderUtil.refreshWorldRenderer();
            ci.cancel();
        }
    }
}
