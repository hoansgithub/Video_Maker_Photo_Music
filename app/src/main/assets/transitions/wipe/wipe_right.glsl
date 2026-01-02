// @id: wipe_right
// @name: Wipe Right
// @category: WIPE
// @premium: false

vec4 transition(vec2 uv) {
    float edge = progress * (1.0 + smoothness) - smoothness;
    float mask = smoothstep(edge, edge + smoothness, uv.x);
    return mix(getToColor(uv), getFromColor(uv), mask);
}
