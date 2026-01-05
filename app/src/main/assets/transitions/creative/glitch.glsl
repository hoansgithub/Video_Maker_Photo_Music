// @id: glitch
// @name: Glitch
// @category: CREATIVE
// @premium: true

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

vec4 transition(vec2 uv) {
    // Clean start and end
    if (progress < 0.01) return getFromColor(uv);
    if (progress > 0.99) return getToColor(uv);

    float intensity = sin(progress * 3.14159);

    // Random horizontal displacement
    float slice = floor(uv.y * 20.0);
    float offset = (rand(vec2(slice, progress * 10.0)) - 0.5) * intensity * 0.1;

    // Clamp UVs to prevent sampling outside texture bounds
    vec2 fromUV = clamp(vec2(uv.x + offset, uv.y), 0.0, 1.0);
    vec2 toUV = clamp(vec2(uv.x - offset, uv.y), 0.0, 1.0);

    // Color channel separation
    vec4 fromColor = getFromColor(fromUV);
    vec4 toColor = getToColor(toUV);

    // RGB shift for glitch effect (clamped progress values)
    vec4 result;
    float rProgress = clamp(progress + intensity * 0.1, 0.0, 1.0);
    float bProgress = clamp(progress - intensity * 0.1, 0.0, 1.0);
    result.r = mix(fromColor.r, toColor.r, rProgress);
    result.g = mix(fromColor.g, toColor.g, progress);
    result.b = mix(fromColor.b, toColor.b, bProgress);
    result.a = 1.0;

    return result;
}
