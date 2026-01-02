// @id: zoom_crossover
// @name: Zoom Crossover
// @category: BLUR
// @premium: false

// Zoom crossover - FROM zooms out while TO zooms in
// Creates a dramatic "passing through" effect
// Both images cross paths at the midpoint

vec4 transition(vec2 uv) {
    vec2 center = vec2(0.5, 0.5);

    // Smooth easing
    float t = progress * progress * (3.0 - 2.0 * progress);

    // FROM image: starts normal (scale=1), zooms OUT (scale increases)
    // Zooms out to 3x size, becoming blurry/faded
    float fromScale = 1.0 + t * 2.0;  // 1.0 -> 3.0

    // TO image: starts zoomed IN (scale=0.3), grows to normal (scale=1)
    // Starts tiny in center, grows to fill screen
    float toScale = 0.3 + t * 0.7;    // 0.3 -> 1.0

    // Calculate UVs for each image
    vec2 fromUV = (uv - center) / fromScale + center;
    vec2 toUV = (uv - center) / toScale + center;

    // Sample images (clamp to avoid edge artifacts)
    vec4 fromColor = vec4(0.0);
    vec4 toColor = vec4(0.0);

    // FROM: valid when UV is in bounds (zooming out reveals more)
    if (fromUV.x >= 0.0 && fromUV.x <= 1.0 && fromUV.y >= 0.0 && fromUV.y <= 1.0) {
        fromColor = getFromColor(fromUV);
    }

    // TO: valid when UV is in bounds (starts cropped, expands)
    if (toUV.x >= 0.0 && toUV.x <= 1.0 && toUV.y >= 0.0 && toUV.y <= 1.0) {
        toColor = getToColor(toUV);
    }

    // Fade FROM out as it zooms away
    float fromAlpha = 1.0 - smoothstep(0.0, 0.7, t);

    // Fade TO in as it zooms toward us
    float toAlpha = smoothstep(0.2, 0.8, t);

    // Blend: FROM fades out while zooming away, TO fades in while approaching
    vec3 result = fromColor.rgb * fromAlpha + toColor.rgb * toAlpha;

    // Normalize if both are visible
    float totalAlpha = fromAlpha + toAlpha;
    if (totalAlpha > 1.0) {
        result /= totalAlpha;
    }

    return vec4(result, 1.0);
}
