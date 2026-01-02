// @id: slide_right
// @name: Slide Right
// @category: SLIDE
// @premium: false

vec4 transition(vec2 uv) {
    vec2 fromUV = uv - vec2(progress, 0.0);
    vec2 toUV = uv - vec2(progress - 1.0, 0.0);

    if (uv.x > progress) {
        return getFromColor(fromUV);
    } else {
        return getToColor(toUV);
    }
}
