// @id: slide_up
// @name: Slide Up
// @category: SLIDE
// @premium: false

vec4 transition(vec2 uv) {
    vec2 fromUV = uv + vec2(0.0, progress);
    vec2 toUV = uv + vec2(0.0, progress - 1.0);

    if (uv.y < 1.0 - progress) {
        return getFromColor(fromUV);
    } else {
        return getToColor(toUV);
    }
}
