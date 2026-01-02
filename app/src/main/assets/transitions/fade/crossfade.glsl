// @id: fade
// @name: Crossfade
// @category: FADE
// @premium: false

// Classic smooth crossfade with subtle brightness boost

vec4 transition(vec2 uv) {
    vec4 fromColor = getFromColor(uv);
    vec4 toColor = getToColor(uv);

    // Smooth easing
    float t = progress * progress * (3.0 - 2.0 * progress);

    // Subtle brightness boost in middle to prevent muddy blend
    float boost = sin(progress * 3.14159) * 0.08;

    vec4 result = mix(fromColor, toColor, t);
    result.rgb += boost;

    return result;
}
