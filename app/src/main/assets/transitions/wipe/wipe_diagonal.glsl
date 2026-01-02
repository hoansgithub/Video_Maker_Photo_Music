// @id: wipe_diagonal
// @name: Wipe Diagonal
// @category: WIPE
// @premium: false

// Diagonal wipe from top-left to bottom-right
// FROM image visible at start, TO image revealed diagonally

vec4 transition(vec2 uv) {
    // Clean start/end
    if (progress < 0.01) {
        return getFromColor(uv);
    }
    if (progress > 0.99) {
        return getToColor(uv);
    }

    // Diagonal position: 0 at top-left, 1 at bottom-right
    float diagonal = (uv.x + uv.y) * 0.5;

    // Soft edge width
    float softness = 0.08;

    // Wipe edge moves from 0 to 1 as progress goes 0 to 1
    float edge = progress;

    // Mask: 0 where FROM should show, 1 where TO should show
    // TO is revealed from top-left corner (where diagonal is small)
    float mask = smoothstep(edge - softness, edge + softness, diagonal);

    // Invert: we want TO to appear where diagonal < edge
    mask = 1.0 - mask;

    return mix(getFromColor(uv), getToColor(uv), mask);
}
