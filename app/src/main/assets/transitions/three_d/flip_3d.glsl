// @id: flip_3d
// @name: 3D Flip
// @category: THREE_D
// @premium: true

// 3D flip - card flips from left edge, showing TO on back
// Full perspective: objects closer to camera appear larger in both X and Y

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
    float angle = t * PI;
    float c = cos(angle);
    float s = sin(angle);

    // Projected width of the page (shrinks to 0 at 90°, then grows again)
    float projectedWidth = abs(c);

    if (c > 0.0) {
        // First half (0-90°): Front face showing FROM
        // Page covers from x=0 to x=projectedWidth
        if (uv.x <= projectedWidth && projectedWidth > 0.001) {
            float sampleX = uv.x / projectedWidth;

            // Perspective based on depth (z-scale effect)
            float z = sampleX * s;
            float fov = 2.0;
            float perspScale = fov / (fov + z * 0.6);

            // Apply perspective to BOTH X and Y
            float perspX = 0.5 + (sampleX - 0.5) / perspScale;
            float perspY = 0.5 + (uv.y - 0.5) / perspScale;

            if (perspY >= 0.0 && perspY <= 1.0 && perspX >= 0.0 && perspX <= 1.0) {
                return getFromColor(vec2(perspX, perspY));
            }
        }
        // Area not covered by page - show TO
        return getToColor(uv);
    } else {
        // Second half (90-180°): Back face showing TO
        // Page now appears from right side, growing leftward
        float pageStart = 1.0 - projectedWidth;

        if (uv.x >= pageStart && projectedWidth > 0.001) {
            float localX = (uv.x - pageStart) / projectedWidth;
            float sampleX = localX;

            // Perspective (z-scale effect)
            float z = (1.0 - localX) * (-s);
            float fov = 2.0;
            float perspScale = fov / (fov + abs(z) * 0.6);

            // Apply perspective to BOTH X and Y
            float perspX = 0.5 + (sampleX - 0.5) / perspScale;
            float perspY = 0.5 + (uv.y - 0.5) / perspScale;

            if (perspY >= 0.0 && perspY <= 1.0 && perspX >= 0.0 && perspX <= 1.0) {
                return getToColor(vec2(perspX, perspY));
            }
        }
        // Area not covered by page - show TO
        return getToColor(uv);
    }
}
