#version 330
#extension GL_ARB_separate_shader_objects : require

// fogless_lines for Minecraft 26.3+ (SPIR-V shader compiler); see fogless_lines_263.vsh.

#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vertexColor * ColorModulator;
}
