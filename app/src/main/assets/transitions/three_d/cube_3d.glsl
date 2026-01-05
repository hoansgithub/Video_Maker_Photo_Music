// @id: cube_3d
// @name: 3D Cube
// @category: THREE_D
// @premium: true

// 3D Cube rotation - FROM rotates away, TO rotates in
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

    // Rotation angle: 0 to 90 degrees
    float angle = t * PI * 0.5;

    // Cube geometry: FROM face shrinks (cos), TO face grows (sin)
    float fromWidth = cos(angle);
    float toWidth = sin(angle);

    // The "hinge" where faces meet
    float hinge = fromWidth / (fromWidth + toWidth);

    if (uv.x < hinge) {
        // FROM FACE (rotating away)
        float localX = uv.x / hinge;

        // Z-depth increases toward the hinge (further = smaller)
        float z = localX * sin(angle) * 0.5;
        float perspZ = 1.0 + z;

        // Apply perspective to BOTH X and Y (z-scale effect)
        float perspectiveY = 0.5 + (uv.y - 0.5) / perspZ;
        float perspectiveX = 0.5 + (localX - 0.5) / perspZ;

        if (perspectiveY < 0.0 || perspectiveY > 1.0 || perspectiveX < 0.0 || perspectiveX > 1.0) {
            return vec4(0.0, 0.0, 0.0, 1.0);
        }

        vec2 fromUV = vec2(perspectiveX, perspectiveY);
        return getFromColor(fromUV);
    } else {
        // TO FACE (rotating into view)
        float localX = (uv.x - hinge) / (1.0 - hinge);

        // Z-depth decreases away from the hinge (closer = larger)
        float z = (1.0 - localX) * cos(angle) * 0.5;
        float perspZ = 1.0 + z;

        // Apply perspective to BOTH X and Y (z-scale effect)
        float perspectiveY = 0.5 + (uv.y - 0.5) / perspZ;
        float perspectiveX = 0.5 + (localX - 0.5) / perspZ;

        if (perspectiveY < 0.0 || perspectiveY > 1.0 || perspectiveX < 0.0 || perspectiveX > 1.0) {
            return vec4(0.0, 0.0, 0.0, 1.0);
        }

        vec2 toUV = vec2(perspectiveX, perspectiveY);
        return getToColor(toUV);
    }
}
