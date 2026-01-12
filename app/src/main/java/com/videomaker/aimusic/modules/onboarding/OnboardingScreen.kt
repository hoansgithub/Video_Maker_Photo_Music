package com.videomaker.aimusic.modules.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.modules.onboarding.pages.OnboardingPage1
import com.videomaker.aimusic.modules.onboarding.pages.OnboardingPage2
import com.videomaker.aimusic.modules.onboarding.pages.OnboardingPage3
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import kotlinx.coroutines.launch

/**
 * Onboarding Screen
 *
 * Main composable for onboarding flow with:
 * - 3 pages showcasing app features
 * - Page indicators
 * - Next/Get Started button
 *
 * @param onComplete Callback when onboarding is complete
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val pageCount = 3
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> OnboardingPage1()
                1 -> OnboardingPage2()
                2 -> OnboardingPage3()
            }
        }

        // Bottom section with indicators and button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page Indicators
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                repeat(pageCount) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                    if (index < pageCount - 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }

            // Next/Get Started Button
            Button(
                onClick = {
                    if (pagerState.currentPage < pageCount - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (pagerState.currentPage < pageCount - 1) {
                        stringResource(R.string.onboarding_next)
                    } else {
                        stringResource(R.string.onboarding_get_started)
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    VideoMakerTheme {
        OnboardingScreen(onComplete = {})
    }
}
