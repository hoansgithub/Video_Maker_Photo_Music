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

    // Sample channels
    float fromR = getFromColor(uv + offsetR).r;
    float fromG = getFromColor(uv + offsetG).g;
    float fromB = getFromColor(uv + offsetB).b;

    float toR = getToColor(uv + offsetR).r;
    float toG = getToColor(uv + offsetG).g;
    float toB = getToColor(uv + offsetB).b;

    // Blend
    vec3 result;
    result.r = mix(fromR, toR, t);
    result.g = mix(fromG, toG, t);
    result.b = mix(fromB, toB, t);

    // Digital noise
    float noise = (hash(uv.x * 100.0 + uv.y * 100.0 + time) - 0.5) * intensity * 0.1;
    result += noise;

    return vec4(result, 1.0);
}
