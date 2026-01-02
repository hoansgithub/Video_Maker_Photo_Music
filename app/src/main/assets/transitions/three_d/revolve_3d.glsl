// @id: revolve_3d
// @name: 3D Revolve
// @category: THREE_D
// @premium: false

// 3D revolving door - panels rotate around center
// Z-scale only: closer to camera = brighter

const float PI = 3.14159265359;

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

    // Revolving door rotates around the center vertical axis
    float angle = t * PI;
    float c = cos(angle);
    float s = sin(angle);

    vec2 pos = uv - 0.5;

    // Two door panels - left and right halves
    bool isLeftPanel = uv.x < 0.5;

    // Each panel rotates around its inner edge (center of screen)
    float localX = isLeftPanel ? (0.5 - uv.x) : (uv.x - 0.5);

    // 3D transformation
    float rotatedX = localX * abs(c);
    float rotatedZ = localX * s;

    // Perspective based on Z depth
    float fov = 2.0;
    float perspScale = fov / (fov + abs(rotatedZ) * 1.5);
    perspScale = max(perspScale, 0.3);

    float screenX;
    if (isLeftPanel) {
        screenX = 0.5 - rotatedX * perspScale;
    } else {
        screenX = 0.5 + rotatedX * perspScale;
    }

    float screenY = pos.y * perspScale + 0.5;

    // Bounds check
    if (screenX < 0.0 || screenX > 1.0 || screenY < 0.0 || screenY > 1.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }

    vec2 sourceUV = vec2(uv.x, screenY);

    // Front when c > 0, back when c < 0
    if (c > 0.0) {
        return getFromColor(sourceUV);
    } else {
        return getToColor(sourceUV);
    }
}
