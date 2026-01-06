// @id: crosshatch
// @name: Crosshatch
// @category: CREATIVE
// @premium: false

vec4 transition(vec2 uv) {
    float p = progress;

    // Early exit: show fromColor when progress is 0
    if (p <= 0.0) {
        return getFromColor(uv);
    }
    // Show toColor when progress is 1
    if (p >= 1.0) {
        return getToColor(uv);
    }

    // Diagonal lines pattern
    float spacing = 0.1;

    // Two sets of diagonal lines
    float line1 = mod(uv.x + uv.y, spacing);
    float line2 = mod(uv.x - uv.y, spacing);

    // Lines reveal based on progress (offset by small epsilon to prevent showing at p=0)
    float threshold = p * spacing;
    float reveal1 = step(line1, threshold);
    float reveal2 = step(line2, threshold);

    // Combine both line patterns
    float pattern = max(reveal1, reveal2);

    vec4 fromColor = getFromColor(uv);
    vec4 toColor = getToColor(uv);

    return mix(fromColor, toColor, pattern);
}
