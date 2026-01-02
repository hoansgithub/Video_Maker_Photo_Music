// @id: squares_wire
// @name: Squares Wire
// @category: GEOMETRIC
// @premium: false

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

vec4 transition(vec2 uv) {
    float p = progress;

    // Grid of squares
    float gridSize = 10.0;
    vec2 grid = floor(uv * gridSize);

    // Random delay per square
    float delay = rand(grid) * 0.7;
    float squareP = clamp((p - delay) / 0.3, 0.0, 1.0);

    // Square grows from center
    vec2 squareUV = fract(uv * gridSize);
    vec2 squareCenter = abs(squareUV - 0.5);
    float squareDist = max(squareCenter.x, squareCenter.y);

    // Wire frame effect during transition
    float wire = step(0.4 - squareP * 0.4, squareDist) * step(squareDist, 0.5);

    vec4 fromColor = getFromColor(uv);
    vec4 toColor = getToColor(uv);

    // Show to color through wire, then fill
    if (squareP > 0.5) {
        return toColor;
    } else if (wire > 0.0) {
        return mix(fromColor, toColor, squareP * 2.0);
    }
    return fromColor;
}
