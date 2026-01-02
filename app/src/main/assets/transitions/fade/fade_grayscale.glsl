// @id: fade_grayscale
// @name: Film Fade
// @category: FADE
// @premium: false

// Cinematic film fade - desaturates with grain and vignette

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 transition(vec2 uv) {
    vec4 fromColor = getFromColor(uv);
    vec4 toColor = getToColor(uv);

    // Effect intensity peaks in middle
    float intensity = sin(progress * 3.14159);

    // Blend images
    vec4 mixed = mix(fromColor, toColor, progress);

    // Desaturation
    float luma = dot(mixed.rgb, vec3(0.299, 0.587, 0.114));
    vec3 gray = vec3(luma);
    mixed.rgb = mix(mixed.rgb, gray, intensity * 0.85);

    // Contrast boost when desaturated
    mixed.rgb = mix(vec3(0.5), mixed.rgb, 1.0 + intensity * 0.2);

    // Film grain
    float grain = (hash(uv * 500.0 + progress * 100.0) - 0.5) * intensity * 0.1;
    mixed.rgb += grain;

    // Vignette
    float dist = length(uv - 0.5);
    float vignette = 1.0 - dist * dist * intensity * 0.8;
    mixed.rgb *= vignette;

    // Slight sepia tint
    mixed.r += intensity * 0.03;
    mixed.b -= intensity * 0.02;

    return mixed;
}
