// @id: rgb_split
// @name: RGB Split
// @category: CREATIVE
// @premium: false

// Strong RGB channel separation glitch effect
// Each color channel moves independently

float hash(float n) {
    return fract(sin(n) * 43758.5453);
}

vec4 transition(vec2 uv) {
    if (progress < 0.01) return getFromColor(uv);
    if (progress > 0.99) return getToColor(uv);

    float t = progress;
    float intensity = sin(t * 3.14159);

    // Animated split directions
    float time = t * 10.0;
    float angle1 = time * 0.7;
    float angle2 = time * -0.5 + 2.0;
    float angle3 = time * 0.3 + 4.0;

    // Split amount
    float split = intensity * 0.04;

    // RGB channel offsets
    vec2 offsetR = vec2(cos(angle1), sin(angle1)) * split;
    vec2 offsetG = vec2(cos(angle2), sin(angle2)) * split * 0.5;
    vec2 offsetB = vec2(cos(angle3), sin(angle3)) * split;

    // Random glitch offset
    float glitchLine = floor(uv.y * 30.0);
    float glitch = hash(glitchLine + floor(time * 5.0)) * intensity * 0.02;
    offsetR.x += glitch;
    offsetB.x -= glitch;

    // Clamp UV offsets to prevent sampling outside texture bounds
    vec2 uvR = clamp(uv + offsetR, 0.0, 1.0);
    vec2 uvG = clamp(uv + offsetG, 0.0, 1.0);
    vec2 uvB = clamp(uv + offsetB, 0.0, 1.0);

    // Sample channels
    float fromR = getFromColor(uvR).r;
    float fromG = getFromColor(uvG).g;
    float fromB = getFromColor(uvB).b;

    float toR = getToColor(uvR).r;
    float toG = getToColor(uvG).g;
    float toB = getToColor(uvB).b;

    // Blend
    vec3 result;
    result.r = mix(fromR, toR, t);
    result.g = mix(fromG, toG, t);
    result.b = mix(fromB, toB, t);

    // Digital noise (subtle)
    float noise = (hash(uv.x * 100.0 + uv.y * 100.0 + time) - 0.5) * intensity * 0.05;
    result += noise;

    // Clamp final result
    result = clamp(result, 0.0, 1.0);

    return vec4(result, 1.0);
}
