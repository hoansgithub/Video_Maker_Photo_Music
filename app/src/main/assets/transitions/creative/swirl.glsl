// @id: swirl
// @name: Swirl
// @category: CREATIVE
// @premium: true

vec4 transition(vec2 uv) {
    vec2 center = vec2(0.5, 0.5);
    vec2 offset = uv - center;
    float dist = length(offset);

    // Swirl angle based on distance and progress
    float angle = (1.0 - dist) * progress * 6.28318 * 2.0;
    float c = cos(angle);
    float s = sin(angle);

    vec2 rotated = vec2(
        offset.x * c - offset.y * s,
        offset.x * s + offset.y * c
    );

    vec2 fromUV = rotated + center;

    // Clamp UV
    fromUV = clamp(fromUV, 0.0, 1.0);

    return mix(getFromColor(fromUV), getToColor(uv), progress);
}
