// @id: tv_static
// @name: TV Static
// @category: CREATIVE
// @premium: true

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

vec4 transition(vec2 uv) {
    // Clean start and end
    if (progress < 0.01) return getFromColor(uv);
    if (progress > 0.99) return getToColor(uv);

    float p = progress;

    // Generate TV static noise
    float noise = rand(uv * 1000.0 + p * 100.0);

    // Static intensity - peaks at middle but capped for subtlety
    float intensity = sin(p * 3.14159) * 0.6;  // Max 60% static, not full

    // Mix between images through static
    vec4 fromColor = getFromColor(uv);
    vec4 toColor = getToColor(uv);

    // Blend from -> to with progress
    vec4 blendedColor = mix(fromColor, toColor, p);

    // Add static noise as overlay, not replacement
    vec4 staticColor = vec4(vec3(noise), 1.0);
    vec4 color = mix(blendedColor, staticColor, intensity * 0.5);  // Static is subtle overlay

    // Add scanlines (more subtle)
    float scanline = sin(uv.y * 400.0) * 0.05 * intensity;
    color.rgb += scanline;

    // Clamp final result
    color.rgb = clamp(color.rgb, 0.0, 1.0);

    return color;
}
