// @id: tv_static
// @name: TV Static
// @category: CREATIVE
// @premium: true

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

vec4 transition(vec2 uv) {
    float p = progress;

    // Generate TV static noise
    float noise = rand(uv * 1000.0 + p * 100.0);

    // Static intensity - peaks at middle
    float intensity = sin(p * 3.14159);

    // Mix between images through static
    vec4 fromColor = getFromColor(uv);
    vec4 toColor = getToColor(uv);
    vec4 staticColor = vec4(vec3(noise), 1.0);

    // First half: from -> static, second half: static -> to
    vec4 color;
    if (p < 0.5) {
        color = mix(fromColor, staticColor, intensity);
    } else {
        color = mix(staticColor, toColor, (p - 0.5) * 2.0);
    }

    // Add scanlines
    float scanline = sin(uv.y * 400.0) * 0.1 * intensity;
    color.rgb += scanline;

    return color;
}
