// @id: zoom_out
// @name: Zoom Out
// @category: ZOOM
// @premium: false

vec4 transition(vec2 uv) {
    float scale = 1.5 - progress * 0.5;
    vec2 center = vec2(0.5, 0.5);
    vec2 toUV = (uv - center) * scale + center;

    vec4 fromColor = getFromColor(uv);
    vec4 toColor = getToColor(toUV);

    // Fade as we zoom
    float fade = smoothstep(0.0, 0.7, progress);
    return mix(fromColor, toColor, fade);
}
