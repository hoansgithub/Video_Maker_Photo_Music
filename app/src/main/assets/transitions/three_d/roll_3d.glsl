// @id: roll_3d
// @name: 3D Roll
// @category: THREE_D
// @premium: false

// Roll effect - rolled part mirrors around the roll axis
// Based on cylinder geometry with z-scale only

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

    // Roll axis moves from bottom to top
    float rollY = -0.2 + t * 1.4;

    // Distance from roll axis
    float d = uv.y - rollY;

    // === Flat FROM region (above roll) ===
    if (d > cylinderRadius) {
        return getFromColor(uv);
    }

    // === Revealed TO region (below roll) ===
    if (d < -cylinderRadius) {
        return getToColor(uv);
    }

    // === On the roll ===
    // Normalize d to [-1, 1] range within cylinder
    float nd = d / cylinderRadius;

    // Angle on cylinder: acos gives 0 at top, PI at bottom
    float theta = acos(clamp(nd, -1.0, 1.0));

    // Arc length from the top edge of cylinder
    float arcLength = theta * cylinderRadius;

    // The texture y-coordinate: start from flat edge and go along the arc
    float textureY = rollY + cylinderRadius - arcLength;

    // For the back side (theta > PI/2), mirror around the roll axis
    bool isBackSide = theta > PI * 0.5;

    float sampleY;
    if (isBackSide) {
        // Mirror: reflect textureY around rollY
        sampleY = 2.0 * rollY - textureY;
    } else {
        sampleY = textureY;
    }
    sampleY = clamp(sampleY, 0.0, 1.0);

    return getFromColor(vec2(uv.x, sampleY));
}
