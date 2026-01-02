// @id: heart
// @name: Heart
// @category: GEOMETRIC
// @premium: true

vec4 transition(vec2 uv) {
    vec2 p = (uv - 0.5) * 2.0;
    p.y -= 0.3;

    // Heart shape formula
    float a = atan(p.x, p.y) / 3.14159;
    float r = length(p);
    float h = abs(a);
    float d = (13.0 * h - 22.0 * h * h + 10.0 * h * h * h) / (6.0 - 5.0 * h);

    float heartDist = r - d * 0.5;
    // Ease-in curve for size: starts small/slow
    float easedProgress = progress * progress;
    float size = (1.0 - easedProgress) * 1.2;

    if (heartDist < size) {
        return getFromColor(uv);
    } else {
        return getToColor(uv);
    }
}
