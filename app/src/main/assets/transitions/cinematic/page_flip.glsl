// @id: page_flip
// @name: Page Flip
// @category: CINEMATIC
// @premium: true

// Page flip transition - page turns from right to left
// FROM page flips away, revealing TO page underneath

vec4 transition(vec2 uv) {
    // Smooth easing
    float t = progress * progress * (3.0 - 2.0 * progress);

    // The fold line moves from right (1.0) to left (0.0)
    float foldX = 1.0 - t;

    // Width of the visible curled portion
    float curlWidth = t * 0.5;

    // Three regions:
    // 1. Left of fold: TO image (revealed underneath)
    // 2. The fold/curl: Back of FROM page (shadow)
    // 3. Right of curl: FROM page (being lifted)

    if (uv.x < foldX) {
        // Region 1: TO image is revealed underneath
        // Sample TO image normally (no mirror)
        vec4 toColor = getToColor(uv);

        // Add slight shadow near the fold edge
        float shadowDist = foldX - uv.x;
        float shadow = smoothstep(0.0, 0.3, shadowDist);
        toColor.rgb *= 0.7 + 0.3 * shadow;

        return toColor;
    }
    else if (uv.x < foldX + curlWidth) {
        // Region 2: The curled portion (back of FROM page)
        // This shows as a darker fold/shadow area
        float localX = (uv.x - foldX) / max(curlWidth, 0.001);

        // Cylinder curl effect on Y
        float curlAngle = localX * 3.14159;
        float curlZ = sin(curlAngle);

        // Sample FROM image - the back of the page
        // Mirror X because we're seeing the back
        float sampleX = foldX + curlWidth - (uv.x - foldX);
        sampleX = clamp(sampleX, 0.0, 1.0);

        vec4 color = getFromColor(vec2(sampleX, uv.y));

        // Darken significantly - this is the back/shadow of the curl
        color.rgb *= 0.3 + 0.2 * curlZ;

        return color;
    }
    else {
        // Region 3: FROM page still visible (being lifted)
        // This part is rising up, sample FROM normally
        float liftAmount = (uv.x - foldX - curlWidth) / max(1.0 - foldX - curlWidth, 0.001);

        vec4 color = getFromColor(uv);

        // Slight lift shadow effect
        color.rgb *= 0.9 + 0.1 * liftAmount;

        return color;
    }
}
