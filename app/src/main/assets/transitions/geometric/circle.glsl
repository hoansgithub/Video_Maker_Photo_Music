// @id: circle
// @name: Circle Reveal
// @category: GEOMETRIC
// @premium: false

vec4 transition(vec2 uv) {
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(uv, center);
    // Ease-in curve for radius: starts small/slow, accelerates
    float easedProgress = progress * progress;
    float radius = easedProgress * 1.0; // Max radius ~1.0 covers corners

    float edge = smoothstep(radius - smoothness, radius, dist);
    return mix(getToColor(uv), getFromColor(uv), edge);
}
