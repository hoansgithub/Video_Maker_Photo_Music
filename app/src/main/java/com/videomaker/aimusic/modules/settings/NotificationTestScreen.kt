package com.videomaker.aimusic.modules.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.notification.NotificationChannels
import com.videomaker.aimusic.core.notification.NotificationDeepLinkFactory
import com.videomaker.aimusic.core.notification.NotificationPayload
import com.videomaker.aimusic.core.notification.NotificationRenderer
import com.videomaker.aimusic.core.notification.NotificationType
import com.videomaker.aimusic.ui.theme.CtaText
import com.videomaker.aimusic.ui.theme.FoundationBlack
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationTestScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationRenderer: NotificationRenderer = koinInject()

    fun showNow(payload: NotificationPayload) {
        scope.launch {
            val shown = notificationRenderer.show(payload)
            Toast.makeText(
                context,
                if (shown) "Notification sent" else "Failed to send notification",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notification Test",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {},
                actions = {
                    Button(
                        onClick = onNavigateBack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    ) {
                        Text("Close", color = MaterialTheme.colorScheme.onSurface)
                    }
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .background(FoundationBlack),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    showNow(
                        NotificationPayload(
                            type = NotificationType.TRENDING_SONG,
                            itemId = "909001",
                            itemType = "song",
                            channelId = NotificationChannels.CHANNEL_TREND_ALERTS,
                            title = "New Trend Song!",
                            body = "Kendrick's 'Not Like Us' is going viral! Create your edit in 1-tap before the trend peaks.",
                            ctaText = "Play Song",
                            deepLink = NotificationDeepLinkFactory.trendingSong(909001L),
                            imageCandidates = emptyList(),
                            fallbackImageRes = R.drawable.img_song1
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CtaText)
            ) { Text("1. Trending Song Alert") }

            Button(
                onClick = {
                    showNow(
                        NotificationPayload(
                            type = NotificationType.VIRAL_TEMPLATE,
                            itemId = "debug_template_viral",
                            itemType = "template",
                            channelId = NotificationChannels.CHANNEL_TREND_ALERTS,
                            title = "Trending templates for you. Try it now!",
                            body = "1,000,000 people used this FlashCut today. Don't miss out on the hype!",
                            ctaText = "Discover Templates",
                            deepLink = NotificationDeepLinkFactory.viralTemplate("debug_template_viral"),
                            imageCandidates = emptyList(),
                            fallbackImageRes = R.drawable.img_template1
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CtaText)
            ) { Text("2. Viral Template Alert") }

            Button(
                onClick = {
                    showNow(
                        NotificationPayload(
                            type = NotificationType.FORGOTTEN_MASTERPIECE,
                            itemId = "debug_project_01",
                            itemType = "video",
                            channelId = NotificationChannels.CHANNEL_MY_VIDEO_RETENTION,
                            title = "Your masterpiece is waiting!",
                            body = "You did the hard part, now let it shine. Your video is ready to be shared with the world!",
                            ctaText = "Continue Editing",
                            deepLink = NotificationDeepLinkFactory.myVideo("debug_project_01", "hint_share"),
                            imageCandidates = emptyList(),
                            fallbackImageRes = R.drawable.img_template2,
                            ivCtaIcon = R.drawable.ic_video_generator_fill
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CtaText)
            ) { Text("3. Forgotten Masterpiece") }

            Button(
                onClick = {
                    showNow(
                        NotificationPayload(
                            type = NotificationType.QUICK_SAVE_REMINDER,
                            itemId = "debug_project_02",
                            itemType = "video",
                            channelId = NotificationChannels.CHANNEL_MY_VIDEO_RETENTION,
                            title = "Don't lose your work!",
                            body = "Your edit looks amazing! Save it to your gallery now so you don't lose those perfect beat-syncs.",
                            ctaText = "Continue Editing",
                            deepLink = NotificationDeepLinkFactory.myVideo("debug_project_02", "hint_save"),
                            imageCandidates = emptyList(),
                            fallbackImageRes = R.drawable.img_template3,
                            ivCtaIcon = R.drawable.ic_video_generator_fill
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CtaText)
            ) { Text("4. Quick Save Reminder") }

            Button(
                onClick = {
                    showNow(
                        NotificationPayload(
                            type = NotificationType.SHARE_ENCOURAGEMENT,
                            itemId = "debug_project_03",
                            itemType = "video",
                            channelId = NotificationChannels.CHANNEL_MY_VIDEO_RETENTION,
                            title = "Ready for the 'Likes'?",
                            body = "Your 'Not Like Us' edit is 100% beat-synced and ready to go. Post it to TikTok now!",
                            ctaText = "Continue Editing",
                            deepLink = NotificationDeepLinkFactory.myVideo("debug_project_03", "hint_share"),
                            imageCandidates = emptyList(),
                            fallbackImageRes = R.drawable.img_song3
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CtaText)
            ) { Text("5. Share Encouragement") }

            Button(
                onClick = {
                    showNow(
                        NotificationPayload(
                            type = NotificationType.ABANDONED_SELECT_PHOTOS,
                            itemId = "debug_draft_01",
                            itemType = "draft",
                            channelId = NotificationChannels.CHANNEL_CREATION_REMINDERS,
                            title = "Don't leave the beat hanging!",
                            body = "Your 'Not Like Us' edit is almost ready. Just pick a few photos to see the magic happen!",
                            ctaText = "View Template",
                            deepLink = NotificationDeepLinkFactory.resumeTemplate(
                                templateId = "debug_template_viral",
                                songId = 909001L,
                                draftId = "debug_draft_01"
                            ),
                            imageCandidates = emptyList(),
                            fallbackImageRes = R.drawable.img_template1
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CtaText)
            ) { Text("6. Abandoned Select Photos") }

            Button(
                onClick = {
                    showNow(
                        NotificationPayload(
                            type = NotificationType.DRAFT_COMPLETION_NUDGE,
                            itemId = "debug_draft_02",
                            itemType = "draft",
                            channelId = NotificationChannels.CHANNEL_CREATION_REMINDERS,
                            title = "Finish what you started!",
                            body = "You are only one step away from a viral video. Come back and add your photos now!",
                            ctaText = "View Template",
                            deepLink = NotificationDeepLinkFactory.resumeTemplate(
                                templateId = "debug_template_viral",
                                songId = 909001L,
                                draftId = "debug_draft_02"
                            ),
                            imageCandidates = emptyList(),
                            fallbackImageRes = R.drawable.img_template2
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CtaText)
            ) { Text("7. Draft Completion Nudge") }
        }
    }
}
