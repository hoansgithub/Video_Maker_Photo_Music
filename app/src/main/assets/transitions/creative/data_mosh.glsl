// @id: data_mosh
// @name: Data Mosh
// @category: CREATIVE
// @premium: true

// Video compression artifact / datamosh effect
// Simulates corrupted video frames bleeding together

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 transition(vec2 uv) {
    if (progress < 0.01) return getFromColor(uv);
    if (progress > 0.99) return getToColor(uv);

    float t = progress;
    float intensity = sin(t * 3.14159);

    // Macro block grid (like video compression)
    float blockSize = 16.0;
    vec2 block = floor(uv * blockSize);
    float blockRand = hash(block + floor(t * 8.0));

    // Motion vector simulation - blocks shift based on "motion"
    vec2 motionVector = vec2(0.0);
    if (blockRand > 0.5) {
        motionVector.x = (hash(block + 0.1) - 0.5) * 0.2 * intensity;
        motionVector.y = (hash(block + 0.2) - 0.5) * 0.1 * intensity;
    }

    // I-frame vs P-frame simulation
    // Some blocks show FROM, some show TO, some are corrupted mix
    float frameType = hash(block + floor(t * 3.0));

    vec2 sampleUV = uv + motionVector;
    sampleUV = clamp(sampleUV, 0.0, 1.0);

    vec4 fromColor = getFromColor(sampleUV);
    vec4 toColor = getToColor(sampleUV);

    vec4 result;

    if (frameType < 0.3 * (1.0 - t)) {
        // I-frame from FROM image (decreases over time)
        result = fromColor;
    } else if (frameType > 0.7 + 0.3 * t) {
        // I-frame from TO image (increases over time)
        result = toColor;
    } else {
        // P-frame corruption - blend with artifacts
        float blendRand = hash(block + 0.5);

        // Smear effect - use color from neighboring block
        vec2 smearUV = uv + vec2(hash(block) - 0.5, 0.0) * 0.1 * intensity;
        smearUV = clamp(smearUV, 0.0, 1.0);

        vec4 smearFrom = getFromColor(smearUV);
        vec4 smearTo = getToColor(smearUV);

        result = mix(
            mix(fromColor, smearFrom, blendRand * intensity),
            mix(toColor, smearTo, blendRand * intensity),
            t
        );

        // Color channel corruption
        if (blockRand > 0.85) {
            result.r = mix(fromColor.r, toColor.g, t);
            result.b = mix(fromColor.g, toColor.r, t);
        }
    }

    // Block edge artifacts
    vec2 blockUV = fract(uv * blockSize);
    float edge = step(0.95, blockUV.x) + step(0.95, blockUV.y);
    result.rgb = mix(result.rgb, vec3(0.0), edge * intensity * 0.3);

    // Compression banding
    result.rgb = floor(result.rgb * (8.0 + 24.0 * (1.0 - intensity))) / (8.0 + 24.0 * (1.0 - intensity));

    return result;
}
