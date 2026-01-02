// @id: glitch
// @name: Glitch
// @category: CREATIVE
// @premium: true

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

vec4 transition(vec2 uv) {
    float intensity = sin(progress * 3.14159);

    // Random horizontal displacement
    float slice = floor(uv.y * 20.0);
    float offset = (rand(vec2(slice, progress * 10.0)) - 0.5) * intensity * 0.1;

    vec2 fromUV = vec2(uv.x + offset, uv.y);
    vec2 toUV = vec2(uv.x - offset, uv.y);

    // Color channel separation
    vec4 fromColor = getFromColor(fromUV);
    vec4 toColor = getToColor(toUV);

    // RGB shift for glitch effect
    vec4 result;
    result.r = mix(fromColor.r, toColor.r, progress + intensity * 0.1);
    result.g = mix(fromColor.g, toColor.g, progress);
    result.b = mix(fromColor.b, toColor.b, progress - intensity * 0.1);
    result.a = 1.0;

    return result;
}
