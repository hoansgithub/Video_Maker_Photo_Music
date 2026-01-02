// @id: zoom_rotate
// @name: Zoom Rotate
// @category: ZOOM
// @premium: false

vec4 transition(vec2 uv) {
    float angle = progress * 3.14159 * 0.5;
    float scale = 1.0 + progress * 0.3;
    vec2 center = vec2(0.5, 0.5);

    vec2 offset = uv - center;
    float c = cos(angle);
    float s = sin(angle);
    vec2 rotated = vec2(offset.x * c - offset.y * s, offset.x * s + offset.y * c);
    vec2 fromUV = rotated / scale + center;

    vec4 fromColor = getFromColor(fromUV);
    vec4 toColor = getToColor(uv);

    return mix(fromColor, toColor, smoothstep(0.3, 0.9, progress));
}
