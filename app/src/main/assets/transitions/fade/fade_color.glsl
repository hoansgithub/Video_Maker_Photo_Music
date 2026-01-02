// @id: fade_color
// @name: Flash
// @category: FADE
// @premium: false

// Flash transition - bright burst in the middle
// Fades to white/bright then reveals next image

vec4 transition(vec2 uv) {
    vec4 fromColor = getFromColor(uv);
    vec4 toColor = getToColor(uv);

    // Flash intensity - peaks at middle
    float flash = sin(progress * 3.14159);
    flash = pow(flash, 0.7); // Make flash more punchy

    // Radial flash from center
    float dist = length(uv - 0.5);
    float radialFlash = flash * (1.0 - dist * 0.5);

    // Flash color (bright white with slight warmth)
    vec3 flashColor = vec3(1.0, 0.98, 0.95);

    // Blend images
    float blend = smoothstep(0.4, 0.6, progress);
    vec4 result = mix(fromColor, toColor, blend);

    // Apply flash
    result.rgb = mix(result.rgb, flashColor, radialFlash * 0.9);

    // Slight overexposure bloom
    result.rgb += flash * 0.2;

    return result;
}
