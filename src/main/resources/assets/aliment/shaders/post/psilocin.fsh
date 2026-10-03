#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Four intensities, one set per stage of the trip. The stage is chosen on the server (see
// `Physiology.psilocinTier`) and baked into four post-effect JSON files that all point at this
// shader, so nothing here has to be animated or pushed from the game side: stage 1 only pulls lines
// out of the block edges, stage 2 starts dyeing the blocks and adds a coloured grain, and stages 3
// and 4 bend the whole picture.
layout(std140) uniform PsilocinConfig {
    float LineStrength;
    float ColourStrength;
    float NoiseStrength;
    float WarpStrength;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// How many samples a pulled line is made of, and how far it reaches across the screen, in pixels.
const int LINE_STEPS = 4;
const float LINE_NEAR = 1.5;
const float LINE_FAR = 11.0;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

// A cheap hash, used for the random direction of a line, the random colour of a block and the grain.
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// Hue in 0..1 to a fully saturated colour.
vec3 hue(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

void main() {
    vec2 px = 1.0 / InSize;

    // 1. The world breathes: two slow waves across the screen, at different angles, pushing the
    //    sample point around. Mild at stage 3, and enough at stage 4 to stop the world making sense.
    vec2 uv = texCoord;
    if (WarpStrength > 0.0) {
        uv += vec2(
            sin(texCoord.y * 19.0 + texCoord.x * 7.0),
            cos(texCoord.x * 16.0 - texCoord.y * 6.0)
        ) * WarpStrength * px * 14.0;
    }

    vec3 color = texture(InSampler, uv).rgb;

    // 2. The blocks take on colours of their own: one hue per eight-pixel cell, mixed back through
    //    the pixel rather than painted over it, so the shading - and with it every shape in the
    //    world - stays readable. This is a colour cast, not a blindfold.
    if (ColourStrength > 0.0) {
        vec2 cell = floor(uv * InSize / 8.0);
        vec3 tint = hue(hash(cell));
        color = mix(color, mix(color, tint, 0.65) * (0.6 + 0.6 * luma(color)), ColourStrength);
    }

    // 2b. A grain of random colour over the whole screen, one pixel across, so it reads as noise
    //     rather than as more blocks. Only some pixels catch a speck, and each one gets its own hue.
    if (NoiseStrength > 0.0) {
        float grain = hash(floor(uv * InSize) * 1.7);
        float speck = step(0.55, grain);
        color = mix(color, hue(grain * 7.0) * (0.4 + 0.9 * luma(color)), NoiseStrength * speck);
    }

    // 3. Lines pulled out of the block edges, each in its own random direction.
    //
    //    Every eight-pixel cell picks one direction and one length from its hash, and a pixel is then
    //    part of a line if an *edge* lies along that direction. Looking for the edge at a distance
    //    rather than at the pixel itself is what turns a recoloured border into a line: the pixels
    //    that light up are a displaced copy of the edge, and four distances at falling strength are
    //    four copies, which together read as one tapered line shot out of the edge.
    if (LineStrength > 0.0) {
        vec2 cell = floor(uv * InSize / 8.0);
        float pick = hash(cell);
        float angle = pick * 6.2831853;
        vec2 dir = vec2(cos(angle), sin(angle));
        float reach = LINE_NEAR + (LINE_FAR - LINE_NEAR) * fract(pick * 7.31);

        float line = 0.0;
        for (int i = 0; i < LINE_STEPS; i++) {
            float d = LINE_NEAR + (reach - LINE_NEAR) * float(i + 1) / float(LINE_STEPS);
            vec2 at = uv + dir * px * d;
            float before = luma(texture(InSampler, at - dir * px).rgb);
            float after = luma(texture(InSampler, at + dir * px).rgb);
            float edge = smoothstep(0.08, 0.30, abs(after - before));
            line = max(line, edge * (1.0 - float(i + 1) / float(LINE_STEPS + 2)));
        }

        vec3 lineColour = hue(fract(pick * 5.7) + uv.x * 0.3 + uv.y * 0.2);
        color = mix(color, lineColour * (0.6 + 0.8 * luma(color)), LineStrength * line);
    }

    fragColor = vec4(color, 1.0);
}
