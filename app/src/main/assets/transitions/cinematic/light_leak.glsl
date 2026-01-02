// @id: light_leak
// @name: Light Leak
// @category: CINEMATIC
// @premium: true

// Cinematic light leak transition
// Simulates film camera light leak with warm orange/yellow glow
// Very popular in indie films and music videos

vec4 transition(vec2 uv) {
    // Smooth easing for organic feel
    float t = progress * progress * (3.0 - 2.0 * progress);

    // Light leak originates from top-right corner
    vec2 leakCenter = vec2(1.2, -0.2);
    float dist = distance(uv, leakCenter);

    // Leak expands outward over time
    float leakRadius = t * 2.5;
    float leakEdge = smoothstep(leakRadius, leakRadius - 0.4, dist);

    // Secondary leak from bottom-left for more organic feel
    vec2 leakCenter2 = vec2(-0.3, 1.1);
    float dist2 = distance(uv, leakCenter2);
    float leakRadius2 = t * 2.0 - 0.3;
    float leakEdge2 = smoothstep(leakRadius2, leakRadius2 - 0.3, dist2);

    // Combine leaks
    float leak = max(leakEdge, leakEdge2 * 0.7);

    // Get source images
    vec4 fromColor = getFromColor(uv);
    vec4 toColor = getToColor(uv);

    // Warm light leak color (cinematic orange/amber)
    vec3 leakColor = vec3(1.0, 0.7, 0.3);

    // Phase 1: Light leak burns into FROM image
    // Phase 2: TO image emerges from the light
    float burnPhase = smoothstep(0.0, 0.5, t);
    float revealPhase = smoothstep(0.3, 0.8, t);

    // Add bloom/glow to the leak
    float bloom = leak * leak * 1.5;

    // Burn out FROM image where leak is strong
    vec3 burnedFrom = fromColor.rgb + leakColor * bloom * burnPhase * 2.0;
    burnedFrom = mix(burnedFrom, leakColor, leak * burnPhase);

    // Reveal TO image through the light
    vec3 revealedTo = toColor.rgb + leakColor * bloom * (1.0 - revealPhase) * 0.5;

    // Blend based on leak intensity and progress
    float blendFactor = leak * revealPhase;
    vec3 result = mix(burnedFrom, revealedTo, blendFactor);

    // Add final glow overlay
    float glowIntensity = sin(t * 3.14159) * 0.3;
    result += leakColor * glowIntensity * leak;

    return vec4(result, 1.0);
}
