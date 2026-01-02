// @id: film_burn
// @name: Film Burn
// @category: CINEMATIC
// @premium: true

// Film burn transition - burns holes through FROM image revealing TO behind
// Simulates old film projector catching fire

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p);
        p *= 2.0;
        amplitude *= 0.5;
    }
    return value;
}

vec4 transition(vec2 uv) {
    // progress is 0.0 to 1.0 (percentage of transition time)
    // Use smooth easing for natural feel
    float t = progress * progress * (3.0 - 2.0 * progress);

    // Create organic burn pattern using fractal noise
    // Burns start from hot spots and spread outward
    vec2 burnCenter1 = vec2(0.75, 0.25);
    vec2 burnCenter2 = vec2(0.15, 0.85);
    vec2 burnCenter3 = vec2(0.5, 0.5);  // Center burn starts later

    float dist1 = length(uv - burnCenter1);
    float dist2 = length(uv - burnCenter2);
    float dist3 = length(uv - burnCenter3);

    // Fractal noise for organic, irregular burn edges
    float n = fbm(uv * 4.0 + t * 0.3);

    // Calculate max distance for normalization (corners are ~0.7 from center)
    float maxDist = 1.0;

    // Burn threshold grows with progress
    // At t=0: threshold=0 (no burn)
    // At t=1: threshold=maxDist (everything burned)
    // The 1.1 multiplier ensures complete burn at 100%
    float burnThreshold = t * maxDist * 1.1;

    // Each burn point expands at slightly different rates
    // Noise creates irregular edges
    float edge1 = dist1 + n * 0.25;
    float edge2 = dist2 + n * 0.25;
    float edge3 = dist3 + n * 0.2 + 0.15;  // Center starts later

    // Burn through calculation - wider transition band for smoother edges
    float burn1 = smoothstep(burnThreshold, burnThreshold - 0.2, edge1);
    float burn2 = smoothstep(burnThreshold, burnThreshold - 0.2, edge2);
    float burn3 = smoothstep(burnThreshold, burnThreshold - 0.15, edge3);

    // Combine all burn areas
    float burnedThrough = max(max(burn1, burn2), burn3);

    // Edge detection for the glowing burning edge
    float edgeWidth = 0.12;
    float burnEdge1 = smoothstep(burnThreshold + edgeWidth, burnThreshold, edge1)
                    - smoothstep(burnThreshold, burnThreshold - 0.08, edge1);
    float burnEdge2 = smoothstep(burnThreshold + edgeWidth, burnThreshold, edge2)
                    - smoothstep(burnThreshold, burnThreshold - 0.08, edge2);
    float burnEdge3 = smoothstep(burnThreshold + edgeWidth * 0.8, burnThreshold, edge3)
                    - smoothstep(burnThreshold, burnThreshold - 0.06, edge3);
    float burnEdge = max(max(burnEdge1, burnEdge2), burnEdge3);

    // Get source colors
    vec4 fromColor = getFromColor(uv);
    vec4 toColor = getToColor(uv);

    // Burn edge colors - bright orange/yellow at edge, darker red behind
    vec3 hotEdge = vec3(1.0, 0.9, 0.3);   // Bright yellow-orange
    vec3 burnColor = vec3(1.0, 0.4, 0.1); // Orange-red

    // Start with FROM image
    vec3 result = fromColor.rgb;

    // Add burn edge glow to FROM image (before it burns through)
    float edgeGlow = burnEdge * (1.0 - burnedThrough);
    result = mix(result, burnColor, edgeGlow * 0.7);
    result = mix(result, hotEdge, edgeGlow * edgeGlow * 0.8);

    // Darken the FROM image near the burn (heat damage)
    float heatDamage = smoothstep(0.5, 0.0, burnEdge) * burnEdge * 0.5;
    result *= 1.0 - heatDamage;

    // Reveal TO image through burned holes
    result = mix(result, toColor.rgb, burnedThrough);

    // Add slight glow around the revealed area
    float revealGlow = burnedThrough * (1.0 - burnedThrough) * 2.0;
    result += hotEdge * revealGlow * 0.2;

    return vec4(result, 1.0);
}
