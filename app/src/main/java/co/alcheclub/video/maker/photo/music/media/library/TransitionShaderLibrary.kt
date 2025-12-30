package co.alcheclub.video.maker.photo.music.media.library

import co.alcheclub.video.maker.photo.music.domain.model.Transition
import co.alcheclub.video.maker.photo.music.domain.model.TransitionCategory

/**
 * TransitionShaderLibrary - GLSL shaders for video transitions
 *
 * Based on GL Transitions specification (https://gl-transitions.com/)
 * Each shader implements a transition(vec2 uv) function that blends
 * between getFromColor(uv) and getToColor(uv) based on progress (0.0-1.0)
 *
 * Shader Requirements:
 * - progress: float uniform (0.0 = from, 1.0 = to)
 * - ratio: float uniform (width/height)
 * - getFromColor(vec2 uv): returns source texture color
 * - getToColor(vec2 uv): returns destination texture color
 */
object TransitionShaderLibrary {

    // ============================================
    // FADE TRANSITIONS
    // ============================================

    private val fadeTransition = Transition(
        id = "fade",
        name = "Crossfade",
        category = TransitionCategory.FADE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                return mix(getFromColor(uv), getToColor(uv), progress);
            }
        """.trimIndent()
    )

    private val fadeColorTransition = Transition(
        id = "fade_color",
        name = "Fade Through Color",
        category = TransitionCategory.FADE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                float p = progress * 2.0;
                if (p < 1.0) {
                    return mix(getFromColor(uv), vec4(fadeColor, 1.0), p);
                } else {
                    return mix(vec4(fadeColor, 1.0), getToColor(uv), p - 1.0);
                }
            }
        """.trimIndent()
    )

    private val fadeGrayscaleTransition = Transition(
        id = "fade_grayscale",
        name = "Fade Grayscale",
        category = TransitionCategory.FADE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                vec4 fromColor = getFromColor(uv);
                vec4 toColor = getToColor(uv);

                // Convert to grayscale at midpoint
                float intensity = 1.0 - abs(progress - 0.5) * 2.0;

                vec4 mixed = mix(fromColor, toColor, progress);
                float gray = dot(mixed.rgb, vec3(0.299, 0.587, 0.114));
                vec3 grayColor = vec3(gray);

                return vec4(mix(mixed.rgb, grayColor, intensity * 0.7), mixed.a);
            }
        """.trimIndent()
    )

    // ============================================
    // SLIDE TRANSITIONS
    // ============================================

    private val slideLeftTransition = Transition(
        id = "slide_left",
        name = "Slide Left",
        category = TransitionCategory.SLIDE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                vec2 fromUV = uv + vec2(progress, 0.0);
                vec2 toUV = uv + vec2(progress - 1.0, 0.0);

                if (uv.x < 1.0 - progress) {
                    return getFromColor(fromUV);
                } else {
                    return getToColor(toUV);
                }
            }
        """.trimIndent()
    )

    private val slideRightTransition = Transition(
        id = "slide_right",
        name = "Slide Right",
        category = TransitionCategory.SLIDE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                vec2 fromUV = uv - vec2(progress, 0.0);
                vec2 toUV = uv - vec2(progress - 1.0, 0.0);

                if (uv.x > progress) {
                    return getFromColor(fromUV);
                } else {
                    return getToColor(toUV);
                }
            }
        """.trimIndent()
    )

    private val slideUpTransition = Transition(
        id = "slide_up",
        name = "Slide Up",
        category = TransitionCategory.SLIDE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                vec2 fromUV = uv + vec2(0.0, progress);
                vec2 toUV = uv + vec2(0.0, progress - 1.0);

                if (uv.y < 1.0 - progress) {
                    return getFromColor(fromUV);
                } else {
                    return getToColor(toUV);
                }
            }
        """.trimIndent()
    )

    private val slideDownTransition = Transition(
        id = "slide_down",
        name = "Slide Down",
        category = TransitionCategory.SLIDE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                vec2 fromUV = uv - vec2(0.0, progress);
                vec2 toUV = uv - vec2(0.0, progress - 1.0);

                if (uv.y > progress) {
                    return getFromColor(fromUV);
                } else {
                    return getToColor(toUV);
                }
            }
        """.trimIndent()
    )

    // ============================================
    // WIPE TRANSITIONS
    // ============================================

    private val wipeLeftTransition = Transition(
        id = "wipe_left",
        name = "Wipe Left",
        category = TransitionCategory.WIPE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                float edge = progress * (1.0 + smoothness) - smoothness;
                float mask = smoothstep(edge, edge + smoothness, 1.0 - uv.x);
                return mix(getToColor(uv), getFromColor(uv), mask);
            }
        """.trimIndent()
    )

    private val wipeRightTransition = Transition(
        id = "wipe_right",
        name = "Wipe Right",
        category = TransitionCategory.WIPE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                float edge = progress * (1.0 + smoothness) - smoothness;
                float mask = smoothstep(edge, edge + smoothness, uv.x);
                return mix(getToColor(uv), getFromColor(uv), mask);
            }
        """.trimIndent()
    )

    private val wipeDiagonalTransition = Transition(
        id = "wipe_diagonal",
        name = "Wipe Diagonal",
        category = TransitionCategory.WIPE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                float diagonal = (uv.x + uv.y) * 0.5;
                float s = 0.1; // smoothness for diagonal
                float edge = progress * (1.0 + s * 2.0) - s;
                float mask = smoothstep(edge, edge + s, diagonal);
                return mix(getFromColor(uv), getToColor(uv), mask);
            }
        """.trimIndent()
    )

    private val blindsTransition = Transition(
        id = "blinds",
        name = "Blinds",
        category = TransitionCategory.WIPE,
        shaderCode = """
            uniform float count; // = 10.0

            vec4 transition(vec2 uv) {
                float blindPos = fract(uv.x * count);
                float threshold = progress;

                if (blindPos < threshold) {
                    return getToColor(uv);
                } else {
                    return getFromColor(uv);
                }
            }
        """.trimIndent()
    )

    // ============================================
    // ZOOM TRANSITIONS
    // ============================================

    private val zoomInTransition = Transition(
        id = "zoom_in",
        name = "Zoom In",
        category = TransitionCategory.ZOOM,
        shaderCode = """
            vec4 transition(vec2 uv) {
                float scale = 1.0 + progress * 0.5;
                vec2 center = vec2(0.5, 0.5);
                vec2 fromUV = (uv - center) / scale + center;

                vec4 fromColor = getFromColor(fromUV);
                vec4 toColor = getToColor(uv);

                // Fade as we zoom
                float fade = smoothstep(0.3, 1.0, progress);
                return mix(fromColor, toColor, fade);
            }
        """.trimIndent()
    )

    private val zoomOutTransition = Transition(
        id = "zoom_out",
        name = "Zoom Out",
        category = TransitionCategory.ZOOM,
        shaderCode = """
            vec4 transition(vec2 uv) {
                float scale = 1.5 - progress * 0.5;
                vec2 center = vec2(0.5, 0.5);
                vec2 toUV = (uv - center) * scale + center;

                vec4 fromColor = getFromColor(uv);
                vec4 toColor = getToColor(toUV);

                // Fade as we zoom
                float fade = smoothstep(0.0, 0.7, progress);
                return mix(fromColor, toColor, fade);
            }
        """.trimIndent()
    )

    private val zoomRotateTransition = Transition(
        id = "zoom_rotate",
        name = "Zoom Rotate",
        category = TransitionCategory.ZOOM,
        shaderCode = """
            vec4 transition(vec2 uv) {
                float angle = progress * 3.14159 * 0.5;
                float scale = 1.0 + progress * 0.3;
                vec2 center = vec2(0.5, 0.5);

                vec2 offset = uv - center;
                float c = cos(angle);
                float s = sin(angle);
                vec2 rotated = vec2(offset.x * c - offset.y * s, offset.x * s + offset.y * c);
                vec2 fromUV = rotated / scale + center;

                vec4 fromColor = getFromColor(fromUV);
                vec4 toColor = getToColor(uv);

                return mix(fromColor, toColor, smoothstep(0.3, 0.9, progress));
            }
        """.trimIndent()
    )

    // ============================================
    // ROTATE TRANSITIONS
    // ============================================

    private val rotateTransition = Transition(
        id = "rotate",
        name = "Rotate",
        category = TransitionCategory.ROTATE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                float angle = progress * 3.14159;
                vec2 center = vec2(0.5, 0.5);
                vec2 offset = uv - center;

                float c = cos(angle);
                float s = sin(angle);
                vec2 rotated = vec2(offset.x * c - offset.y * s, offset.x * s + offset.y * c);
                vec2 fromUV = rotated + center;

                // Clamp and blend
                if (fromUV.x < 0.0 || fromUV.x > 1.0 || fromUV.y < 0.0 || fromUV.y > 1.0) {
                    return getToColor(uv);
                }
                return mix(getFromColor(fromUV), getToColor(uv), progress);
            }
        """.trimIndent()
    )

    private val flipHorizontalTransition = Transition(
        id = "flip_horizontal",
        name = "Flip Horizontal",
        category = TransitionCategory.ROTATE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                float p = progress * 2.0;

                if (p < 1.0) {
                    // First half: scale from image down to line
                    float scale = 1.0 - p;
                    vec2 fromUV = vec2((uv.x - 0.5) / max(scale, 0.001) + 0.5, uv.y);
                    if (fromUV.x < 0.0 || fromUV.x > 1.0) {
                        return vec4(0.0, 0.0, 0.0, 1.0);
                    }
                    return getFromColor(fromUV);
                } else {
                    // Second half: scale to image up from line
                    float scale = p - 1.0;
                    vec2 toUV = vec2((uv.x - 0.5) / max(scale, 0.001) + 0.5, uv.y);
                    if (toUV.x < 0.0 || toUV.x > 1.0) {
                        return vec4(0.0, 0.0, 0.0, 1.0);
                    }
                    return getToColor(toUV);
                }
            }
        """.trimIndent()
    )

    private val flipVerticalTransition = Transition(
        id = "flip_vertical",
        name = "Flip Vertical",
        category = TransitionCategory.ROTATE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                float p = progress * 2.0;

                if (p < 1.0) {
                    float scale = 1.0 - p;
                    vec2 fromUV = vec2(uv.x, (uv.y - 0.5) / max(scale, 0.001) + 0.5);
                    if (fromUV.y < 0.0 || fromUV.y > 1.0) {
                        return vec4(0.0, 0.0, 0.0, 1.0);
                    }
                    return getFromColor(fromUV);
                } else {
                    float scale = p - 1.0;
                    vec2 toUV = vec2(uv.x, (uv.y - 0.5) / max(scale, 0.001) + 0.5);
                    if (toUV.y < 0.0 || toUV.y > 1.0) {
                        return vec4(0.0, 0.0, 0.0, 1.0);
                    }
                    return getToColor(toUV);
                }
            }
        """.trimIndent()
    )

    // ============================================
    // BLUR TRANSITIONS
    // ============================================

    private val blurTransition = Transition(
        id = "blur",
        name = "Blur",
        category = TransitionCategory.BLUR,
        shaderCode = """
            vec4 transition(vec2 uv) {
                // Blur intensity peaks at midpoint
                float blurAmount = sin(progress * 3.14159) * 0.03;

                vec4 fromColor = vec4(0.0);
                vec4 toColor = vec4(0.0);

                // Simple box blur with 9 samples
                for (float x = -1.0; x <= 1.0; x += 1.0) {
                    for (float y = -1.0; y <= 1.0; y += 1.0) {
                        vec2 offset = vec2(x, y) * blurAmount;
                        fromColor += getFromColor(uv + offset);
                        toColor += getToColor(uv + offset);
                    }
                }
                fromColor /= 9.0;
                toColor /= 9.0;

                return mix(fromColor, toColor, progress);
            }
        """.trimIndent()
    )

    private val directionalBlurTransition = Transition(
        id = "directional_blur",
        name = "Directional Blur",
        category = TransitionCategory.BLUR,
        shaderCode = """
            uniform vec2 direction; // = vec2(1.0, 0.0)

            vec4 transition(vec2 uv) {
                float blurAmount = sin(progress * 3.14159) * 0.05;
                vec2 dir = normalize(direction) * blurAmount;

                vec4 color = vec4(0.0);
                float total = 0.0;

                for (float i = -4.0; i <= 4.0; i += 1.0) {
                    float weight = 1.0 - abs(i) / 5.0;
                    vec2 offset = dir * i;
                    vec4 sample = mix(getFromColor(uv + offset), getToColor(uv + offset), progress);
                    color += sample * weight;
                    total += weight;
                }

                return color / total;
            }
        """.trimIndent()
    )

    // ============================================
    // GEOMETRIC TRANSITIONS
    // ============================================

    private val circleTransition = Transition(
        id = "circle",
        name = "Circle Reveal",
        category = TransitionCategory.GEOMETRIC,
        shaderCode = """
            vec4 transition(vec2 uv) {
                vec2 center = vec2(0.5, 0.5);
                float dist = distance(uv, center);
                // Ease-in curve for radius: starts small/slow, accelerates
                float easedProgress = progress * progress;
                float radius = easedProgress * 1.0; // Max radius ~1.0 covers corners

                float edge = smoothstep(radius - smoothness, radius, dist);
                return mix(getToColor(uv), getFromColor(uv), edge);
            }
        """.trimIndent()
    )

    private val diamondTransition = Transition(
        id = "diamond",
        name = "Diamond",
        category = TransitionCategory.GEOMETRIC,
        shaderCode = """
            vec4 transition(vec2 uv) {
                vec2 center = vec2(0.5, 0.5);
                vec2 offset = abs(uv - center);
                float dist = offset.x + offset.y; // Manhattan distance
                // Ease-in curve: starts small/slow, accelerates
                float easedProgress = progress * progress;
                float size = easedProgress * 1.2;

                float edge = smoothstep(size - smoothness, size, dist);
                return mix(getToColor(uv), getFromColor(uv), edge);
            }
        """.trimIndent()
    )

    private val heartTransition = Transition(
        id = "heart",
        name = "Heart",
        category = TransitionCategory.GEOMETRIC,
        isPremium = true,
        shaderCode = """
            vec4 transition(vec2 uv) {
                vec2 p = (uv - 0.5) * 2.0;
                p.y -= 0.3;

                // Heart shape formula
                float a = atan(p.x, p.y) / 3.14159;
                float r = length(p);
                float h = abs(a);
                float d = (13.0 * h - 22.0 * h * h + 10.0 * h * h * h) / (6.0 - 5.0 * h);

                float heartDist = r - d * 0.5;
                // Ease-in curve for size: starts small/slow
                float easedProgress = progress * progress;
                float size = (1.0 - easedProgress) * 1.2;

                if (heartDist < size) {
                    return getFromColor(uv);
                } else {
                    return getToColor(uv);
                }
            }
        """.trimIndent()
    )

    private val starTransition = Transition(
        id = "star",
        name = "Star",
        category = TransitionCategory.GEOMETRIC,
        isPremium = true,
        shaderCode = """
            vec4 transition(vec2 uv) {
                vec2 center = vec2(0.5, 0.5);
                vec2 p = uv - center;
                float angle = atan(p.y, p.x);
                float dist = length(p);

                // Star shape with 5 points
                float star = cos(angle * 5.0) * 0.3 + 0.7;
                // Ease-in curve: starts small/slow, accelerates
                float easedProgress = progress * progress;
                float radius = easedProgress * star * 1.0;

                if (dist < radius) {
                    return getToColor(uv);
                } else {
                    return getFromColor(uv);
                }
            }
        """.trimIndent()
    )

    // ============================================
    // CREATIVE TRANSITIONS
    // ============================================

    private val pixelizeTransition = Transition(
        id = "pixelize",
        name = "Pixelize",
        category = TransitionCategory.CREATIVE,
        shaderCode = """
            vec4 transition(vec2 uv) {
                // Pixel size peaks at midpoint
                float intensity = sin(progress * 3.14159);
                float pixelSize = mix(1.0, 50.0, intensity) / 500.0;

                // Quantize UV coordinates
                vec2 pixelUV = floor(uv / pixelSize) * pixelSize;

                return mix(getFromColor(pixelUV), getToColor(pixelUV), progress);
            }
        """.trimIndent()
    )

    private val rippleTransition = Transition(
        id = "ripple",
        name = "Ripple",
        category = TransitionCategory.CREATIVE,
        shaderCode = """
            uniform float amplitude; // = 0.05
            uniform float frequency; // = 20.0

            vec4 transition(vec2 uv) {
                vec2 center = vec2(0.5, 0.5);
                float dist = distance(uv, center);

                // Ripple wave
                float wave = sin(dist * frequency - progress * 10.0) * amplitude;
                wave *= (1.0 - progress); // Fade out ripple

                vec2 dir = normalize(uv - center);
                vec2 fromUV = uv + dir * wave;

                return mix(getFromColor(fromUV), getToColor(uv), progress);
            }
        """.trimIndent()
    )

    private val swirlTransition = Transition(
        id = "swirl",
        name = "Swirl",
        category = TransitionCategory.CREATIVE,
        isPremium = true,
        shaderCode = """
            vec4 transition(vec2 uv) {
                vec2 center = vec2(0.5, 0.5);
                vec2 offset = uv - center;
                float dist = length(offset);

                // Swirl angle based on distance and progress
                float angle = (1.0 - dist) * progress * 6.28318 * 2.0;
                float c = cos(angle);
                float s = sin(angle);

                vec2 rotated = vec2(
                    offset.x * c - offset.y * s,
                    offset.x * s + offset.y * c
                );

                vec2 fromUV = rotated + center;

                // Clamp UV
                fromUV = clamp(fromUV, 0.0, 1.0);

                return mix(getFromColor(fromUV), getToColor(uv), progress);
            }
        """.trimIndent()
    )

    private val glitchTransition = Transition(
        id = "glitch",
        name = "Glitch",
        category = TransitionCategory.CREATIVE,
        isPremium = true,
        shaderCode = """
            float rand(vec2 co) {
                return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
            }

            vec4 transition(vec2 uv) {
                float intensity = sin(progress * 3.14159);

                // Random horizontal displacement
                float slice = floor(uv.y * 20.0);
                float offset = (rand(vec2(slice, progress * 10.0)) - 0.5) * intensity * 0.1;

                vec2 fromUV = vec2(uv.x + offset, uv.y);
                vec2 toUV = vec2(uv.x - offset, uv.y);

                // Color channel separation
                vec4 fromColor = getFromColor(fromUV);
                vec4 toColor = getToColor(toUV);

                // RGB shift for glitch effect
                vec4 result;
                result.r = mix(fromColor.r, toColor.r, progress + intensity * 0.1);
                result.g = mix(fromColor.g, toColor.g, progress);
                result.b = mix(fromColor.b, toColor.b, progress - intensity * 0.1);
                result.a = 1.0;

                return result;
            }
        """.trimIndent()
    )

    // ============================================
    // CINEMATIC TRANSITIONS
    // ============================================

    private val cubeTransition = Transition(
        id = "cube",
        name = "Cube",
        category = TransitionCategory.CINEMATIC,
        isPremium = true,
        shaderCode = """
            vec4 transition(vec2 uv) {
                float angle = progress * 1.5708; // 90 degrees in radians

                // Simulate 3D cube rotation
                if (uv.x < progress) {
                    // Show 'to' face
                    float scale = cos(angle);
                    vec2 toUV = vec2((uv.x - progress) / max(scale, 0.001) + 1.0, uv.y);
                    if (toUV.x >= 0.0 && toUV.x <= 1.0) {
                        // Add shading
                        vec4 color = getToColor(toUV);
                        color.rgb *= 0.8 + 0.2 * (1.0 - progress);
                        return color;
                    }
                } else {
                    // Show 'from' face
                    float scale = sin(1.5708 - angle);
                    vec2 fromUV = vec2(uv.x / max(scale, 0.001), uv.y);
                    if (fromUV.x >= 0.0 && fromUV.x <= 1.0) {
                        vec4 color = getFromColor(fromUV);
                        color.rgb *= 0.8 + 0.2 * progress;
                        return color;
                    }
                }

                return vec4(0.0, 0.0, 0.0, 1.0);
            }
        """.trimIndent()
    )

    private val doorwayTransition = Transition(
        id = "doorway",
        name = "Doorway",
        category = TransitionCategory.CINEMATIC,
        shaderCode = """
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
        """.trimIndent()
    )

    private val pageFlipTransition = Transition(
        id = "page_flip",
        name = "Page Flip",
        category = TransitionCategory.CINEMATIC,
        isPremium = true,
        shaderCode = """
            vec4 transition(vec2 uv) {
                float p = progress;

                // Page curl position
                float curlX = 1.0 - p;

                if (uv.x > curlX) {
                    // Curled part showing backside of page
                    float localX = (uv.x - curlX) / max(p, 0.001);

                    // Curl deformation
                    float curl = sin(localX * 3.14159 * 0.5);
                    vec2 fromUV = vec2(1.0 - localX, uv.y);

                    vec4 color = getFromColor(fromUV);
                    // Darken curled part
                    color.rgb *= 0.6 + 0.4 * curl;
                    return color;
                } else {
                    // Show destination image
                    return getToColor(uv);
                }
            }
        """.trimIndent()
    )

    private val filmBurnTransition = Transition(
        id = "film_burn",
        name = "Film Burn",
        category = TransitionCategory.CINEMATIC,
        isPremium = true,
        shaderCode = """
            float rand(vec2 co) {
                return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
            }

            vec4 transition(vec2 uv) {
                float p = progress;

                // Noise-based burn pattern
                float noise = rand(uv * 10.0 + p);
                float burn = smoothstep(p - 0.2, p + 0.1, noise * uv.x + (1.0 - uv.y) * 0.5);

                vec4 fromColor = getFromColor(uv);
                vec4 toColor = getToColor(uv);

                // Add orange/red burn color
                vec3 burnColor = vec3(1.0, 0.5, 0.1);
                float burnIntensity = sin(p * 3.14159) * 0.5;

                vec4 result = mix(fromColor, toColor, burn);
                result.rgb = mix(result.rgb, burnColor, burnIntensity * (1.0 - abs(burn - 0.5) * 2.0));

                return result;
            }
        """.trimIndent()
    )

    // ============================================
    // 3D TRANSITIONS
    // ============================================

    private val cube3DTransition = Transition(
        id = "cube_3d",
        name = "3D Cube",
        category = TransitionCategory.THREE_D,
        isPremium = true,
        shaderCode = """
            const float PI = 3.14159265359;
            const float PERSP = 0.7;
            const float UNZOOM = 0.3;

            vec4 transition(vec2 uv) {
                float p = progress;
                float angle = p * PI / 2.0;

                // Calculate perspective
                vec2 fromUV = uv;
                vec2 toUV = uv;

                float sinA = sin(angle);
                float cosA = cos(angle);

                // Perspective correction
                float perspFrom = 1.0 + (1.0 - cosA) * PERSP;
                float perspTo = 1.0 + sinA * PERSP;

                // Unzoom during transition
                float zoom = 1.0 - sin(p * PI) * UNZOOM;

                // From face (rotating away)
                fromUV = (uv - 0.5) * zoom * perspFrom + 0.5;
                fromUV.x = fromUV.x * cosA + (1.0 - cosA);

                // To face (rotating in)
                toUV = (uv - 0.5) * zoom * perspTo + 0.5;
                toUV.x = toUV.x * sinA;

                vec4 fromColor = getFromColor(fromUV);
                vec4 toColor = getToColor(toUV);

                // Choose which face to show based on UV
                float edge = uv.x * cosA + sinA * 0.5;

                if (edge < p) {
                    // Show "to" face
                    if (toUV.x < 0.0 || toUV.x > 1.0 || toUV.y < 0.0 || toUV.y > 1.0) {
                        return vec4(0.0, 0.0, 0.0, 1.0);
                    }
                    // Add shading based on angle
                    float shade = 0.7 + 0.3 * sinA;
                    return vec4(toColor.rgb * shade, toColor.a);
                } else {
                    // Show "from" face
                    if (fromUV.x < 0.0 || fromUV.x > 1.0 || fromUV.y < 0.0 || fromUV.y > 1.0) {
                        return vec4(0.0, 0.0, 0.0, 1.0);
                    }
                    float shade = 0.7 + 0.3 * cosA;
                    return vec4(fromColor.rgb * shade, fromColor.a);
                }
            }
        """.trimIndent()
    )

    private val fold3DTransition = Transition(
        id = "fold_3d",
        name = "3D Fold",
        category = TransitionCategory.THREE_D,
        isPremium = true,
        shaderCode = """
            const float PI = 3.14159265359;

            vec4 transition(vec2 uv) {
                float p = progress;

                // Number of folds
                float folds = 2.0;
                float foldWidth = 1.0 / folds;

                // Calculate which fold segment we're in
                float segment = floor(uv.x * folds);
                float localX = fract(uv.x * folds);

                // Alternate fold direction
                float foldDir = mod(segment, 2.0) == 0.0 ? 1.0 : -1.0;

                // Fold angle increases with progress
                float angle = p * PI * 0.5 * foldDir;
                float cosA = cos(angle);

                // Squash the fold
                float squash = abs(cosA);

                // Calculate perspective
                float depth = (1.0 - squash) * 0.3;

                // UV for current segment
                vec2 foldUV = uv;
                foldUV.x = (segment + localX * squash) / folds;

                // Shade based on fold angle
                float shade = 0.5 + 0.5 * abs(cosA);

                // Mix between from and to based on progress
                vec4 fromColor = getFromColor(foldUV);
                vec4 toColor = getToColor(foldUV);

                // Transition reveals "to" from right to left
                float reveal = (1.0 - uv.x) < p ? 1.0 : 0.0;

                vec4 color = mix(fromColor, toColor, reveal);
                color.rgb *= shade;

                return color;
            }
        """.trimIndent()
    )

    private val flip3DTransition = Transition(
        id = "flip_3d",
        name = "3D Flip",
        category = TransitionCategory.THREE_D,
        isPremium = true,
        shaderCode = """
            const float PI = 3.14159265359;

            vec4 transition(vec2 uv) {
                float p = progress;

                // Rotation angle (0 to 180 degrees)
                float angle = p * PI;
                float cosA = cos(angle);
                float sinA = abs(sin(angle));

                // Perspective parameters
                float persp = 0.4;
                float depth = 0.5;

                // Calculate UV with perspective
                vec2 centeredUV = uv - 0.5;

                // Apply horizontal scaling for flip effect
                float scale = abs(cosA);

                // Avoid division by zero
                scale = max(scale, 0.001);

                // Perspective distortion
                float perspFactor = 1.0 + centeredUV.y * sinA * persp;
                vec2 perspUV;
                perspUV.x = centeredUV.x / (scale * perspFactor);
                perspUV.y = centeredUV.y / perspFactor;
                perspUV += 0.5;

                // Check bounds
                if (perspUV.x < 0.0 || perspUV.x > 1.0 || perspUV.y < 0.0 || perspUV.y > 1.0) {
                    return vec4(0.0, 0.0, 0.0, 1.0);
                }

                // Shading based on angle
                float shade = 0.5 + 0.5 * abs(cosA);

                // First half shows "from", second half shows "to" (mirrored)
                if (p < 0.5) {
                    vec4 color = getFromColor(perspUV);
                    color.rgb *= shade;
                    return color;
                } else {
                    // Mirror X for backside
                    perspUV.x = 1.0 - perspUV.x;
                    vec4 color = getToColor(perspUV);
                    color.rgb *= shade;
                    return color;
                }
            }
        """.trimIndent()
    )

    private val pageCurl3DTransition = Transition(
        id = "page_curl_3d",
        name = "3D Page Curl",
        category = TransitionCategory.THREE_D,
        isPremium = true,
        shaderCode = """
            const float PI = 3.14159265359;
            const float CURL_RADIUS = 0.15;

            vec4 transition(vec2 uv) {
                float p = progress;

                // Curl position moves from right to left
                float curlX = 1.0 - p * 1.5;

                // Distance from curl line
                float dist = uv.x - curlX;

                if (dist < 0.0) {
                    // Behind curl - show "to" image
                    return getToColor(uv);
                } else if (dist < CURL_RADIUS * PI) {
                    // In the curl zone
                    float curlAngle = dist / CURL_RADIUS;

                    if (curlAngle < PI) {
                        // Front of curl (showing "from" image curling away)
                        float curlZ = 1.0 - cos(curlAngle);

                        // Calculate curled UV
                        vec2 curlUV;
                        curlUV.x = curlX + sin(curlAngle) * CURL_RADIUS;
                        curlUV.y = uv.y;

                        // Shadow on the curl
                        float shade = 0.4 + 0.6 * (1.0 - curlZ * 0.5);

                        if (curlUV.x >= 0.0 && curlUV.x <= 1.0) {
                            vec4 color = getFromColor(curlUV);
                            color.rgb *= shade;
                            return color;
                        }
                    } else {
                        // Back of curl (showing backside)
                        float backAngle = curlAngle - PI;
                        vec2 backUV;
                        backUV.x = curlX + CURL_RADIUS * 2.0 - sin(backAngle) * CURL_RADIUS;
                        backUV.y = uv.y;

                        // Backside is darker
                        float shade = 0.3 + 0.3 * cos(backAngle);

                        if (backUV.x >= 0.0 && backUV.x <= 1.0) {
                            // Mirror the image for backside
                            backUV.x = 1.0 - backUV.x;
                            vec4 color = getFromColor(backUV);
                            color.rgb *= shade;
                            return color;
                        }
                    }

                    return getToColor(uv);
                } else {
                    // Still showing "from" image (not curled yet)
                    return getFromColor(uv);
                }
            }
        """.trimIndent()
    )

    private val roll3DTransition = Transition(
        id = "roll_3d",
        name = "3D Roll",
        category = TransitionCategory.THREE_D,
        shaderCode = """
            const float PI = 3.14159265359;

            vec4 transition(vec2 uv) {
                float p = progress;

                // Roll from bottom to top
                float rollY = 1.0 - p * 1.2;
                float rollRadius = 0.1;

                float dist = uv.y - rollY;

                if (dist > 0.0) {
                    // Below roll line - show "to" image
                    return getToColor(uv);
                } else {
                    float absDist = abs(dist);

                    if (absDist < rollRadius * PI * 0.5) {
                        // In the roll
                        float rollAngle = absDist / rollRadius;

                        // Rolling cylinder effect
                        vec2 rollUV;
                        rollUV.x = uv.x;
                        rollUV.y = rollY - sin(rollAngle) * rollRadius;

                        // Shade based on angle
                        float shade = 0.5 + 0.5 * cos(rollAngle);

                        if (rollUV.y >= 0.0 && rollUV.y <= 1.0) {
                            vec4 color = getFromColor(rollUV);
                            color.rgb *= shade;
                            return color;
                        }
                    }

                    // Above roll - show "from" image
                    return getFromColor(uv);
                }
            }
        """.trimIndent()
    )

    private val revolve3DTransition = Transition(
        id = "revolve_3d",
        name = "3D Revolve",
        category = TransitionCategory.THREE_D,
        shaderCode = """
            const float PI = 3.14159265359;

            vec4 transition(vec2 uv) {
                float p = progress;

                // Revolving door effect
                float angle = p * PI;
                float cosA = cos(angle);
                float sinA = sin(angle);

                vec2 center = vec2(0.5, 0.5);
                vec2 offset = uv - center;

                // Apply perspective based on x position
                float perspX = offset.x * cosA;
                float perspZ = offset.x * sinA;

                // Depth scaling
                float depth = 1.0 / (1.0 + perspZ * 0.5);

                vec2 transformedUV;
                transformedUV.x = perspX * depth + 0.5;
                transformedUV.y = offset.y * depth + 0.5;

                // Bounds check
                if (transformedUV.x < 0.0 || transformedUV.x > 1.0 ||
                    transformedUV.y < 0.0 || transformedUV.y > 1.0) {
                    return vec4(0.0, 0.0, 0.0, 1.0);
                }

                // Shade based on depth
                float shade = 0.5 + 0.5 * depth;

                // Show "from" or "to" based on which side we're seeing
                if (cosA > 0.0) {
                    vec4 color = getFromColor(transformedUV);
                    color.rgb *= shade;
                    return color;
                } else {
                    // Mirror for back face
                    transformedUV.x = 1.0 - transformedUV.x;
                    vec4 color = getToColor(transformedUV);
                    color.rgb *= shade;
                    return color;
                }
            }
        """.trimIndent()
    )

    // ============================================
    // LIBRARY ACCESS
    // ============================================

    private val allTransitions = listOf(
        // Fade
        fadeTransition,
        fadeColorTransition,
        fadeGrayscaleTransition,
        // Slide
        slideLeftTransition,
        slideRightTransition,
        slideUpTransition,
        slideDownTransition,
        // Wipe
        wipeLeftTransition,
        wipeRightTransition,
        wipeDiagonalTransition,
        blindsTransition,
        // Zoom
        zoomInTransition,
        zoomOutTransition,
        zoomRotateTransition,
        // Rotate
        rotateTransition,
        flipHorizontalTransition,
        flipVerticalTransition,
        // Blur
        blurTransition,
        directionalBlurTransition,
        // Geometric
        circleTransition,
        diamondTransition,
        heartTransition,
        starTransition,
        // Creative
        pixelizeTransition,
        rippleTransition,
        swirlTransition,
        glitchTransition,
        // Cinematic
        cubeTransition,
        doorwayTransition,
        pageFlipTransition,
        filmBurnTransition,
        // 3D Effects
        cube3DTransition,
        fold3DTransition,
        flip3DTransition,
        pageCurl3DTransition,
        roll3DTransition,
        revolve3DTransition
    )

    /**
     * Get all available transitions
     */
    fun getAll(): List<Transition> = allTransitions

    /**
     * Get transition by ID
     */
    fun getById(id: String): Transition? = allTransitions.find { it.id == id }

    /**
     * Get transitions by category
     */
    fun getByCategory(category: TransitionCategory): List<Transition> =
        allTransitions.filter { it.category == category }

    /**
     * Get free transitions only
     */
    fun getFree(): List<Transition> = allTransitions.filter { !it.isPremium }

    /**
     * Get premium transitions only
     */
    fun getPremium(): List<Transition> = allTransitions.filter { it.isPremium }

    /**
     * Get default transition (crossfade)
     */
    fun getDefault(): Transition = fadeTransition

    /**
     * Get transitions grouped by category
     */
    fun getGroupedByCategory(): Map<TransitionCategory, List<Transition>> =
        allTransitions.groupBy { it.category }
}
