package com.videomaker.aimusic.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// PRIMARY COLORS - App Tint Color
// ============================================

val Primary = Color(0xFFCCFF00)         // App tint color (bright yellow-lime)
val PrimaryVariant = Color(0xFFAACC00)  // Darker variant (80% brightness)
val PrimaryLight = Color(0xFFE6FF66)    // Lighter variant (40% white mix)
val PrimaryDark = Color(0xFF99CC00)     // Darkest variant (60% brightness)

val Secondary = Color(0xFFEA580C)       // Orange-600 (accent gradient start)
val SecondaryVariant = Color(0xFFF472B6) // Pink-400 (accent gradient end)
val SecondaryLight = Color(0xFFFB923C)  // Orange-400 (lighter orange)
val SecondaryDark = Color(0xFFC2410C)   // Orange-700 (darker orange)

val Tertiary = Color(0xFF1D4ED8)        // Blue-700 (purple gradient start)
val TertiaryVariant = Color(0xFF8B5CF6) // Violet-500 (purple gradient end)
val TertiaryLight = Color(0xFF60A5FA)   // Blue-400 (lighter blue)
val TertiaryDark = Color(0xFF1E3A8A)    // Blue-900 (darkest blue)

// ============================================
// GRADIENT COLORS - From Figma Design
// ============================================

// Red/Warm gradient (used for accent tags)
val GradientOrangeStart = Color(0xFFEA580C)  // Orange-600 (from-orange-600)
val GradientOrangeEnd = Color(0xFFF472B6)    // Pink-400 (to-pink-400)

// Purple/Cool gradient (used for accent tags)
val GradientPurpleStart = Color(0xFF1D4ED8)  // Blue-700 (from-blue-700)
val GradientPurpleEnd = Color(0xFF8B5CF6)    // Violet-500 (to-violet-500)

// Gradient aliases for backwards compatibility
val GradientOrange = GradientOrangeStart
val GradientPink = GradientOrangeEnd
val GradientBlue = GradientPurpleStart
val GradientViolet = GradientPurpleEnd

// Gradient collections for easy use
val WarmGradient = listOf(GradientOrangeStart, GradientOrangeEnd)
val CoolGradient = listOf(GradientPurpleStart, GradientPurpleEnd)
val FullGradient = listOf(GradientOrangeStart, GradientOrangeEnd, GradientPurpleStart, GradientPurpleEnd)

// ============================================
// BACKGROUND COLORS - Dark Theme (From Figma)
// ============================================

val SplashBackground = Color(0xFF101010)    // Splash/loading screen background
val BackgroundDark = Color(0xFF09090B)      // Zinc-950 (Figma background)
val BackgroundDarkElevated = Color(0xFF18181B) // Zinc-900 (elevated surface)
val SurfaceDark = Color(0xFF27272A)         // Zinc-800 (card/container surface)
val SurfaceDarkVariant = Color(0xFF3F3F46)  // Zinc-700 (alternative surface)
val CardDark = Color(0xFF18181B)            // Zinc-900 (card background)

// ============================================
// BACKGROUND COLORS - Light Theme (Alternative)
// ============================================

val BackgroundLight = Color(0xFFFFFFFF)     // Pure white
val BackgroundLightVariant = Color(0xFFF5F5F5) // Off-white
val SurfaceLight = Color(0xFFE3E3E3)        // Light surface (from icon)
val SurfaceLightVariant = Color(0xFFEEEEEE) // Alternative light surface
val CardLight = Color(0xFFFAFAFA)           // Light card background

// ============================================
// TEXT COLORS
// ============================================

// Dark theme text
val TextPrimaryDark = Color(0xFFF8FAFC)     // Near white (primary text on dark)
val TextSecondaryDark = Color(0xFFB3B3B3)   // Secondary text on dark
val TextTertiaryDark = Color(0xFF8C8C8C)    // Tertiary/hint text on dark
val TextDisabledDark = Color(0xFF666666)    // Disabled text on dark

// Light theme text
val TextPrimaryLight = Color(0xFF1C1C1C)    // Near black (primary text on light)
val TextSecondaryLight = Color(0xFF5A5A5A)  // Secondary text on light
val TextTertiaryLight = Color(0xFF999999)   // Tertiary/hint text on light
val TextDisabledLight = Color(0xFFCCCCCC)   // Disabled text on light

// Text on colored backgrounds
val TextOnPrimary = Color(0xFF09090B)       // Dark text on lime (Zinc-950/Neutral-N1000)
val TextOnSecondary = Color(0xFFFFFFFF)     // White text on orange gradient
val TextOnTertiary = Color(0xFFFFFFFF)      // White text on blue gradient

// ============================================
// GRAY SCALE - Zinc/Neutral from Figma
// ============================================

val Gray50 = Color(0xFFFAFAFA)          // Neutral-50
val Gray100 = Color(0xFFF5F5F5)         // Neutral-100
val Gray200 = Color(0xFFE5E5E5)         // Neutral-200
val Gray300 = Color(0xFFD4D4D8)         // Zinc-300
val Gray400 = Color(0xFFA1A1AA)         // Zinc-400
val Gray450 = Color(0xFF8E8E93)         // Mid-gray for inactive elements
val Gray500 = Color(0xFF71717A)         // Zinc-500
val Gray550 = Color(0xFF8C8C8C)         // Slightly darker mid-gray
val Gray600 = Color(0xFF52525B)         // Zinc-600
val Gray700 = Color(0xFF3F3F46)         // Zinc-700
val Gray800 = Color(0xFF27272A)         // Zinc-800
val Gray900 = Color(0xFF18181B)         // Zinc-900

// ============================================
// OVERLAY COLORS - Transparency variants
// ============================================

val White8 = Color(0x14FFFFFF)      // 8% white overlay
val White10 = Color(0x1AFFFFFF)     // 10% white overlay
val White12 = Color(0x1FFFFFFF)     // 12% white overlay
val White16 = Color(0x29FFFFFF)     // 16% white overlay
val White20 = Color(0x33FFFFFF)     // 20% white overlay
val White40 = Color(0x66FFFFFF)     // 40% white overlay
val White60 = Color(0x99FFFFFF)     // 60% white overlay
val White80 = Color(0xCCFFFFFF)     // 80% white overlay

val Black8 = Color(0x14000000)      // 8% black overlay
val Black12 = Color(0x1F000000)     // 12% black overlay
val Black16 = Color(0x29000000)     // 16% black overlay
val Black20 = Color(0x33000000)     // 20% black overlay
val Black24 = Color(0x3D000000)     // 24% black overlay
val Black40 = Color(0x66000000)     // 40% black overlay
val Black60 = Color(0x99000000)     // 60% black overlay
val Black80 = Color(0xCC000000)     // 80% black overlay

// Primary color overlays
val PrimaryOverlay20 = Color(0x33FF8E05)    // 20% orange overlay
val PrimaryOverlay40 = Color(0x66FF8E05)    // 40% orange overlay
val SecondaryOverlay20 = Color(0x3362E1CF)  // 20% cyan overlay
val SecondaryOverlay40 = Color(0x6662E1CF)  // 40% cyan overlay

// ============================================
// SEMANTIC COLORS
// ============================================

val Success = Color(0xFF4CAF50)         // Green for success states
val SuccessLight = Color(0xFF81C784)    // Light success
val SuccessDark = Color(0xFF388E3C)     // Dark success

val Error = Color(0xFFF44336)           // Red for errors
val ErrorLight = Color(0xFFE57373)      // Light error
val ErrorDark = Color(0xFFD32F2F)       // Dark error

val Warning = Color(0xFFFF9800)         // Orange for warnings
val WarningLight = Color(0xFFFFB74D)    // Light warning
val WarningDark = Color(0xFFF57C00)     // Dark warning

val Info = Color(0xFF2196F3)            // Blue for info
val InfoLight = Color(0xFF64B5F6)       // Light info
val InfoDark = Color(0xFF1976D2)        // Dark info

// ============================================
// COMPONENT-SPECIFIC COLORS
// ============================================

// Buttons
val CtaText = Color(0xFF151515)                 // Text on Primary CTA button
val ButtonPrimary = Primary                     // Orange button
val ButtonSecondary = Secondary                 // Cyan button
val ButtonTertiary = Tertiary                   // Pink button
val ButtonOutline = Gray800                     // Outlined button border
val ButtonDisabled = Gray600                    // Disabled button

// Input fields
val InputBackground = White10                   // Input background (dark theme)
val InputBackgroundLight = Gray100              // Input background (light theme)
val InputBorder = White16                       // Input border (dark theme)
val InputBorderLight = Gray300                  // Input border (light theme)
val InputFocused = Primary                      // Focused input border
val InputError = Error                          // Error state border

// Cards & Containers
val CardBackground = SurfaceDark                // Card background (dark)
val CardBackgroundLight = SurfaceLight          // Card background (light)
val CardBorder = White12                        // Card border (dark)
val CardBorderLight = Gray200                   // Card border (light)
val CardHover = White8                          // Card hover overlay
val CardPressed = White16                       // Card pressed overlay

// Dividers
val DividerDark = SurfaceDarkVariant            // Divider on dark background
val DividerLight = Gray200                      // Divider on light background
val DividerPrimary = PrimaryOverlay20           // Accent divider

// Chips & Tags
val ChipBackground = Black24                    // Chip background (dark)
val ChipBackgroundLight = Gray200               // Chip background (light)
val ChipBorder = White12                        // Chip border (dark)
val ChipBorderLight = Gray300                   // Chip border (light)
val ChipSelected = Primary                      // Selected chip
val ChipSelectedBackground = PrimaryOverlay20   // Selected chip background

// Loading & Shimmer
val ShimmerLight = Gray800                      // Shimmer highlight (dark theme)
val ShimmerDark = SurfaceDarkVariant            // Shimmer base (dark theme)
val ShimmerLightTheme = Gray200                 // Shimmer highlight (light theme)
val ShimmerDarkTheme = Gray100                  // Shimmer base (light theme)

// ============================================
// SPECIAL EFFECT COLORS
// ============================================

// Glass morphism effect
val GlassBackground = White10                   // Glass background
val GlassBorder = White12                       // Glass border
val GlassHighlight = White20                    // Glass highlight

// Music visualizer / Audio waveform colors
val WaveformPrimary = Primary
val WaveformSecondary = Secondary
val WaveformTertiary = Tertiary
val WaveformGradient = FullGradient

// Video timeline colors
val TimelineBackground = SurfaceDark
val TimelineCursor = Primary
val TimelineSelection = PrimaryOverlay40
val TimelineSegment = Secondary

// ============================================
// BACKWARDS COMPATIBILITY ALIASES
// ============================================
// Aliases for existing code that uses old color names

// Text aliases (commonly used in existing code)
val TextPrimary = TextPrimaryDark           // Alias for dark theme primary text
val TextSecondary = TextSecondaryDark       // Alias for dark theme secondary text
val TextTertiary = TextTertiaryDark         // Alias for dark theme tertiary text
val TextBright = TextOnPrimary              // Pure white
val TextHint = TextTertiaryDark             // Hint text
val TextInactive = TextDisabledDark         // Inactive text
val TextOnBackground = TextPrimaryDark      // Text on dark background
val TextOnSurface = TextPrimaryLight        // Text on light surface

// Component aliases
val PlaceholderBackground = SurfaceDarkVariant  // Placeholder/loading background
val ChipBorderInactive = ChipBorder             // Inactive chip border
val SearchFieldBackground = InputBackground      // Search field background
val SearchFieldBorder = InputBorder              // Search field border

// Accent color aliases (keeping old names for compatibility)
val GoldAccent = Color(0xFFFFD700)              // Gold for premium/star icons
val MintAccent = Color(0xFF04FFB1)              // Mint green accent
val CoralAccent = TertiaryLight                 // Coral pink accent
val TealAccent = SecondaryLight                 // Teal/cyan accent
val OrangeAccent = PrimaryLight                 // Orange accent
val SlateAccent = Gray500                       // Slate gray accent
val PinkAccent = TertiaryLight                  // Pink accent
val GreenAccent = SecondaryVariant              // Green accent
val RoseAccent = Color(0xFFE8A0BF)              // Rose pink
val CyanAccent = Secondary                      // Cyan accent
val AmberAccent = Color(0xFFD4A574)             // Amber/vintage gold

// Music note colors (from icon)
val MusicNoteWhite = TextOnPrimary              // White music note
val MusicNoteGray = SurfaceLight                // Gray variant

// ============================================
// TEMPLATE CARD
// ============================================
val TemplateBadgeBackground = Color(0xE5282828) // Use-count badge bg (#282828, 90% opacity)

// ============================================
// MUSIC PLAYER
// ============================================
val PlayerCardBackground = Color(0xFF373737)    // Song info + seeker card background
