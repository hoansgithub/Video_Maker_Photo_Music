// @id: diamond
// @name: Diamond
// @category: GEOMETRIC
// @premium: false

vec4 transition(vec2 uv) {
    vec2 center = vec2(0.5, 0.5);
    vec2 offset = abs(uv - center);
    float dist = offset.x + offset.y; // Manhattan distance
    // Ease-in curve: starts small/slow, accelerates
    float easedProgress = progress * progress;
    float size = easedProgress * 1.2;

    float edge = smoothstep(size - smoothness, size, dist);
    return mix(getToColor(uv), getFromColor(uv), edge);
}
