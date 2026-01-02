// @id: rotate
// @name: Rotate
// @category: ROTATE
// @premium: false

vec4 transition(vec2 uv) {
    float angle = progress * 3.14159;
    vec2 center = vec2(0.5, 0.5);
    vec2 offset = uv - center;

    float c = cos(angle);
    float s = sin(angle);
    vec2 rotated = vec2(offset.x * c - offset.y * s, offset.x * s + offset.y * c);
    vec2 fromUV = rotated + center;

    // Clamp and blend
    if (fromUV.x < 0.0 || fromUV.x > 1.0 || fromUV.y < 0.0 || fromUV.y > 1.0) {
        return getToColor(uv);
    }
    return mix(getFromColor(fromUV), getToColor(uv), progress);
}
