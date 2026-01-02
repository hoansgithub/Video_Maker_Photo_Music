// @id: dreamy
// @name: Dreamy
// @category: FADE
// @premium: false

// Dreamy transition with soft bloom, light leak, and gentle blur

vec4 transition(vec2 uv) {
    float t = progress;
    float intensity = sin(t * 3.14159);

    // Soft blur amount
    float blur = intensity * 0.015;

    // Sample with blur (5-tap)
    vec4 fromColor = getFromColor(uv) * 0.4;
    fromColor += getFromColor(uv + vec2(blur, 0.0)) * 0.15;
    fromColor += getFromColor(uv - vec2(blur, 0.0)) * 0.15;
    fromColor += getFromColor(uv + vec2(0.0, blur)) * 0.15;
    fromColor += getFromColor(uv - vec2(0.0, blur)) * 0.15;

    vec4 toColor = getToColor(uv) * 0.4;
    toColor += getToColor(uv + vec2(blur, 0.0)) * 0.15;
    toColor += getToColor(uv - vec2(blur, 0.0)) * 0.15;
    toColor += getToColor(uv + vec2(0.0, blur)) * 0.15;
    toColor += getToColor(uv - vec2(0.0, blur)) * 0.15;

    // Light leak from corner
    vec2 leakCenter = vec2(0.8, 0.2);
    float leak = 1.0 - length(uv - leakCenter);
    leak = pow(max(0.0, leak), 2.0) * intensity * 0.4;
    vec3 leakColor = vec3(1.0, 0.95, 0.85); // Warm light

    // Bloom/glow
    float glow = intensity * 0.15;

    // Soft vignette
    float vignette = 1.0 - length(uv - 0.5) * 0.3 * intensity;

    // Blend
    vec4 result = mix(fromColor, toColor, t);
    result.rgb += glow;
    result.rgb += leakColor * leak;
    result.rgb *= vignette;

    // Slight warmth
    result.r += intensity * 0.03;

    return result;
}
