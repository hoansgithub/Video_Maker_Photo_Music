// @id: water_drop
// @name: Water Drop
// @category: CREATIVE
// @premium: false

// Water Drop Impact - A drop falls and splashes, revealing the next image
// Phase 1: Drop falls from top (0.0-0.4)
// Phase 2: Impact splash expands (0.4-1.0)
// Distinct from ripple: has visible falling drop + splash rings

vec4 transition(vec2 uv) {
    // Clean start/end
    if (progress < 0.01) {
        return getFromColor(uv);
    }
    if (progress > 0.99) {
        return getToColor(uv);
    }

    vec2 impactPoint = vec2(0.5, 0.5);
    float dropPhase = 0.4;  // Drop falls during first 40%

    vec4 fromColor = getFromColor(uv);
    vec4 toColor = getToColor(uv);
    vec4 result = fromColor;

    // === PHASE 1: FALLING DROP (0.0 - 0.4) ===
    if (progress < dropPhase) {
        float dropProgress = progress / dropPhase;

        // Drop falls from top with acceleration (ease-in)
        float dropY = -0.1 + dropProgress * dropProgress * 0.6;
        vec2 dropPos = vec2(0.5, dropY);

        // Draw the drop (teardrop shape)
        float distToDrop = distance(uv, dropPos);
        float dropSize = 0.04 + dropProgress * 0.02;  // Grows slightly as it falls

        // Teardrop: circle + tail
        float tailStretch = max(0.0, (dropPos.y - uv.y)) * 2.0;
        float dropShape = distToDrop - dropSize * (1.0 + tailStretch * 0.5);

        // Drop is a blue-tinted overlay
        if (dropShape < 0.0) {
            float dropAlpha = smoothstep(0.0, -0.02, dropShape);
            vec3 dropColor = vec3(0.7, 0.85, 1.0);  // Light blue
            // Add highlight
            float highlight = smoothstep(0.02, 0.0, distance(uv, dropPos - vec2(0.01, 0.01)));
            dropColor += highlight * 0.3;
            result = vec4(mix(result.rgb, dropColor, dropAlpha * 0.7), 1.0);
        }
    }

    // === PHASE 2: SPLASH IMPACT (0.4 - 1.0) ===
    if (progress >= dropPhase) {
        float splashProgress = (progress - dropPhase) / (1.0 - dropPhase);
        float dist = distance(uv, impactPoint);

        // Multiple splash rings expanding outward
        float ringCount = 4.0;
        float maxRadius = splashProgress * 1.2;

        float splashEffect = 0.0;

        for (float i = 0.0; i < ringCount; i++) {
            float ringDelay = i * 0.15;
            float ringProgress = max(0.0, splashProgress - ringDelay);
            float ringRadius = ringProgress * 0.8;
            float ringWidth = 0.03 * (1.0 - ringProgress * 0.5);  // Rings get thinner

            // Ring intensity fades as it expands
            float ringIntensity = (1.0 - ringProgress) * 0.8;

            // Create ring
            float ring = smoothstep(ringRadius - ringWidth, ringRadius, dist)
                       - smoothstep(ringRadius, ringRadius + ringWidth, dist);

            splashEffect += ring * ringIntensity;
        }

        // Distortion from splash
        vec2 dir = normalize(uv - impactPoint + 0.001);
        float distortAmount = splashEffect * 0.03;
        vec2 splashUV = uv + dir * distortAmount;
        splashUV = clamp(splashUV, 0.0, 1.0);

        // Sample with distortion
        fromColor = getFromColor(splashUV);
        toColor = getToColor(splashUV);

        // Central splash crater reveals TO image
        float craterRadius = splashProgress * 0.6;
        float craterEdge = 0.08;
        float crater = smoothstep(craterRadius - craterEdge, craterRadius, dist);

        // Blend: inside crater = TO, outside = FROM with ring distortion
        result = mix(toColor, fromColor, crater);

        // Add splash foam/white at ring peaks
        float foam = splashEffect * 0.4;
        result.rgb += foam;

        // Final crossfade for smooth ending
        float finalBlend = smoothstep(0.7, 1.0, progress);
        result = mix(result, toColor, finalBlend);
    }

    return result;
}
