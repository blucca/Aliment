#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ColdShiverConfig {
    float Intensity;
    float EdgeStart;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// The hypothermia shiver: the same edge-only displacement as the fever shimmer, but slower and
// mostly vertical, which reads as the frame trembling rather than as heat rising. As with the
// fever version, the centre of the screen stays perfectly sharp.
void main() {
    vec2 centred = texCoord * 2.0 - 1.0;
    float edge = smoothstep(EdgeStart, 1.0, max(abs(centred.x), abs(centred.y)));

    float tremble = sin(texCoord.y * 21.0 + GameTime * 0.09)
        + 0.5 * cos(texCoord.x * 63.0 - GameTime * 0.05);
    vec2 offset = vec2(tremble * 0.25, tremble * 0.8) * 0.0055 * Intensity * edge;

    vec4 base = texture(InSampler, clamp(texCoord + offset, vec2(0.0), vec2(1.0)));

    // A cold periphery: the blue channel is left alone while red and green are pulled down, which
    // drains the warmth out of the edges without darkening them.
    vec3 chilled = mix(base.rgb, base.rgb * vec3(0.80, 0.90, 1.18), edge * 0.55);
    fragColor = vec4(chilled, 1.0);
}
