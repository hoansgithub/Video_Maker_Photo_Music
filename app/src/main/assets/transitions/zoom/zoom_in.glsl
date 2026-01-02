// @id: zoom_in
// @name: Zoom In
// @category: ZOOM
// @premium: false

vec4 transition(vec2 uv) {
    float scale = 1.0 + progress * 0.5;
    vec2 center = vec2(0.5, 0.5);
    vec2 fromUV = (uv - center) / scale + center;

    vec4 fromColor = getFromColor(fromUV);
    vec4 toColor = getToColor(uv);

    // Fade as we zoom
    float fade = smoothstep(0.3, 1.0, progress);
    return mix(fromColor, toColor, fade);
}
