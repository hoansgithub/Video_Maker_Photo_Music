package com.videomaker.aimusic.modules.rate

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.videomaker.aimusic.R
import com.videomaker.aimusic.modules.onboarding.listOnboardingStep
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.BackgroundDark
import com.videomaker.aimusic.ui.theme.Black40
import com.videomaker.aimusic.ui.theme.FoundationBlack
import com.videomaker.aimusic.ui.theme.FoundationBlack_100
import com.videomaker.aimusic.ui.theme.Gray600
import com.videomaker.aimusic.ui.theme.Neutral_N500
import com.videomaker.aimusic.ui.theme.Neutral_N600
import com.videomaker.aimusic.ui.theme.Neutral_N900
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextPrimaryDark
import kotlinx.coroutines.delay
import kotlin.comparisons.then
import android.view.ViewTreeObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@Composable
fun RatingSatisfactionPopup(
    onNotReally: () -> Unit,
    onGood: () -> Unit,
    onDismiss: () -> Unit,
) {

    Dialog(
        onDismissRequest = {
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .clickableSingle {
                }
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Neutral_N900)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Smile icon at top center
                    Image(
                        painter = painterResource(R.drawable.img_rate_content),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(0.75f),
                        contentScale = ContentScale.FillWidth
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Title: "We're grateful for your time on <App Name>"
                    Text(
                        text = stringResource(R.string.rating_satisfaction_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle: "Are you satisfied with our app?"
                    Text(
                        text = stringResource(R.string.rating_satisfaction_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neutral_N500,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Two inline buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // "Not really" button - secondary style with background
                        Text(
                            text = stringResource(R.string.rating_not_really),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            textAlign = TextAlign.Center,
                            color = FoundationBlack_100,
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.Black.copy(0.2f), RoundedCornerShape(120.dp))
                                .clickableSingle { onNotReally.invoke() }
                                .padding(16.dp)
                        )

                        // "GOOD" button - primary style with smiling icon
                        Text(
                            text = stringResource(R.string.rating_good),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 16.sp,
                            color = FoundationBlack,
                            fontWeight = FontWeight.W600,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .background(Primary, RoundedCornerShape(120.dp))
                                .clickableSingle { onGood.invoke() }
                                .padding(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Close circle button - top right inside popup
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Black40)
                        .clickableSingle { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_circle),
                        contentDescription = null,
                        tint = Gray600,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
@Composable
fun RatingStarsPopup(
    onLowRating: (stars: Int) -> Unit,
    onHighRating: (stars: Int) -> Unit,
    onDismiss: () -> Unit,
) {

    var selectedStars by remember { mutableIntStateOf(5) }
    Dialog(
        onDismissRequest = {
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .clickableSingle {
                }
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Neutral_N900)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Spacer(modifier = Modifier.height(30.dp))

                    // Title: "We're grateful for your time on <App Name>"
                    Text(
                        text = stringResource(R.string.rating_experience_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )


                    Spacer(modifier = Modifier.height(24.dp))

                    // 5 Star rating buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (star in 1..5) {
                            val isSelected = star <= selectedStars
                            Icon(
                                painter = painterResource(
                                    R.drawable.ic_star
                                ),
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFFFABA0B) else Neutral_N600,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { selectedStars = star }
                                    .padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Rate Us button - primary style
                    Button(
                        onClick = {
                            if (selectedStars <= 3) {
                                onLowRating(selectedStars)
                            } else {
                                onHighRating(selectedStars)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = BackgroundDark
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.rating_submit),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Close circle button - top right inside popup
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Black40)
                        .clickableSingle { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_circle),
                        contentDescription = null,
                        tint = Gray600,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RatingFeedbackPopup(
    onSubmit: (feedback: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val imeState = rememberImeState()
    val pagerState = rememberLazyListState()
    var selectedStars by remember { mutableIntStateOf(-1) }
    var feedBackContent by remember { mutableStateOf("") }
    val listFeedBack = feedbackOptions()

    LaunchedEffect(selectedStars) {
        if (selectedStars != -1) {
            pagerState.animateScrollToItem(selectedStars)
        }
    }

    Dialog(
        onDismissRequest = {
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .clickableSingle {
                }
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Neutral_N900)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight(0.7f)
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Spacer(modifier = Modifier.height(30.dp))

                    // Title: "We're grateful for your time on <App Name>"
                    Text(
                        text = stringResource(R.string.rating_feedback_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle: "Are you satisfied with our app?"
                    Text(
                        text = stringResource(R.string.rating_feedback_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neutral_N500,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Feedback
                    LazyColumn(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        items(listFeedBack, key = {it.index}){
                            ItemFeedBackView(
                                current = selectedStars,
                                item = it,
                                onClick = {
                                    selectedStars = it.index
                                    if (it.index != 7) {
                                        feedBackContent = it.content
                                    }
                                },
                                onChange = {
                                    feedBackContent = it
                                }
                            )
                        }

                        if (imeState.value) {
                            item {
                                Spacer(Modifier.height(100.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Rate Us button - primary style
                    Button(
                        onClick = {
                            onSubmit.invoke(feedBackContent)
                        },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = feedBackContent.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = BackgroundDark
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.rating_submit),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                // Close circle button - top right inside popup
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Black40)
                        .clickableSingle { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_circle),
                        contentDescription = null,
                        tint = Gray600,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ItemFeedBackView(
    current: Int,
    item: ItemFeedBack,
    onClick: (ItemFeedBack) -> Unit,
    onChange: (String) -> Unit,
) {
    var feedbackText by remember { mutableStateOf("") }

    // Prevent TextField focus crash during AnimatedContent transition
    // Allow 200ms for fade animation to complete before enabling TextField interactions
    var isTextFieldEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300) // Wait for AnimatedContent fade-in to complete (~300ms)
        isTextFieldEnabled = true
    }


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
        verticalAlignment = if (item.index == 7 && current == item.index) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (current == item.index){
            Image(
                painter = painterResource(R.drawable.img_checkbox),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = if (item.index == 7 && current == item.index) 18.dp else 0.dp)
                    .size(24.dp)
            )
        } else {
            Spacer(
                modifier = Modifier
                    .padding(top = if (item.index == 7 && current == item.index) 18.dp else 0.dp)
                    .size(24.dp)
                    .border(1.5.dp, Color(0xFF868686), CircleShape)
                    .clickableSingle {
                        onClick.invoke(item)
                    }
            )
        }
        Column(
            modifier = Modifier
                .weight(1f),
        ) {
            Text(
                text = item.content,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 18.dp, end = 16.dp),
                color = TextPrimaryDark,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W400),
                fontSize = 16.sp
            )

            if (item.index != 7) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(0.06f))
                )
            } else {
                if (current == item.index) {
                    onChange.invoke(feedbackText)
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .fillMaxWidth()
                            .heightIn(min = 100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color.White, RoundedCornerShape(16.dp))
                            .then(
                                if (!isTextFieldEnabled) {
                                    // Block all pointer events during animation settling period
                                    Modifier.clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { /* Consume clicks during animation */ }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        BasicTextField(
                            value = feedbackText,
                            onValueChange = {
                                feedbackText = it
                                onChange.invoke(it)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                            cursorBrush = SolidColor(Primary),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (feedbackText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.rating_feedback_type_hint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Neutral_N600
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

data class ItemFeedBack(
    val index: Int,
    val content: String
)

@Composable
private fun feedbackOptions(): List<ItemFeedBack> = listOf(
    ItemFeedBack(
        1,
        stringResource(R.string.rating_feedback_reason_music_quality)
    ),
    ItemFeedBack(
        2,
        stringResource(R.string.rating_feedback_reason_translation)
    ),
    ItemFeedBack(
        3,
        stringResource(R.string.rating_feedback_reason_tools)
    ),
    ItemFeedBack(
        4,
        stringResource(R.string.rating_feedback_reason_output_quality)
    ),
    ItemFeedBack(
        5,
        stringResource(R.string.rating_feedback_reason_performance)
    ),
    ItemFeedBack(
        6,
        stringResource(R.string.rating_feedback_reason_templates)
    ),
    ItemFeedBack(
        7,
        stringResource(R.string.rating_feedback_reason_other)
    ),
)

/**
 * Rating popup flow states.
 * Type-safe state machine for the rating flow.
 */
enum class RatingStep {
    None,
    Satisfaction,
    Feedback,
    Stars
}

@Composable
fun rememberImeState(): State<Boolean> {
    val imeState = remember {
        mutableStateOf(false)
    }

    val view = LocalView.current
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val isKeyboardOpen = ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime()) ?: true
            imeState.value = isKeyboardOpen
        }

        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    return imeState
}
