package com.videomaker.aimusic.modules.onboarding.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.VideoMakerTheme

/**
 * Onboarding Page 3 - Export & Share
 *
 * Showcases export and sharing features
 */
@Composable
fun OnboardingPage3() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF4facfe),
                        Color(0xFF00f2fe)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 120.dp, bottom = 200.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Title
            Text(
                text = stringResource(R.string.onboarding_page3_title),
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                lineHeight = 44.sp,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Feature 1 - High Quality
            FeatureItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.onboarding_page3_feature1_title),
                description = stringResource(R.string.onboarding_page3_feature1_desc)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Feature 2 - Share
            FeatureItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.onboarding_page3_feature2_title),
                description = stringResource(R.string.onboarding_page3_feature2_desc)
            )
        }
    }
}

@Composable
private fun FeatureItem(
    icon: @Composable () -> Unit,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPage3Preview() {
    VideoMakerTheme {
        OnboardingPage3()
    }
}
