// @id: flip_3d
// @name: 3D Flip
// @category: THREE_D
// @premium: true

const float PI = 3.14159265359;

vec4 transition(vec2 uv) {
    float p = progress;
    // Smooth easing for natural motion
    float t = p * p * (3.0 - 2.0 * p);

    // Rotation angle (0 to 180 degrees) - card flips around Y axis
    float angle = t * PI;
    float c = cos(angle);
    float s = sin(angle);

    // Centered coordinates
    vec2 pos = uv - 0.5;

    // 3D card position - rotating around Y axis at center
    float x3d = pos.x * abs(c);
    float z3d = pos.x * s;

    // Perspective projection
    float fov = 2.5;
    float perspScale = fov / (fov - z3d * 0.8);

    // Ensure we don't have extreme perspective
    perspScale = clamp(perspScale, 0.5, 2.0);

    // Apply perspective to UV
    vec2 perspUV;
    perspUV.x = x3d * perspScale + 0.5;
    perspUV.y = pos.y * perspScale + 0.5;

    // Bounds check with soft edge
    if (perspUV.x < 0.0 || perspUV.x > 1.0 || perspUV.y < 0.0 || perspUV.y > 1.0) {
        return vec4(0.0, 0.0, 0.0, 1.0);
    }

    // Lighting - based on surface normal relative to light
    float light = 0.5 + 0.5 * abs(c);

    // Add subtle shadow on edges during flip
    float edgeShadow = 1.0 - smoothstep(0.4, 0.5, abs(uv.x - 0.5)) * (1.0 - abs(c)) * 0.3;
    light *= edgeShadow;

    // First half shows "from" face, second half shows "to" face
    if (c > 0.0) {
        // Front face (from image)
        vec4 color = getFromColor(perspUV);
        color.rgb *= light;
        return color;
    } else {
        // Back face (to image) - show correctly oriented (not mirrored)
        vec4 color = getToColor(perspUV);
        color.rgb *= light;
        return color;
    }
}
