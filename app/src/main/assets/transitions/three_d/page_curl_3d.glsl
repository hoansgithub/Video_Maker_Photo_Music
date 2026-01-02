// @id: page_curl_3d
// @name: 3D Page Curl
// @category: THREE_D
// @premium: true

// Page roll effect - rolled part mirrors around the curl axis

const float PI = 3.14159265359;
const float cylinderRadius = 0.12;

float slowEase(float p) {
    float edge = 0.12;
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

    // Curl axis moves from left to right
    float curlX = -0.2 + t * 1.4;

    // Distance from curl axis
    float d = uv.x - curlX;

    // === Flat FROM region (right of curl) ===
    if (d > cylinderRadius) {
        return getFromColor(uv);
    }

    // === Revealed TO region (left of curl) ===
    if (d < -cylinderRadius) {
        return getToColor(uv);
    }

    // === On the roll ===
    // Normalize d to [-1, 1] range within cylinder
    float nd = d / cylinderRadius;

    // Angle on cylinder: acos gives 0 at top, PI at bottom
    // For roll: right edge (d=r) is angle 0, left edge (d=-r) is angle PI
    float theta = acos(clamp(nd, -1.0, 1.0));

    // Arc length from the right edge of cylinder
    float arcLength = theta * cylinderRadius;

    // The texture x-coordinate: start from flat edge and go along the arc
    float textureX = curlX + cylinderRadius - arcLength;

    // For the back side (theta > PI/2), mirror around the curl axis
    bool isBackSide = theta > PI * 0.5;

    float sampleX;
    if (isBackSide) {
        // Mirror: reflect textureX around curlX
        sampleX = 2.0 * curlX - textureX;
    } else {
        sampleX = textureX;
    }
    sampleX = clamp(sampleX, 0.0, 1.0);

    return getFromColor(vec2(sampleX, uv.y));
}
