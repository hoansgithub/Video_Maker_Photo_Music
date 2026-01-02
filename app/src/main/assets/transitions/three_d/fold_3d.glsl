// @id: fold_3d
// @name: 3D Fold
// @category: THREE_D
// @premium: true

const float PI = 3.14159265359;

vec4 transition(vec2 uv) {
    float p = progress;
    // Smooth easing
    float t = p * p * (3.0 - 2.0 * p);

    // Accordion fold with 4 panels
    float numFolds = 4.0;
    float foldWidth = 1.0 / numFolds;

    // Determine which panel we're in
    float panelIdx = floor(uv.x * numFolds);
    float localX = fract(uv.x * numFolds);

    // Each panel folds at different times (cascade effect)
    float panelDelay = panelIdx / numFolds * 0.5;
    float panelProgress = clamp((t - panelDelay) / (1.0 - panelDelay * 0.8), 0.0, 1.0);

    // Fold angle - even panels fold forward, odd panels fold backward
    float isEven = mod(panelIdx, 2.0);
    float foldAngle = panelProgress * PI * 0.5;

    // 3D coordinates of the fold
    float c = cos(foldAngle);
    float s = sin(foldAngle);

    // Calculate where this point on the panel maps to
    float foldedX;
    float foldedZ;
    float panelStart = panelIdx * foldWidth;

    if (isEven < 0.5) {
        // Even panels: hinge on left, fold right edge toward camera
        foldedX = panelStart + localX * foldWidth * c;
        foldedZ = localX * foldWidth * s;
    } else {
        // Odd panels: hinge on right, fold left edge toward camera
        foldedX = panelStart + foldWidth - (1.0 - localX) * foldWidth * c;
        foldedZ = (1.0 - localX) * foldWidth * s;
    }

    // Perspective projection
    float fov = 2.0;
    float depth = fov / (fov + foldedZ);

    // Calculate final UV
    vec2 foldUV;
    foldUV.x = foldedX;
    foldUV.y = (uv.y - 0.5) * depth + 0.5;

    // Bounds check
    if (foldUV.x < 0.0 || foldUV.x > 1.0 || foldUV.y < 0.0 || foldUV.y > 1.0) {
        return mix(getFromColor(uv), getToColor(uv), t);
    }

    // Lighting - panels facing camera are brighter
    float light = 0.4 + 0.6 * c;

    // Blend from to image based on overall progress
    vec4 fromCol = getFromColor(foldUV);
    vec4 toCol = getToColor(foldUV);
    vec4 color = mix(fromCol, toCol, t);
    color.rgb *= light;

    return color;
}
