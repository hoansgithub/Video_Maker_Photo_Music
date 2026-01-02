// @id: blinds
// @name: Blinds
// @category: WIPE
// @premium: false

vec4 transition(vec2 uv) {
    float count = 10.0;
    float blindPos = fract(uv.x * count);
    float threshold = progress;

    if (blindPos < threshold) {
        return getToColor(uv);
    } else {
        return getFromColor(uv);
    }
}
