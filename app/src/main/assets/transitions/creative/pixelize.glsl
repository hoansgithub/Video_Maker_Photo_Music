// @id: pixelize
// @name: Pixelize
// @category: CREATIVE
// @premium: false

vec4 transition(vec2 uv) {
    // Pixel size peaks at midpoint
    float intensity = sin(progress * 3.14159);
    float pixelSize = mix(1.0, 50.0, intensity) / 500.0;

    // Quantize UV coordinates
    vec2 pixelUV = floor(uv / pixelSize) * pixelSize;

    return mix(getFromColor(pixelUV), getToColor(pixelUV), progress);
}
