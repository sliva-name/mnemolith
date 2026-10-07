#version 330

// Mnemolith lens "thermal" view: the scene is blended toward a dark plum -> rose ramp by luminance,
// lightly in the centre and strongly at the edges, with a soft vignette. Echo outlines are composited after this pass, so they keep their light pink.

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

const vec3 SHADOW = vec3(0.055, 0.010, 0.040);
const vec3 MID = vec3(0.400, 0.065, 0.235);
const vec3 HIGH = vec3(0.930, 0.560, 0.720);

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    luma = pow(clamp(luma, 0.0, 1.0), 0.85);
    vec3 ramp = luma < 0.5 ? mix(SHADOW, MID, luma * 2.0) : mix(MID, HIGH, (luma - 0.5) * 2.0);
    vec2 d = texCoord - vec2(0.5);
    float vignette = smoothstep(0.82, 0.28, length(d * vec2(1.0, 0.8)));
    // Lighter wash: the centre keeps most of the scene's own colour, the pink ramp gathers toward the edges.
    vec3 pink = mix(color * vec3(0.70, 0.42, 0.58), ramp, 0.75);
    float strength = mix(0.85, 0.40, vignette);
    vec3 tinted = mix(color, pink, strength);
    tinted *= mix(0.62, 1.0, vignette);
    fragColor = vec4(tinted, 1.0);
}
