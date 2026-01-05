// @id: glitch_memories
// @name: Glitch Memories
// @category: CREATIVE
// @premium: true

// Glitch transition with digital artifacts and chromatic aberration
// FROM image glitches and fragments into TO image

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

vec4 transition(vec2 uv) {
    // Smooth easing
    float t = progress * progress * (3.0 - 2.0 * progress);

    // Glitch intensity: 0 at start/end, peaks in middle
    float glitchIntensity = sin(progress * 3.14159);

    // At progress=0: show FROM only, no glitch
    // At progress=1: show TO only, no glitch
    if (progress < 0.01) {
        return getFromColor(uv);
    }
    if (progress > 0.99) {
        return getToColor(uv);
    }

    // Block-based glitching
    float blockSize = 0.04 + rand(vec2(floor(t * 8.0), 0.0)) * 0.04;
    vec2 block = floor(uv / blockSize) * blockSize;

    // Random horizontal displacement per block
    float displaceChance = glitchIntensity * 0.4;  // Only some blocks glitch
    float shouldDisplace = step(rand(block + 0.1), displaceChance);
    float randOffset = (rand(block + floor(t * 15.0)) * 2.0 - 1.0);
    float offsetAmount = glitchIntensity * 0.08;

    vec2 distortedUV = uv;
    distortedUV.x += randOffset * offsetAmount * shouldDisplace;

    // Chromatic aberration - scales with glitch intensity
    float aberration = glitchIntensity * 0.015;

    // Clamp distorted UV first
    distortedUV = clamp(distortedUV, 0.0, 1.0);

    // Clamp aberration UVs to prevent sampling outside texture bounds
    vec2 uvR = clamp(distortedUV + vec2(aberration, 0.0), 0.0, 1.0);
    vec2 uvB = clamp(distortedUV - vec2(aberration, 0.0), 0.0, 1.0);

    // Sample FROM with chromatic aberration
    vec4 fromR = getFromColor(uvR);
    vec4 fromG = getFromColor(distortedUV);
    vec4 fromB = getFromColor(uvB);
    vec4 fromColor = vec4(fromR.r, fromG.g, fromB.b, 1.0);

    // Sample TO with chromatic aberration
    vec4 toR = getToColor(uvR);
    vec4 toG = getToColor(distortedUV);
    vec4 toB = getToColor(uvB);
    vec4 toColor = vec4(toR.r, toG.g, toB.b, 1.0);

    // Flash probability increases with progress
    // At t=0: all FROM (threshold=1.0, rand never exceeds)
    // At t=0.5: 50/50 mix
    // At t=1: all TO (threshold=0.0, rand always exceeds)
    float flashThreshold = 1.0 - t;
    float blockRand = rand(block + floor(t * 12.0));
    float showTo = step(flashThreshold, blockRand);

    // Add some scanline flicker
    float scanline = step(0.98, rand(vec2(uv.y * 100.0, floor(t * 30.0)))) * glitchIntensity;

    vec4 result = mix(fromColor, toColor, showTo);

    // Occasional white flash on glitch blocks
    result.rgb += vec3(scanline * 0.3);

    return result;
}
