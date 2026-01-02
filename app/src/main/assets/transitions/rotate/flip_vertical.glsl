// @id: flip_vertical
// @name: Flip Vertical
// @category: ROTATE
// @premium: false

vec4 transition(vec2 uv) {
    float p = progress * 2.0;

    if (p < 1.0) {
        float scale = 1.0 - p;
        vec2 fromUV = vec2(uv.x, (uv.y - 0.5) / max(scale, 0.001) + 0.5);
        if (fromUV.y < 0.0 || fromUV.y > 1.0) {
            return vec4(0.0, 0.0, 0.0, 1.0);
        }
        return getFromColor(fromUV);
    } else {
        float scale = p - 1.0;
        vec2 toUV = vec2(uv.x, (uv.y - 0.5) / max(scale, 0.001) + 0.5);
        if (toUV.y < 0.0 || toUV.y > 1.0) {
            return vec4(0.0, 0.0, 0.0, 1.0);
        }
        return getToColor(toUV);
    }
}
