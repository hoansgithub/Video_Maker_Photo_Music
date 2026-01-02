// @id: kaleidoscope
// @name: Kaleidoscope
// @category: CREATIVE
// @premium: true

// Kaleidoscope transition - images fragment into mirrored segments
// Effect intensity peaks in the middle, clean at start/end

const float PI = 3.14159265359;

vec4 transition(vec2 uv) {
    // At start: show clean FROM image
    if (progress < 0.01) {
        return getFromColor(uv);
    }
    // At end: show clean TO image
    if (progress > 0.99) {
        return getToColor(uv);
    }

    // Smooth easing
    float t = progress * progress * (3.0 - 2.0 * progress);

    // Effect intensity: 0 at start/end, 1 in middle
    float intensity = sin(progress * PI);

    // Center coordinates
    vec2 center = uv - 0.5;

    // Polar coordinates
    float origAngle = atan(center.y, center.x);
    float radius = length(center);

    // Number of segments (more segments = more kaleidoscope effect)
    float segments = 6.0;
    float segmentAngle = PI * 2.0 / segments;

    // Rotation increases with progress
    float rotation = t * PI * 2.0;

    // Calculate kaleidoscope angle
    float kaleidoAngle = mod(origAngle + rotation, segmentAngle);
    kaleidoAngle = abs(kaleidoAngle - segmentAngle * 0.5);

    // Blend between original angle and kaleidoscope angle based on intensity
    float finalAngle = mix(origAngle, kaleidoAngle, intensity);

    // Convert back to UV
    vec2 effectUV = vec2(
        cos(finalAngle) * radius + 0.5,
        sin(finalAngle) * radius + 0.5
    );

    // Scale pulse effect - only during transition
    float scale = 1.0 + intensity * 0.2;
    effectUV = (effectUV - 0.5) / scale + 0.5;

    // Blend between original UV and effect UV based on intensity
    vec2 finalUV = mix(uv, effectUV, intensity);
    finalUV = clamp(finalUV, 0.0, 1.0);

    // Sample both images
    vec4 fromColor = getFromColor(finalUV);
    vec4 toColor = getToColor(finalUV);

    // Crossfade based on progress
    return mix(fromColor, toColor, t);
}
