// @id: ripple
// @name: Ripple
// @category: CREATIVE
// @premium: false

// Ripple transition - water ripple emanates from center
// Effect peaks in middle, clean at start/end

vec4 transition(vec2 uv) {
    // Clean start/end
    if (progress < 0.01) {
        return getFromColor(uv);
    }
    if (progress > 0.99) {
        return getToColor(uv);
    }

    float frequency = 25.0;
    float speed = 12.0;

    vec2 center = vec2(0.5, 0.5);
    float dist = distance(uv, center);

    // Ripple intensity: 0 at start/end, peaks in middle
    float intensity = sin(progress * 3.14159);

    // Amplitude scales with intensity
    float amplitude = 0.04 * intensity;

    // Ripple expands outward from center over time
    float rippleRadius = progress * 1.5;

    // Wave only affects areas the ripple has reached
    float rippleMask = smoothstep(rippleRadius, rippleRadius - 0.3, dist);

    // Ripple wave with expanding rings
    float wave = sin(dist * frequency - progress * speed) * amplitude * rippleMask;

    // Direction from center
    vec2 dir = normalize(uv - center + 0.001); // +0.001 to avoid zero at center

    // Apply ripple distortion
    vec2 rippleUV = uv + dir * wave;
    rippleUV = clamp(rippleUV, 0.0, 1.0);

    // Sample both images with ripple
    vec4 fromColor = getFromColor(rippleUV);
    vec4 toColor = getToColor(rippleUV);

    // Smooth crossfade
    float blend = smoothstep(0.0, 1.0, progress);

    return mix(fromColor, toColor, blend);
}
