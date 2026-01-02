// @id: revolve_3d
// @name: 3D Revolve
// @category: THREE_D
// @premium: false

const float PI = 3.14159265359;

vec4 transition(vec2 uv) {
    float p = progress;
    // Smooth easing
    float t = p * p * (3.0 - 2.0 * p);

    // Revolving door rotates around the center vertical axis
    float angle = t * PI;
    float c = cos(angle);
    float s = sin(angle);

    vec2 pos = uv - 0.5;

    // Two door panels - left and right halves
    bool isLeftPanel = uv.x < 0.5;

    // Each panel rotates around its inner edge (center of screen)
    // localX is distance from hinge (0 at center, 0.5 at edge)
    float localX = isLeftPanel ? (0.5 - uv.x) : (uv.x - 0.5);

    // 3D transformation - rotate around the hinge
    float rotatedX = localX * abs(c);
    float rotatedZ = localX * s;

    // Perspective based on Z depth
    float fov = 2.0;
    float perspScale = fov / (fov + abs(rotatedZ) * 1.5);
    perspScale = max(perspScale, 0.3);

    // Calculate screen X position after rotation
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

    // Determine if we're seeing front or back based on Z
    // Front: Z < 0 (rotating away from viewer)
    // Back: Z > 0 (rotating toward viewer)
    bool showingFront = (isLeftPanel && s >= 0.0) || (!isLeftPanel && s >= 0.0);
    // Actually simpler: front when c > 0, back when c < 0
    showingFront = c > 0.0;

    // For the "to" image, we need to map back to original UV
    // The UV should map naturally to the source texture
    vec2 sourceUV = vec2(uv.x, screenY);

    // Lighting
    float light = 0.4 + 0.6 * abs(c);

    // Gap shadow in the middle during transition
    float gapShadow = 1.0 - smoothstep(0.0, 0.15, abs(uv.x - 0.5)) * 0.4 * abs(s);
    light *= gapShadow;

    if (showingFront) {
        // Front side - show "from" image
        vec4 color = getFromColor(sourceUV);
        color.rgb *= light;
        return color;
    } else {
        // Back side - show "to" image (correctly oriented)
        vec4 color = getToColor(sourceUV);
        color.rgb *= light;
        return color;
    }
}
