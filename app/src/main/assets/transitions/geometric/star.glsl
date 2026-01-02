// @id: star
// @name: Star
// @category: GEOMETRIC
// @premium: true

vec4 transition(vec2 uv) {
    vec2 center = vec2(0.5, 0.5);
    vec2 p = uv - center;
    float angle = atan(p.y, p.x);
    float dist = length(p);

    // Star shape with 5 points
    float star = cos(angle * 5.0) * 0.3 + 0.7;
    // Ease-in curve: starts small/slow, accelerates
    float easedProgress = progress * progress;
    float radius = easedProgress * star * 1.0;

    if (dist < radius) {
        return getToColor(uv);
    } else {
        return getFromColor(uv);
    }
}
