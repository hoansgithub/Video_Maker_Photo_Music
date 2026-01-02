// @id: crosshatch
// @name: Crosshatch
// @category: CREATIVE
// @premium: false

vec4 transition(vec2 uv) {
    float p = progress;

    // Diagonal lines pattern
    float lineWidth = 0.03;
    float spacing = 0.1;

    // Two sets of diagonal lines
    float line1 = mod(uv.x + uv.y, spacing);
    float line2 = mod(uv.x - uv.y, spacing);

    // Lines reveal based on progress
    float reveal1 = step(line1, p * spacing);
    float reveal2 = step(line2, p * spacing);

    // Combine both line patterns
    float pattern = max(reveal1, reveal2);

    vec4 fromColor = getFromColor(uv);
    vec4 toColor = getToColor(uv);

    return mix(fromColor, toColor, pattern);
}
