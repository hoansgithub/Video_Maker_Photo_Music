package com.videomaker.aimusic.modules.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.BuildConfig
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.CtaText
import com.videomaker.aimusic.ui.theme.FoundationBlack
import com.videomaker.aimusic.ui.theme.FoundationBlack_100
import com.videomaker.aimusic.ui.theme.TextInactive
import com.videomaker.aimusic.ui.theme.VideoMakerTheme
import androidx.core.net.toUri
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * SettingsScreen - App settings page
 *
 * Features:
 * - App version info
 *
 * Note: Language selection is handled by LanguageSelectionActivity on first launch.
 *       Language option has been removed from settings for a smoother UX.
 *
 * @param onNavigateBack Callback to navigate back
 */

private const val CONTACT_EMAIL = "admin@alcheclub.co"
private const val TERMS_OF_SERVICE_URL = "https://alcheclub.co/terms-of-use/"
private const val PRIVACY_POLICY_URL = "https://alcheclub.co/privacy-policy/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val reviewManager = remember(context) { ReviewManagerFactory.create(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(0.1f), CircleShape)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = { onNavigateBack.invoke() }
                            )
                            .padding(12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FoundationBlack
                )
            )
        },
        containerColor = FoundationBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_account),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextInactive
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CtaText, RoundedCornerShape(16.dp))
                    .padding(start = 24.dp)
            ) {
                SettingsItem(
                    icon = R.drawable.ic_language,
                    title = stringResource(R.string.settings_language),
                    subtitle = "",
                    isShowLine = true,
                    onClick = {}
                )
                SettingsItem(
                    icon = R.drawable.ic_menu_square,
                    title = stringResource(R.string.settings_app_widet),
                    subtitle = "",
                    isShowLine = true,
                    onClick = {}
                )
                SettingsItem(
                    icon = R.drawable.ic_rate,
                    title = stringResource(R.string.settings_rate_us),
                    subtitle = "",
                    isShowLine = true,
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val reviewInfo = reviewManager.requestReviewFlow().await()
                                val activity = context as? Activity
                                if (activity != null) {
                                    val startTime = System.currentTimeMillis()
                                    reviewManager.launchReviewFlow(activity, reviewInfo).await()
                                    val elapsed = System.currentTimeMillis() - startTime

                                    // Nếu hoàn thành dưới 1 giây → dialog có thể không hiện
                                    if (elapsed < 1000) {
                                        openPlayStore(context)
                                    }
                                } else {
                                    openPlayStore(context)
                                }
                            } catch (e: Exception) {
                                openPlayStore(context)
                            }
                        }
                    }
                )
                SettingsItem(
                    icon = R.drawable.ic_share,
                    title = stringResource(R.string.settings_share_with_friends),
                    subtitle = "",
                    isShowLine = true,
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Check out this app!")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Hey! I'm using this awesome app. Try it out:\nhttps://play.google.com/store/apps/details?id=${context.packageName}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                    }
                )
                SettingsItem(
                    icon = R.drawable.ic_report_problem,
                    title = stringResource(R.string.settings_report),
                    subtitle = "",
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = "mailto:$CONTACT_EMAIL".toUri()
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // No email client available
                        }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_legal),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextInactive
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CtaText, RoundedCornerShape(16.dp))
                    .padding(start = 24.dp)
            ) {
                SettingsItem(
                    icon = R.drawable.ic_term_of_service,
                    title = stringResource(R.string.settings_term_of_service),
                    subtitle = "",
                    isShowLine = true,
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, TERMS_OF_SERVICE_URL.toUri())
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // No browser available
                        }
                    }
                )
                SettingsItem(
                    icon = R.drawable.ic_privacy,
                    title = stringResource(R.string.settings_privacy_policy),
                    subtitle = "",
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // No browser available
                        }
                    }
                )
            }

            // About Section
//            SettingsItem(
//                icon = Icons.Default.Info,
//                title = stringResource(R.string.settings_about),
//                subtitle = "${stringResource(R.string.settings_version)} ${BuildConfig.VERSION_NAME}",
//                onClick = {}
//            )
        }
    }
}

private fun openPlayStore(context: Context) {
    val packageName = context.packageName
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        )
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$packageName".toUri()
            )
        )
    }
}

@Composable
private fun SettingsItem(
    icon: Int,
    title: String,
    subtitle: String,
    isShowArrow: Boolean = false,
    isShowLine: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = CtaText
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Text content
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.W400,
                            color = FoundationBlack_100
                        )
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Arrow
                if (isShowArrow) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

            }
            if (isShowLine){
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(0.08f))
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    VideoMakerTheme {
        SettingsScreen(
            onNavigateBack = {}
        )
    }
}
