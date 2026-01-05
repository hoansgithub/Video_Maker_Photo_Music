// @id: crt_retro
// @name: CRT Retro
// @category: CREATIVE
// @premium: true

// Classic CRT monitor effect with scanlines and curvature
// Inspired by: https://babylonjs.medium.com/retro-crt-shader/

vec2 curveUV(vec2 uv, float curvature) {
    uv = uv * 2.0 - 1.0;
    vec2 offset = abs(uv.yx) * curvature;
    uv = uv + uv * offset * offset;
    uv = uv * 0.5 + 0.5;
    return uv;
}

vec4 transition(vec2 uv) {
    if (progress < 0.01) return getFromColor(uv);
    if (progress > 0.99) return getToColor(uv);

    float t = progress;
    float intensity = sin(t * 3.14159);

    // Screen curvature
    float curvature = intensity * 0.15;
    vec2 curvedUV = curveUV(uv, curvature);

    // Check if outside screen
    if (curvedUV.x < 0.0 || curvedUV.x > 1.0 || curvedUV.y < 0.0 || curvedUV.y > 1.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }

    // Sample with slight RGB offset (color bleeding)
    float bleed = intensity * 0.003;
    vec2 uvR = clamp(curvedUV + vec2(bleed, 0.0), 0.0, 1.0);
    vec2 uvB = clamp(curvedUV - vec2(bleed, 0.0), 0.0, 1.0);

    vec3 fromColor;
    fromColor.r = getFromColor(uvR).r;
    fromColor.g = getFromColor(curvedUV).g;
    fromColor.b = getFromColor(uvB).b;

    vec3 toColor;
    toColor.r = getToColor(uvR).r;
    toColor.g = getToColor(curvedUV).g;
    toColor.b = getToColor(uvB).b;

    vec3 result = mix(fromColor, toColor, t);

    // Scanlines
    float scanline = sin(curvedUV.y * 800.0) * 0.5 + 0.5;
    scanline = pow(scanline, 1.5) * 0.15 * intensity + (1.0 - 0.15 * intensity);
    result *= scanline;

    // Phosphor glow (RGB stripes)
    float phosphor = sin(curvedUV.x * 1200.0) * 0.5 + 0.5;
    phosphor = phosphor * 0.05 * intensity + (1.0 - 0.05 * intensity);
    result *= phosphor;

    // Screen flicker
    float flicker = 1.0 - intensity * 0.02 * sin(t * 50.0);
    result *= flicker;

    // Vignette (darker corners like old CRT)
    float dist = length(curvedUV - 0.5);
    float vignette = 1.0 - dist * dist * intensity * 0.8;
    result *= vignette;

    // Slight green tint (old monitor look)
    result.g += intensity * 0.02;

    // Clamp final result
    result = clamp(result, 0.0, 1.0);

    return vec4(result, 1.0);
}
