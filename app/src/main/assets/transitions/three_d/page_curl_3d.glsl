// @id: page_curl_3d
// @name: 3D Page Curl
// @category: THREE_D
// @premium: true

const float PI = 3.14159265359;

vec4 transition(vec2 uv) {
    float p = progress;
    // Smooth easing
    float t = p * p * (3.0 - 2.0 * p);

    // Curl parameters
    float radius = 0.12;
    float curlWidth = radius * PI;

    // Curl position moves from right to left
    float curlPos = 1.2 - t * 1.4;

    // Distance from curl line
    float dist = uv.x - curlPos;

    if (dist < -0.01) {
        // Behind curl - show "to" image with slight shadow near curl
        float shadow = 1.0 - smoothstep(-0.15, -0.01, dist) * 0.3 * (1.0 - t);
        vec4 col = getToColor(uv);
        col.rgb *= shadow;
        return col;
    }

    if (dist > curlWidth + 0.01) {
        // Not yet curled - show "from" image
        return getFromColor(uv);
    }

    // In the curl zone - calculate 3D cylinder surface
    float angle = clamp(dist / radius, 0.0, PI);

    // Point on cylinder surface
    float cylX = sin(angle) * radius;
    float cylZ = (1.0 - cos(angle)) * radius;

    // Perspective
    float fov = 2.0;
    float perspScale = fov / (fov + cylZ);

    // Calculate source UV on the page
    float srcX = curlPos + cylX;

    // Vertical perspective (page curves slightly in Y too)
    float yOffset = (uv.y - 0.5) * (1.0 - perspScale * 0.8);

    vec2 srcUV = vec2(srcX, uv.y - yOffset * cylZ * 2.0);

    // Bounds check
    if (srcUV.x < 0.0 || srcUV.x > 1.0 || srcUV.y < 0.0 || srcUV.y > 1.0) {
        return getToColor(uv);
    }

    // Lighting - normal points outward from cylinder
    float normalAngle = angle - PI * 0.5;
    float light = 0.5 + 0.5 * cos(normalAngle);

    // Determine if showing front or back of page
    if (angle < PI * 0.5) {
        // Front face of page
        vec4 color = getFromColor(srcUV);
        color.rgb *= light;
        return color;
    } else {
        // Back face - slightly darker, show "to" through curl
        float backLight = 0.3 + 0.4 * cos(angle - PI);
        vec4 color = getFromColor(vec2(1.0 - srcUV.x, srcUV.y));
        color.rgb *= backLight;
        // Blend with a paper-like tint
        color.rgb = mix(color.rgb, vec3(0.95, 0.93, 0.88), 0.3);
        return color;
    }
}
