// @id: wipe_left
// @name: Wipe Left
// @category: WIPE
// @premium: false

vec4 transition(vec2 uv) {
    float edge = progress * (1.0 + smoothness) - smoothness;
    float mask = smoothstep(edge, edge + smoothness, 1.0 - uv.x);
    return mix(getToColor(uv), getFromColor(uv), mask);
}
