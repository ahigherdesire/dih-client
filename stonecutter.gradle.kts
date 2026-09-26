plugins {
    id("dev.kikugie.stonecutter")
}

// The version the sources are switched to in the IDE ("Set active project" in the Stonecutter tasks).
stonecutter active "26.2-fabric"

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Per-version and per-loader values from stonecutter.properties.toml.
    properties {
        tags(version, loader)
    }

    // `//? if fabric {`, `//? if neoforge {`, `//? if forge {` in the sources.
    constants {
        match(loader, "fabric", "neoforge", "forge")
    }

    // Minecraft 26.3 renames, applied as text to the 26.3 builds (and undone when switching the IDE back to 26.2).
    // Keep every entry qualified enough to be unique: a bare word would also rewrite unrelated text on the way back.
    replacements {
        string(current.parsed >= "26.3") {
            // The render API moved from com.mojang.blaze3d to com.mojang.renderpearl.api.
            replace("com.mojang.blaze3d.GpuFormat", "com.mojang.renderpearl.api.GpuFormat")
            replace("com.mojang.blaze3d.PrimitiveTopology", "com.mojang.renderpearl.api.pipeline.PrimitiveTopology")
            replace("com.mojang.blaze3d.buffers.GpuBuffer", "com.mojang.renderpearl.api.buffers.GpuBuffer")
            replace("com.mojang.blaze3d.buffers.GpuBufferSlice", "com.mojang.renderpearl.api.buffers.GpuBufferSlice")
            replace("com.mojang.blaze3d.pipeline.BindGroupLayout", "com.mojang.renderpearl.api.pipeline.BindGroupLayout")
            replace("com.mojang.blaze3d.pipeline.BlendFunction", "com.mojang.renderpearl.api.pipeline.BlendFunction")
            replace("com.mojang.blaze3d.pipeline.ColorTargetState", "com.mojang.renderpearl.api.pipeline.ColorTargetState")
            replace("com.mojang.blaze3d.pipeline.DepthStencilState", "com.mojang.renderpearl.api.pipeline.DepthStencilState")
            replace("com.mojang.blaze3d.pipeline.RenderPipeline", "com.mojang.renderpearl.api.pipeline.RenderPipeline")
            replace("com.mojang.blaze3d.platform.CompareOp", "com.mojang.renderpearl.api.pipeline.CompareOp")
            replace("com.mojang.blaze3d.platform.PolygonMode", "com.mojang.renderpearl.api.pipeline.PolygonMode")
            replace("com.mojang.blaze3d.textures.AddressMode", "com.mojang.renderpearl.api.textures.AddressMode")
            replace("com.mojang.blaze3d.textures.FilterMode", "com.mojang.renderpearl.api.textures.FilterMode")
            replace("com.mojang.blaze3d.textures.GpuTexture", "com.mojang.renderpearl.api.textures.GpuTexture")
            replace("com.mojang.blaze3d.vertex.VertexFormat", "com.mojang.renderpearl.api.vertex.VertexFormat")
            // Renamed classes (bare names, so uses like `instanceof EnderMan` follow; neither new spelling occurs in src).
            replace("EnderMan", "Enderman")
            replace("RedStoneWireBlock", "RedstoneWireBlock")
            replace("RegistryDataLoader.WORLDGEN_REGISTRIES", "RegistryDataLoader.WORLD_REGISTRIES")
            replace("RegistryLayer.WORLDGEN", "RegistryLayer.WORLD")
        }
    }
}
