// @id: fold_3d
// @name: 3D Fold
// @category: THREE_D
// @premium: true

// 3D Accordion Fold - panels fold like accordion
// Z-scale only: closer to camera = brighter

const float PI = 3.14159265359;
const float NUM_FOLDS = 4.0;

float slowEase(float p) {
    float edge = 0.1;
    if (p < edge) {
        return (p * p) / edge;
    } else if (p > 1.0 - edge) {
        float x = (p - (1.0 - edge)) / edge;
        return (1.0 - edge) + (1.0 - (1.0 - x) * (1.0 - x)) * edge;
    }
    return p;
}

vec4 transition(vec2 uv) {
    if (progress <= 0.001) return getFromColor(uv);
    if (progress >= 0.98) return getToColor(uv);

    float t = slowEase(progress);
    float foldWidth = 1.0 / NUM_FOLDS;

    // Which panel and local position
    float panelFloat = uv.x * NUM_FOLDS;
    float panelIdx = floor(panelFloat);
    float localX = fract(panelFloat);

    // Cascade timing
    float cascadeOffset = 0.12;
    float foldDuration = 0.75;

    float panelStart = panelIdx * cascadeOffset;
    float panelProgress = clamp((t - panelStart) / foldDuration, 0.0, 1.0);
    panelProgress = slowEase(panelProgress);

    // Fold angle
    float maxFoldAngle = PI * 0.85;
    float foldAngle = panelProgress * maxFoldAngle;

    float cosAngle = cos(foldAngle);
    float sinAngle = sin(foldAngle);

    // Alternating fold direction
    float isEven = step(0.5, 1.0 - mod(panelIdx, 2.0));

    // Calculate folded position
    float panelStartX = panelIdx * foldWidth;
    float distFromHinge = mix(1.0 - localX, localX, isEven);
    float foldedZ = distFromHinge * foldWidth * sinAngle;

    float hingeX = mix(panelStartX + foldWidth, panelStartX, isEven);
    float foldDirection = mix(-1.0, 1.0, isEven);
    float foldedX = hingeX + foldDirection * distFromHinge * foldWidth * cosAngle;

    // Perspective
    float focalLength = 3.0;
    float perspectiveScale = focalLength / (focalLength + abs(foldedZ) * 0.8);
    float perspectiveY = (uv.y - 0.5) * perspectiveScale + 0.5;

    // Smooth start
    vec2 foldedUV = vec2(foldedX, perspectiveY);
    float uvBlend = smoothstep(0.0, 0.08, t);
    foldedUV = mix(uv, foldedUV, uvBlend);

    vec2 sampleUV = clamp(foldedUV, 0.001, 0.999);
    float inBounds = step(0.0, foldedUV.x) * step(foldedUV.x, 1.0) *
                     step(0.0, foldedUV.y) * step(foldedUV.y, 1.0);

    // Sample images
    vec4 fromColor = getFromColor(sampleUV);
    vec4 toColor = getToColor(uv);

    // Blend to destination
    float colorBlend = smoothstep(0.5, 0.9, t);
    vec4 foldedColor = mix(fromColor, toColor, colorBlend);

    // Bounds check
    vec4 finalColor = mix(toColor, foldedColor, inBounds);

    // Smooth end
    float endFade = smoothstep(0.88, 0.98, t);
    finalColor = mix(finalColor, toColor, endFade);

    return finalColor;
}
