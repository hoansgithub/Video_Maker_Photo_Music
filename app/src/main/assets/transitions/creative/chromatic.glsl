// @id: chromatic
// @name: RGB Split
// @category: CREATIVE
// @premium: true

vec4 transition(vec2 uv) {
    float p = progress;

    // RGB split amount - peaks at middle of transition
    float splitAmount = sin(p * 3.14159) * 0.05;

    // Direction of split
    vec2 dir = normalize(uv - 0.5);

    // Sample each channel with offset
    vec2 uvR = uv + dir * splitAmount;
    vec2 uvG = uv;
    vec2 uvB = uv - dir * splitAmount;

    // Clamp UVs
    uvR = clamp(uvR, 0.0, 1.0);
    uvB = clamp(uvB, 0.0, 1.0);

    vec4 fromColor = vec4(
        getFromColor(uvR).r,
        getFromColor(uvG).g,
        getFromColor(uvB).b,
        1.0
    );

    vec4 toColor = vec4(
        getToColor(uvR).r,
        getToColor(uvG).g,
        getToColor(uvB).b,
        1.0
    );

    return mix(fromColor, toColor, p);
}
