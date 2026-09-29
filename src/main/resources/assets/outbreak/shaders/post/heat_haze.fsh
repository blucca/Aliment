#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform HeatHazeConfig {
    float Intensity;
    float EdgeStart;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// The fever shimmer. It deliberately only exists near the edges of the screen: the middle - the
// crosshair, and whatever the player is actually looking at - is left sharp, so this reads as "the
// world is swimming at the edges of my vision" rather than as a broken camera.
//
// Two sine waves at different speeds and angles, multiplied together, so the ripple never looks
// like one clean oscillation. GameTime is in ticks, so the constants are radians per tick.
void main() {
    vec2 centred = texCoord * 2.0 - 1.0;
    float edge = smoothstep(EdgeStart, 1.0, max(abs(centred.x), abs(centred.y)));

    float ripple = sin(texCoord.y * 46.0 + GameTime * 0.17) * cos(texCoord.x * 27.0 - GameTime * 0.11);
    vec2 offset = vec2(ripple * 0.6, ripple) * 0.0055 * Intensity * edge;

    vec4 base = texture(InSampler, clamp(texCoord + offset, vec2(0.0), vec2(1.0)));

    // A fever flushes the periphery; the colour is only pushed around at the edges, and the
    // middle keeps its own colour exactly.
    vec3 flushed = mix(base.rgb, base.rgb * vec3(1.12, 0.82, 0.70) + vec3(0.05, 0.0, 0.0), edge * 0.55);
    fragColor = vec4(flushed, 1.0);
}
