// @id: pixel_glitch
// @name: Pixel Glitch
// @category: CREATIVE
// @premium: true

// Digital corruption/pixelation glitch effect
// Blocks of pixels shift and corrupt

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 transition(vec2 uv) {
    if (progress < 0.01) return getFromColor(uv);
    if (progress > 0.99) return getToColor(uv);

    float t = progress;
    float intensity = sin(t * 3.14159);

    // Pixelation amount
    float pixelSize = mix(200.0, 20.0, intensity * intensity);
    vec2 pixelUV = floor(uv * pixelSize) / pixelSize;

    // Block-based glitch
    float blockSize = 8.0;
    vec2 block = floor(uv * blockSize);
    float blockRand = hash(block + floor(t * 10.0));

    // Horizontal shift for some blocks
    vec2 shiftedUV = uv;
    if (blockRand > 0.7) {
        float shift = (hash(block + 0.5) - 0.5) * 0.15 * intensity;
        shiftedUV.x += shift;
    }

    // Vertical shift for some blocks
    if (blockRand > 0.85) {
        float shift = (hash(block + 1.5) - 0.5) * 0.1 * intensity;
        shiftedUV.y += shift;
    }

    shiftedUV = clamp(shiftedUV, 0.0, 1.0);

    // Sample with pixelation in glitched areas
    vec2 sampleUV = mix(shiftedUV, pixelUV, intensity * 0.5);

    vec4 fromColor = getFromColor(sampleUV);
    vec4 toColor = getToColor(sampleUV);

    vec4 result = mix(fromColor, toColor, t);

    // Color corruption in random blocks
    if (blockRand > 0.9) {
        float colorShift = hash(block + 2.5);
        if (colorShift < 0.33) {
            result.rgb = result.rrr; // Red only
        } else if (colorShift < 0.66) {
            result.rgb = result.ggg; // Green only
        } else {
            result.rgb = result.bbb; // Blue only
        }
    }

    // Scanline glitch
    float scanline = step(0.5, fract(uv.y * 100.0 + t * 20.0));
    result.rgb *= 1.0 - scanline * intensity * 0.1;

    // Subtle brightness variation in random blocks (instead of harsh white/black)
    if (blockRand > 0.95) {
        float brightnessShift = (hash(block + 3.5) - 0.5) * 0.3 * intensity;
        result.rgb += brightnessShift;
    }

    // Clamp final result
    result.rgb = clamp(result.rgb, 0.0, 1.0);

    return result;
}
