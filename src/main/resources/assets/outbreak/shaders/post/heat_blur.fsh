#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D PreviousSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform HeatBlurConfig {
    float Feedback;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// Motion blur, done the cheap way: the frame is mixed with the previous frame, so anything that has
// moved leaves a short trail behind it and anything standing still stays perfectly sharp. The
// "previous" target is a persistent one, which is what lets the last frame still be there.
//
// The previous frame's alpha is used as a "has this ever been written?" flag. A fresh target is
// cleared to transparent black, so without it the very first blurred frame - and the first frame
// after a window resize - would be the world mixed half and half with black. Instead the trail
// simply builds up from nothing over the first few frames.
void main() {
    vec4 current = texture(InSampler, texCoord);
    vec4 previous = texture(PreviousSampler, texCoord);

    float trail = Feedback * previous.a;
    fragColor = vec4(mix(current.rgb, previous.rgb, trail), 1.0);
}
