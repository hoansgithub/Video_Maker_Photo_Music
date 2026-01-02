// @id: roll_3d
// @name: 3D Roll
// @category: THREE_D
// @premium: false

const float PI = 3.14159265359;

vec4 transition(vec2 uv) {
    float p = progress;
    // Smooth easing
    float t = p * p * (3.0 - 2.0 * p);

    // Cylinder parameters - rolls up from bottom
    float radius = 0.06;
    float cylWidth = radius * PI;

    // Roll position moves from below screen to above screen
    // Start below (negative) so roll enters from bottom
    // End above 1.0 so roll exits at top
    float rollPos = -cylWidth + t * (1.0 + cylWidth * 2.0);

    // Distance from roll line (bottom edge of cylinder)
    float dist = uv.y - rollPos;

    // ZONE 1: Above the cylinder - still showing "from" image
    if (dist > cylWidth) {
        return getFromColor(uv);
    }

    // ZONE 2: Below the cylinder - show "to" image (revealed area)
    if (dist < 0.0) {
        // Soft shadow near the roll
        float shadow = 1.0 - smoothstep(-0.15, 0.0, dist) * 0.35;
        vec4 col = getToColor(uv);
        col.rgb *= shadow;
        return col;
    }

    // ZONE 3: On the cylinder surface (0 <= dist <= cylWidth)
    float angle = (dist / cylWidth) * PI;

    // 3D cylinder coordinates
    float cylY = sin(angle) * radius;
    float cylZ = (1.0 - cos(angle)) * radius;

    // Perspective
    float fov = 2.5;
    float perspScale = fov / (fov + cylZ);

    // Source Y on the unrolled page - maps cylinder surface back to flat image
    float srcY = rollPos + cylY;

    // Apply perspective to X for 3D effect
    float xOffset = (uv.x - 0.5) * cylZ * 0.2;
    vec2 srcUV = vec2(clamp(uv.x - xOffset, 0.0, 1.0), clamp(srcY, 0.0, 1.0));

    // Lighting based on surface normal
    float light = 0.4 + 0.6 * cos(angle - PI * 0.4);
    light = max(light, 0.35);

    // Front of cylinder (facing viewer) shows "from" image
    // Back of cylinder (curling under) shows darkened "from"
    if (angle < PI * 0.55) {
        // Front face - normal "from" image
        vec4 color = getFromColor(srcUV);
        color.rgb *= light;
        return color;
    } else {
        // Back face - the underside of the curling page
        // Show with paper-like appearance
        float backLight = 0.3 + 0.25 * cos(angle - PI);
        vec4 color = getFromColor(srcUV);
        color.rgb *= backLight;
        // Add slight paper tint
        color.rgb = mix(color.rgb, vec3(0.92, 0.90, 0.86), 0.35);
        return color;
    }
}
