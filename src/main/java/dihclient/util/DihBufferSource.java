package dihclient.util;

//? if >=26.2 {
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class DihBufferSource implements AutoCloseable {
    private final StagedVertexBuffer stagedBuffer;
    private final List<StagedVertexBuffer.Draw> draws = new ArrayList<>();
    private final List<RenderType> drawTypes = new ArrayList<>();

    public DihBufferSource() {
        this(RenderType.BIG_BUFFER_SIZE);
    }

    public DihBufferSource(int bufferSize) {
        stagedBuffer = new StagedVertexBuffer(() -> "DihBufferSource", bufferSize);
    }

    public VertexConsumer getBuffer(RenderType renderType) {
        if (!drawTypes.isEmpty() && drawTypes.getLast() == renderType
            && renderType.canConsolidateConsecutiveGeometry()) {
            return stagedBuffer.getVertexBuilder(draws.getLast());
        }

        StagedVertexBuffer.Draw draw = stagedBuffer.appendDraw(renderType.format(),
            renderType.primitiveTopology(), renderType.sortOnUpload()
                ? RenderSystem.getProjectionType().vertexSorting() : null);

        draws.add(draw);
        drawTypes.add(renderType);
        return stagedBuffer.getVertexBuilder(draw);
    }

    public void uploadAndDraw() {
        try {
            if (draws.isEmpty()) return;

            stagedBuffer.upload();

            for (int i = 0; i < draws.size(); i++) {
                draw(drawTypes.get(i), draws.get(i));
            }
        } finally {
            draws.clear();
            drawTypes.clear();

            stagedBuffer.endFrame();
        }
    }

    @Override
    public void close() {
        stagedBuffer.close();
    }

    public static final class Holder {
        private final int bufferSize;
        private DihBufferSource source;

        public Holder(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        public DihBufferSource get() {
            DihBufferSource current = source;
            if (current == null) {
                current = new DihBufferSource(bufferSize);
                source = current;
            }
            return current;
        }
    }

    private void draw(RenderType type, StagedVertexBuffer.Draw draw) {
        StagedVertexBuffer.ExecuteInfo info = stagedBuffer.getExecuteInfo(draw);
        if (info != null) {
            DihRender.drawFromBuffer(type, info);
        }
    }
}
//?} else {
/*import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

// 26.1: no StagedVertexBuffer yet, so this batches through vanilla's immediate buffer source.
public final class DihBufferSource implements AutoCloseable {
    private final ByteBufferBuilder byteBuffer;
    private final MultiBufferSource.BufferSource source;

    public DihBufferSource() {
        this(RenderType.BIG_BUFFER_SIZE);
    }

    public DihBufferSource(int bufferSize) {
        byteBuffer = new ByteBufferBuilder(bufferSize);
        source = MultiBufferSource.immediate(byteBuffer);
    }

    public VertexConsumer getBuffer(RenderType renderType) {
        return source.getBuffer(renderType);
    }

    public void uploadAndDraw() {
        source.endBatch();
    }

    @Override
    public void close() {
        byteBuffer.close();
    }

    public static final class Holder {
        private final int bufferSize;
        private DihBufferSource source;

        public Holder(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        public DihBufferSource get() {
            DihBufferSource current = source;
            if (current == null) {
                current = new DihBufferSource(bufferSize);
                source = current;
            }
            return current;
        }
    }
}
*///?}
