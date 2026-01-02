// @id: flip_horizontal
// @name: Flip Horizontal
// @category: ROTATE
// @premium: false

vec4 transition(vec2 uv) {
    float p = progress * 2.0;

    if (p < 1.0) {
        // First half: scale from image down to line
        float scale = 1.0 - p;
        vec2 fromUV = vec2((uv.x - 0.5) / max(scale, 0.001) + 0.5, uv.y);
        if (fromUV.x < 0.0 || fromUV.x > 1.0) {
            return vec4(0.0, 0.0, 0.0, 1.0);
        }
        return getFromColor(fromUV);
    } else {
        // Second half: scale to image up from line
        float scale = p - 1.0;
        vec2 toUV = vec2((uv.x - 0.5) / max(scale, 0.001) + 0.5, uv.y);
        if (toUV.x < 0.0 || toUV.x > 1.0) {
            return vec4(0.0, 0.0, 0.0, 1.0);
        }
        return getToColor(toUV);
    }
}
