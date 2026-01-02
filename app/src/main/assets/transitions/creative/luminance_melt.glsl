// @id: luminance_melt
// @name: Luminance Melt
// @category: CREATIVE
// @premium: true

// Luminance-based melt transition
// Dark areas melt/drip down first, bright areas hold longer
// Creates a dramatic "melting painting" effect

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 transition(vec2 uv) {
    // Clean start/end
    if (progress < 0.01) {
        return getFromColor(uv);
    }
    if (progress > 0.98) {
        return getToColor(uv);
    }

    // Smooth progress
    float t = progress * progress * (3.0 - 2.0 * progress);

    // Sample FROM image at original position for luminance calculation
    vec4 fromOriginal = getFromColor(uv);
    float luma = dot(fromOriginal.rgb, vec3(0.299, 0.587, 0.114));

    // Add noise for organic dripping
    float noise = hash(vec2(floor(uv.x * 30.0), 0.0)) * 0.3;

    // Melt threshold - increases with progress
    // Dark areas (low luma) melt first, bright areas last
    float meltThreshold = t * 1.4;

    // How much this pixel has melted (0 = solid, 1 = fully melted)
    // Dark pixels (low luma) reach threshold earlier and melt first
    // Bright pixels (high luma) hold longer before melting
    float meltAmount = smoothstep(luma - 0.1 + noise * 0.3, luma + 0.2 + noise * 0.3, meltThreshold);

    // Drip distortion - pixels drip downward as they melt
    // More drip in the middle of transition
    float dripIntensity = sin(progress * 3.14159);
    float dripDistance = meltAmount * dripIntensity * 0.25;

    // Add some horizontal wobble for organic feel
    float wobble = sin(uv.y * 20.0 + t * 10.0) * 0.02 * meltAmount * dripIntensity;

    // Calculate dripped UV for FROM image
    vec2 drippedUV = uv;
    drippedUV.y += dripDistance;
    drippedUV.x += wobble;
    drippedUV = clamp(drippedUV, 0.0, 1.0);

    // Sample FROM at dripped position
    vec4 fromDripped = getFromColor(drippedUV);

    // Sample TO at original position
    vec4 toColor = getToColor(uv);

    // Blend: melted areas show TO, solid areas show dripped FROM
    vec4 result = mix(fromDripped, toColor, meltAmount);

    // Add subtle glow at melt edge (no darkening)
    float edgeGlow = smoothstep(0.4, 0.5, meltAmount) - smoothstep(0.5, 0.6, meltAmount);
    vec3 glowColor = vec3(1.0, 0.9, 0.7);
    result.rgb += glowColor * edgeGlow * 0.3 * dripIntensity;

    return result;
}
