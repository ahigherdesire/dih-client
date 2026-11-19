package dihclient.util;

//? if >=26.2 {
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;

import java.util.function.Consumer;

public final class DihEspMeshBuffer implements AutoCloseable {

    private static final int SCRATCH_BYTES = 1 << 20;

    private final String label;
    private GpuBuffer vertexBuffer;
    private PrimitiveTopology topology;
    private int indexCount;

    public DihEspMeshBuffer(String label) {
        this.label = label;
    }

    public boolean hasMesh() {
        return vertexBuffer != null && !vertexBuffer.isClosed() && indexCount > 0;
    }

    public boolean bake(RenderType type, Consumer<VertexConsumer> emit) {
        drop();
        try (ByteBufferBuilder scratch = new ByteBufferBuilder(SCRATCH_BYTES)) {
            BufferBuilder builder = new BufferBuilder(scratch, type.primitiveTopology(), type.format());
            emit.accept(builder);
            MeshData mesh = builder.build();
            if (mesh == null) return false;
            try (mesh) {
                MeshData.DrawState state = mesh.drawState();
                if (state.indexCount() <= 0) return false;
                topology = state.primitiveTopology();
                indexCount = state.indexCount();
                vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
            return hasMesh();
        }
    }

    public void draw(RenderType type, Matrix4fc framePose, double offsetX, double offsetY, double offsetZ) {
        if (!hasMesh()) return;
        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(topology);
        GpuBuffer indices = sequential.getBuffer(indexCount);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            if (framePose != null) modelView.mul(framePose);
            modelView.translate((float) offsetX, (float) offsetY, (float) offsetZ);

            DihRender.drawFromBuffer(type, vertexBuffer, indices, sequential.type(), indexCount, topology);
        } finally {
            modelView.popMatrix();
        }
    }

    public void drop() {
        GpuBuffer buffer = vertexBuffer;
        vertexBuffer = null;
        indexCount = 0;
        topology = null;
        if (buffer == null || buffer.isClosed()) return;
        if (RenderSystem.isOnRenderThread()) {
            buffer.close();
        } else {

            RenderSystem.queueFencedTask(buffer::close);
        }
    }

    @Override
    public void close() {
        drop();
    }
}
//?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

// 26.1: RenderType can only draw a MeshData (no drawing from a GPU buffer DIH owns), so the baked vertices are
// kept in native memory and replayed into a fresh MeshData each frame.
public final class DihEspMeshBuffer implements AutoCloseable {

    private static final int SCRATCH_BYTES = 1 << 20;

    private final String label;
    private ByteBuffer vertices;
    private MeshData.DrawState drawState;

    public DihEspMeshBuffer(String label) {
        this.label = label;
    }

    public boolean hasMesh() {
        return vertices != null && drawState != null && drawState.indexCount() > 0;
    }

    public boolean bake(RenderType type, Consumer<VertexConsumer> emit) {
        drop();
        try (ByteBufferBuilder scratch = new ByteBufferBuilder(SCRATCH_BYTES)) {
            BufferBuilder builder = new BufferBuilder(scratch, type.mode(), type.format());
            emit.accept(builder);
            MeshData mesh = builder.build();
            if (mesh == null) return false;
            try (mesh) {
                MeshData.DrawState state = mesh.drawState();
                if (state.indexCount() <= 0) return false;
                ByteBuffer source = mesh.vertexBuffer();
                ByteBuffer copy = MemoryUtil.memAlloc(source.remaining());
                MemoryUtil.memCopy(MemoryUtil.memAddress(source), MemoryUtil.memAddress(copy), source.remaining());
                vertices = copy;
                drawState = state;
            }
            return hasMesh();
        }
    }

    public void draw(RenderType type, Matrix4fc framePose, double offsetX, double offsetY, double offsetZ) {
        if (!hasMesh()) return;
        int size = vertices.remaining();
        ByteBufferBuilder scratch = new ByteBufferBuilder(size);
        long target = scratch.reserve(size);
        MemoryUtil.memCopy(MemoryUtil.memAddress(vertices), target, size);
        ByteBufferBuilder.Result result = scratch.build();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            if (framePose != null) modelView.mul(framePose);
            modelView.translate((float) offsetX, (float) offsetY, (float) offsetZ);
            if (result != null) type.draw(new MeshData(result, drawState));
        } finally {
            modelView.popMatrix();
            scratch.close();
        }
    }

    public void drop() {
        ByteBuffer buffer = vertices;
        vertices = null;
        drawState = null;
        if (buffer != null) MemoryUtil.memFree(buffer);
    }

    @Override
    public void close() {
        drop();
    }
}
*///?}
