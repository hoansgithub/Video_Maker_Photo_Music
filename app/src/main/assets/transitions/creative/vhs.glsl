// @id: vhs
// @name: VHS Retro
// @category: CREATIVE
// @premium: true

// VHS tape effect with random moving glitches
// Simulates old videotape tracking errors and degradation

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

float rand1(float x) {
    return fract(sin(x * 12.9898) * 43758.5453);
}

vec4 transition(vec2 uv) {
    // Clean start/end
    if (progress < 0.01) {
        return getFromColor(uv);
    }
    if (progress > 0.99) {
        return getToColor(uv);
    }

    float time = progress * 20.0;  // Animation speed
    float intensity = sin(progress * 3.14159);  // Effect intensity

    // === RANDOM TRACKING GLITCHES ===
    // Multiple glitch bands that move randomly
    float glitchOffset = 0.0;

    // Generate 3 random glitch bands at different Y positions
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        // Random Y position that changes over time
        float glitchY = rand1(floor(time * 2.0 + fi * 7.0)) ;
        float glitchHeight = 0.02 + rand1(fi + 0.5) * 0.04;

        // Check if current Y is in glitch band
        float inGlitch = smoothstep(glitchY - glitchHeight, glitchY, uv.y)
                       - smoothstep(glitchY, glitchY + glitchHeight, uv.y);

        // Random horizontal offset for this glitch
        float offset = (rand1(floor(time * 4.0 + fi * 3.0)) - 0.5) * 0.15;
        glitchOffset += inGlitch * offset * intensity;
    }

    // === LARGE ROLLING GLITCH BAR ===
    // A bigger distortion that rolls up the screen
    float rollY = fract(time * 0.3);
    float rollBand = smoothstep(rollY - 0.08, rollY - 0.04, uv.y)
                   - smoothstep(rollY + 0.04, rollY + 0.08, uv.y);
    glitchOffset += rollBand * 0.08 * intensity;

    // === HORIZONTAL JITTER ===
    float jitter = (rand(vec2(floor(time * 8.0), floor(uv.y * 50.0))) - 0.5) * 0.008 * intensity;

    // Apply horizontal distortions
    vec2 distortedUV = uv;
    distortedUV.x += glitchOffset + jitter;
    distortedUV.x = clamp(distortedUV.x, 0.0, 1.0);

    // === SCANLINES ===
    float scanline = sin(uv.y * 400.0) * 0.03 * intensity;
    float scanlineFlicker = sin(time * 50.0) * 0.01;

    // === CHROMATIC ABERRATION ===
    float aberration = (0.003 + intensity * 0.008);
    vec2 uvR = distortedUV + vec2(aberration, 0.0);
    vec2 uvG = distortedUV;
    vec2 uvB = distortedUV - vec2(aberration, 0.0);

    // Sample with chromatic aberration
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

    // Blend between from and to
    float blend = smoothstep(0.0, 1.0, progress);
    vec4 color = mix(fromColor, toColor, blend);

    // === STATIC NOISE ===
    float staticNoise = rand(uv * 500.0 + time) * 0.08 * intensity;

    // === HEAD SWITCHING NOISE (bottom of frame) ===
    float headSwitch = smoothstep(0.95, 1.0, uv.y) * rand(vec2(time * 10.0, uv.x)) * 0.5 * intensity;

    // === COLOR DEGRADATION ===
    // Slight desaturation and color shift
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    color.rgb = mix(color.rgb, vec3(luma), 0.15 * intensity);

    // Slight blue/magenta tint (VHS color bleeding)
    color.r *= 0.95;
    color.b *= 1.05;

    // Apply all noise effects
    color.rgb += scanline + scanlineFlicker;
    color.rgb += staticNoise;
    color.rgb = mix(color.rgb, vec3(1.0), headSwitch);

    // Clamp final color
    color.rgb = clamp(color.rgb, 0.0, 1.0);

    return color;
}
