// @id: wind
// @name: Wind
// @category: CREATIVE
// @premium: false

// Dramatic Sand Storm - Performance optimized
// Reduced noise calls, simplified math

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Simplified fbm - only 3 octaves
float fbm(vec2 p) {
    return noise(p) * 0.5 + noise(p * 2.0) * 0.3 + noise(p * 4.0) * 0.2;
}

vec4 transition(vec2 uv) {
    // Clean start/end
    if (progress < 0.01) {
        return getFromColor(uv);
    }
    if (progress > 0.99) {
        return getToColor(uv);
    }

    float t = progress;

    // === TURBULENT EROSION (single fbm call) ===
    float turbulence = fbm(uv * 5.0 + vec2(t * 3.0, t));

    // Wind position with wave
    float windPos = uv.x + sin(uv.y * 6.0 + t * 4.0) * 0.06;

    // Erosion
    float erodeThreshold = t * 1.3;
    float edge = windPos + turbulence * 0.3;
    float eroded = smoothstep(erodeThreshold, erodeThreshold - 0.12, edge);

    // === STORM ZONE ===
    float stormZone = smoothstep(erodeThreshold + 0.25, erodeThreshold, edge)
                    - smoothstep(erodeThreshold, erodeThreshold - 0.2, edge);
    stormZone = max(0.0, stormZone);

    // === PARTICLES (reuse turbulence, one extra noise) ===
    float particleNoise = noise(uv * 25.0 + vec2(t * 10.0, 0.0));
    float particles = smoothstep(0.5, 0.7, particleNoise) * stormZone;

    // === SIMPLE DISPLACEMENT ===
    vec2 fromUV = uv;
    fromUV.x += stormZone * 0.1;
    fromUV.y += sin(uv.x * 25.0 + t * 15.0) * 0.015 * stormZone;
    fromUV = clamp(fromUV, 0.0, 1.0);

    // === SAMPLE IMAGES ===
    vec4 fromColor = getFromColor(fromUV);
    vec4 toColor = getToColor(uv);

    // === COLORS ===
    vec3 sandColor = vec3(1.0, 0.95, 0.85);
    vec3 dustColor = vec3(0.9, 0.85, 0.75);

    // === COMPOSITE ===
    vec3 result = fromColor.rgb;

    // Darken in storm zone
    result *= 1.0 - stormZone * 0.2;

    // Add particles
    result = mix(result, sandColor, particles * 0.6);

    // Dust haze
    result = mix(result, dustColor, stormZone * 0.2);

    // Reveal TO
    result = mix(result, toColor.rgb, eroded);

    // Edge glow
    float edgeGlow = stormZone * (1.0 - eroded);
    result = mix(result, sandColor, edgeGlow * 0.4);

    return vec4(result, 1.0);
}
