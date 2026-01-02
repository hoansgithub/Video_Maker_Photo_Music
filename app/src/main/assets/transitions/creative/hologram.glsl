// @id: hologram
// @name: Hologram
// @category: CREATIVE
// @premium: true

// Sci-fi holographic transition effect
// Scanlines, flicker, and cyan/magenta tint

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 transition(vec2 uv) {
    if (progress < 0.01) return getFromColor(uv);
    if (progress > 0.99) return getToColor(uv);

    float t = progress;
    float intensity = sin(t * 3.14159);

    // Hologram flicker
    float flicker = 0.97 + hash(vec2(floor(t * 30.0), 0.0)) * 0.03;

    // Scan line moving down
    float scanPos = fract(t * 2.0);
    float scanLine = smoothstep(scanPos - 0.1, scanPos, uv.y)
                   - smoothstep(scanPos, scanPos + 0.02, uv.y);

    // Horizontal scanlines
    float hScan = sin(uv.y * 300.0) * 0.5 + 0.5;
    hScan = pow(hScan, 2.0) * 0.15 * intensity;

    // Vertical interference
    float vScan = sin(uv.x * 50.0 + t * 10.0) * 0.02 * intensity;

    // Sample with slight distortion
    vec2 distortedUV = uv;
    distortedUV.x += vScan;
    distortedUV.y += scanLine * 0.01;
    distortedUV = clamp(distortedUV, 0.0, 1.0);

    vec4 fromColor = getFromColor(distortedUV);
    vec4 toColor = getToColor(distortedUV);
    vec4 result = mix(fromColor, toColor, t);

    // Hologram color tint (cyan + magenta)
    vec3 holoTint;
    holoTint.r = result.r * 0.8 + result.b * 0.3;  // Magenta influence
    holoTint.g = result.g * 0.6 + result.b * 0.4;  // Cyan influence
    holoTint.b = result.b * 1.2;
    result.rgb = mix(result.rgb, holoTint, intensity * 0.6);

    // Add cyan/magenta edge glow
    float edgeGlow = pow(abs(uv.x - 0.5) * 2.0, 3.0) * intensity * 0.3;
    result.r += edgeGlow * 0.8;  // Magenta
    result.b += edgeGlow;

    // Apply effects
    result.rgb *= flicker;
    result.rgb *= 1.0 - hScan;
    result.rgb += scanLine * vec3(0.3, 0.8, 1.0) * 0.3;  // Cyan scan line

    // Slight transparency effect
    float alpha = 0.9 + intensity * 0.1;
    result.rgb *= alpha;

    // Digital noise
    float noise = (hash(uv * 500.0 + t) - 0.5) * intensity * 0.05;
    result.rgb += noise;

    return result;
}
