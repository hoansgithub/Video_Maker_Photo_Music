// @id: page_flip
// @name: Page Flip
// @category: CINEMATIC
// @premium: true

// Page flip transition - page turns from left to right
// FROM page flips away, revealing TO page underneath

vec4 transition(vec2 uv) {
    if (progress <= 0.001) return getFromColor(uv);
    if (progress >= 0.98) return getToColor(uv);

    // Smooth easing
    float t = progress * progress * (3.0 - 2.0 * progress);

    // The fold line moves from left (0.0) to right (1.0)
    float foldX = t;

    // Width of the visible curled portion
    float curlWidth = 0.15;

    // Three regions:
    // 1. Left of fold: TO image (revealed underneath)
    // 2. The fold/curl: Back of FROM page (mirrored)
    // 3. Right of curl: FROM page (still flat)

    if (uv.x < foldX - curlWidth) {
        // Region 1: TO image is revealed underneath
        return getToColor(uv);
    }
    else if (uv.x < foldX) {
        // Region 2: The curled portion (back of FROM page)
        // Mirror the FROM image - cigarette paper concept
        float localX = (foldX - uv.x) / curlWidth;  // 0 at fold, 1 at edge

        // Sample FROM image mirrored around the fold line
        float sampleX = foldX + (foldX - uv.x);
        sampleX = clamp(sampleX, 0.0, 1.0);

        return getFromColor(vec2(sampleX, uv.y));
    }
    else {
        // Region 3: FROM page still visible (flat, not yet flipped)
        return getFromColor(uv);
    }
}
