// @id: blur
// @name: Blur
// @category: BLUR
// @premium: false

vec4 transition(vec2 uv) {
    // Blur intensity peaks at midpoint
    float blurAmount = sin(progress * 3.14159) * 0.03;

    vec4 fromColor = vec4(0.0);
    vec4 toColor = vec4(0.0);

    // Simple box blur with 9 samples
    for (float x = -1.0; x <= 1.0; x += 1.0) {
        for (float y = -1.0; y <= 1.0; y += 1.0) {
            vec2 offset = vec2(x, y) * blurAmount;
            fromColor += getFromColor(uv + offset);
            toColor += getToColor(uv + offset);
        }
    }
    fromColor /= 9.0;
    toColor /= 9.0;

    return mix(fromColor, toColor, progress);
}
