package com.videomaker.aimusic.modules.language

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.data.local.LanguageManager
import com.videomaker.aimusic.core.data.local.SupportedLanguage
import com.videomaker.aimusic.core.data.local.getAllLanguages
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import com.videomaker.aimusic.ui.theme.Black12
import com.videomaker.aimusic.ui.theme.Black20
import com.videomaker.aimusic.ui.theme.Gray700
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.White20
import com.videomaker.aimusic.ui.theme.White40

/**
 * LanguageSelectionScreen - Language picker for onboarding and settings.
 *
 * Styled to match standard onboarding elements:
 * - Background: colorScheme.background
 * - CTA: glass capsule (primaryContainer bg + White40 border)
 * - Language cards: flat dark style matching feature cards
 */
@Composable
fun LanguageSelectionScreen(
    onLanguageSelected: (String) -> Unit,
    onContinue: () -> Unit,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {}
) {
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    val languages = remember { LanguageManager.getAllLanguages() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = if (showBackButton) 120.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showBackButton) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(70.dp))
            }

            Text(
                text = stringResource(R.string.language_select_title),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = if (showBackButton) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.language_select_subtitle),
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (showBackButton) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {

                Spacer(modifier = Modifier.height(36.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    languages.forEach { language ->
                        LanguageCard(
                            language = language,
                            isSelected = selectedLanguage == language.code,
                            onClick = {
                                selectedLanguage = language.code
                                onLanguageSelected(language.code)
                            }
                        )
                    }

                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Fixed CTA at bottom
        if (showBackButton){
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 48.dp)
            ) {
                OnboardingCtaMaxWidthButton(
                    text = stringResource(R.string.language_continue),
                    onClick = onContinue,
                    enabled = selectedLanguage != null
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
            ) {
                OnboardingCtaButton(
                    text = stringResource(R.string.onboarding_next),
                    icon = R.drawable.ic_right_arrow,
                    color = Primary,
                    onClick = onContinue,
                    enabled = selectedLanguage != null
                )
            }
        }
    }
}

// ============================================
// LANGUAGE CARD — flat dark style
// ============================================

@Composable
private fun LanguageCard(
    language: SupportedLanguage,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(50)
    val accentColor = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Black20)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else Gray700,
                shape = cardShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = language.flag,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 14.dp)
        )

        Text(
            text = language.displayName,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(22.dp))
        }
    }
}

// ============================================
// SHARED CTA BUTTON — iOS glassmorphism style
//
// Layers (inside capsule clip):
//   1. Base fill      — primaryContainer (~10% white)
//   2. Top highlight  — White40 → transparent over top 45% (specular reflection)
//   3. Bottom shadow  — transparent → Black12 over bottom 35% (depth)
//   4. Outer stroke   — White20 border (surface edge)
// ============================================

@Composable
internal fun OnboardingCtaButton(
    text: String,
    onClick: () -> Unit,
    icon: Int? = null,
    color: Color = MaterialTheme.colorScheme.onBackground,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(50)
    val baseColor = MaterialTheme.colorScheme.primaryContainer

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .drawBehind {
                // 1. Base glass fill
                drawRect(baseColor)
                // 2. Top inner highlight — light catching the top of the glass
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(White40, Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.45f
                    )
                )
                // 3. Bottom inner shadow — subtle depth below the glass
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Black12),
                        startY = size.height * 0.65f,
                        endY = size.height
                    )
                )
            }
            .border(1.dp, White20, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 20.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )

        icon?.let { 
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(20.dp)
            )
        }
    }
}

@Composable
internal fun OnboardingCtaMaxWidthButton(
    text: String,
    onClick: () -> Unit,
    icon: Int? = null,
    color: Color = MaterialTheme.colorScheme.onBackground,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(50)
    val baseColor = MaterialTheme.colorScheme.primaryContainer

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .drawBehind {
                // 1. Base glass fill
                drawRect(baseColor)
                // 2. Top inner highlight — light catching the top of the glass
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(White40, Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.45f
                    )
                )
                // 3. Bottom inner shadow — subtle depth below the glass
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Black12),
                        startY = size.height * 0.65f,
                        endY = size.height
                    )
                )
            }
            .border(1.dp, White20, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 20.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )

        icon?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(20.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LanguageSelectionScreenPreview() {
    VideoMakerTheme {
        LanguageSelectionScreen(
            onLanguageSelected = {},
            onContinue = {}
        )
    }
}
