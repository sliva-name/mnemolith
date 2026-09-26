#version 330

// Fracture fringe. The pass runs only while the player's chunk is in the fracture band.
// Strength is baked: NeoForge 26.2 post-effect uniforms are static values in the JSON.
// Center stays readable. The edge desaturates and splits a thin chromatic fringe.
// Darkening is the pressure vignette, not this pass, so the two toggles stay independent.

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 fromCenter = texCoord - vec2(0.5);
    float edge = smoothstep(0.22, 0.78, length(fromCenter * vec2(1.15, 0.90)));
    float shift = edge * 0.0035;
    float red = texture(InSampler, texCoord + vec2(shift, 0.0)).r;
    float green = texture(InSampler, texCoord).g;
    float blue = texture(InSampler, texCoord - vec2(shift, shift * 0.35)).b;
    vec3 color = vec3(red, green, blue);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float desat = mix(0.22, 0.58, edge);
    color = mix(color, vec3(luma), desat);
    color *= mix(vec3(1.0), vec3(0.86, 0.90, 0.96), 0.45);
    fragColor = vec4(color, 1.0);
}
