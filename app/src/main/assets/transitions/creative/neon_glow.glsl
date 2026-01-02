// @id: neon_glow
// @name: Neon Glow
// @category: CREATIVE
// @premium: true

// Cyberpunk neon transition with chromatic aberration and glow
// Inspired by: https://halisavakis.com/my-take-on-shaders-chromatic-aberration/

vec4 transition(vec2 uv) {
    if (progress < 0.01) return getFromColor(uv);
    if (progress > 0.99) return getToColor(uv);

    float t = progress;
    float intensity = sin(t * 3.14159);

    // Chromatic aberration - RGB split
    float aberration = intensity * 0.025;
    vec2 dir = uv - 0.5;

    vec2 uvR = uv + dir * aberration;
    vec2 uvG = uv;
    vec2 uvB = uv - dir * aberration;

    // Sample both images with aberration
    vec3 fromR = getFromColor(uvR).r * vec3(1.0, 0.0, 0.0);
    vec3 fromG = getFromColor(uvG).g * vec3(0.0, 1.0, 0.0);
    vec3 fromB = getFromColor(uvB).b * vec3(0.0, 0.0, 1.0);
    vec3 fromColor = fromR + fromG + fromB;

    vec3 toR = getToColor(uvR).r * vec3(1.0, 0.0, 0.0);
    vec3 toG = getToColor(uvG).g * vec3(0.0, 1.0, 0.0);
    vec3 toB = getToColor(uvB).b * vec3(0.0, 0.0, 1.0);
    vec3 toColor = toR + toG + toB;

    // Blend
    vec3 result = mix(fromColor, toColor, t);

    // Neon glow boost - enhance bright areas
    float luma = dot(result, vec3(0.299, 0.587, 0.114));
    vec3 neonTint = vec3(1.0, 0.2, 0.8); // Pink/magenta
    vec3 neonTint2 = vec3(0.2, 0.8, 1.0); // Cyan
    vec3 glow = mix(neonTint, neonTint2, uv.x) * intensity * 0.3;

    result += glow * luma;

    // Scanline overlay
    float scanline = sin(uv.y * 400.0) * 0.03 * intensity;
    result -= scanline;

    // Vignette with neon edge
    float dist = length(uv - 0.5);
    float vignette = 1.0 - dist * 0.5;
    result *= vignette;

    // Edge glow
    float edgeGlow = smoothstep(0.5, 0.7, dist) * intensity * 0.4;
    result += mix(neonTint, neonTint2, uv.y) * edgeGlow;

    return vec4(result, 1.0);
}
