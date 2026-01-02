// @id: mosaic_tiles
// @name: Mosaic Tiles
// @category: CREATIVE
// @premium: false

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

vec4 transition(vec2 uv) {
    float p = progress;

    // Tile grid
    float tileCount = 8.0;
    vec2 tile = floor(uv * tileCount);
    vec2 tileUV = fract(uv * tileCount);

    // Random delay per tile
    float delay = rand(tile) * 0.5;
    float tileProgress = clamp((p - delay) / (1.0 - delay), 0.0, 1.0);

    // Tile flip effect
    float flip = tileProgress * 3.14159;

    if (flip < 1.5708) {
        // First half - showing from
        float scale = cos(flip);
        vec2 scaledUV = (tileUV - 0.5) * vec2(scale, 1.0) + 0.5;
        vec2 finalUV = (tile + scaledUV) / tileCount;
        return getFromColor(finalUV);
    } else {
        // Second half - showing to
        float scale = -cos(flip);
        vec2 scaledUV = (tileUV - 0.5) * vec2(scale, 1.0) + 0.5;
        vec2 finalUV = (tile + scaledUV) / tileCount;
        return getToColor(finalUV);
    }
}
