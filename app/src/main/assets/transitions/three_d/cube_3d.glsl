// @id: cube_3d
// @name: 3D Cube
// @category: THREE_D
// @premium: true

const float PI = 3.14159265359;

// 3D Cube rotation transition
//
// The cube rotates around the Y-axis (vertical):
// - FROM face starts facing the camera, rotates away to the left
// - TO face starts at 90° (invisible), rotates in from the right
//
// At progress=0: FROM face fully visible (0° rotation)
// At progress=1: TO face fully visible (90° rotation complete)

vec4 transition(vec2 uv) {
    // Smooth easing for natural motion
    float t = progress * progress * (3.0 - 2.0 * progress);

    // Rotation angle: 0 to 90 degrees
    float angle = t * PI * 0.5;

    // Define cube geometry
    // cubeSize is the apparent width of each face
    // As cube rotates, FROM face shrinks (cos), TO face grows (sin)
    float fromWidth = cos(angle);  // 1.0 -> 0.0
    float toWidth = sin(angle);    // 0.0 -> 1.0

    // The "hinge" where faces meet moves from right to left
    // At t=0: hinge at x=1.0 (FROM fills screen)
    // At t=1: hinge at x=0.0 (TO fills screen)
    float hinge = fromWidth / (fromWidth + toWidth);

    // Add small epsilon to avoid division issues at extremes
    float safeFromWidth = max(fromWidth, 0.001);
    float safeToWidth = max(toWidth, 0.001);

    if (uv.x < hinge) {
        // ===== FROM FACE (rotating away) =====

        // Map screen X [0, hinge] -> texture U [0, 1]
        float localX = uv.x / hinge;

        // Perspective correction for rotating face:
        // Right edge (near hinge) is farther from camera
        // This creates the foreshortening effect
        float depth = 0.4;  // Perspective strength
        float z = 1.0 + localX * sin(angle) * depth;

        // Apply perspective to Y (vertical compression near hinge)
        float perspectiveY = 0.5 + (uv.y - 0.5) / z;

        // Clamp to valid texture coordinates
        if (perspectiveY < 0.0 || perspectiveY > 1.0) {
            return vec4(0.0, 0.0, 0.0, 1.0);
        }

        vec2 fromUV = vec2(localX, perspectiveY);
        vec4 col = getFromColor(fromUV);

        // Lighting: darken as face rotates away from camera
        // Light comes from front, so cos(angle) gives facing amount
        float light = 0.5 + 0.5 * cos(angle);
        col.rgb *= light;

        return col;
    }
    else {
        // ===== TO FACE (rotating into view) =====

        // Map screen X [hinge, 1] -> texture U [0, 1]
        float localX = (uv.x - hinge) / (1.0 - hinge);

        // Perspective correction for incoming face:
        // Left edge (near hinge) is farther from camera
        float depth = 0.4;
        float z = 1.0 + (1.0 - localX) * cos(angle) * depth;

        // Apply perspective to Y
        float perspectiveY = 0.5 + (uv.y - 0.5) / z;

        // Clamp to valid texture coordinates
        if (perspectiveY < 0.0 || perspectiveY > 1.0) {
            return vec4(0.0, 0.0, 0.0, 1.0);
        }

        vec2 toUV = vec2(localX, perspectiveY);
        vec4 col = getToColor(toUV);

        // Lighting: brighten as face rotates toward camera
        // sin(angle) gives how much the TO face is facing the camera
        float light = 0.5 + 0.5 * sin(angle);
        col.rgb *= light;

        return col;
    }
}
