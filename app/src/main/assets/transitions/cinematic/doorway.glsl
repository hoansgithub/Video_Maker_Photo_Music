// @id: doorway
// @name: Doorway
// @category: CINEMATIC
// @premium: false

vec4 transition(vec2 uv) {
    float p = progress;

    // Door opening from center
    float doorEdge = 0.5 - p * 0.5;

    if (uv.x < doorEdge || uv.x > 1.0 - doorEdge) {
        // Door panels (from image with perspective)
        float panelX;
        if (uv.x < 0.5) {
            panelX = uv.x / max(doorEdge * 2.0, 0.001);
        } else {
            panelX = (uv.x - (1.0 - doorEdge)) / max(doorEdge * 2.0, 0.001) + 0.5;
        }

        // Add depth shading
        vec4 color = getFromColor(vec2(panelX, uv.y));
        color.rgb *= 0.5 + 0.5 * (1.0 - p);
        return color;
    } else {
        // Through the door (to image)
        float scale = 0.8 + p * 0.2;
        vec2 toUV = (uv - 0.5) / scale + 0.5;
        return getToColor(toUV);
    }
}
