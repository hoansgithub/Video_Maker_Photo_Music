package com.videomaker.aimusic.modules.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.theme.NewIdeasBackground
import com.videomaker.aimusic.ui.theme.Primary

private const val ANIM_MS = 700           // full "Create New Video" collapse / expand
private const val SW_DELAY_MS = 260        // "See What's New" waits while the pill collapses past its start point
private const val SW_MS = ANIM_MS - SW_DELAY_MS // then slides in, finishing together with the pill
private const val FAB_SIZE = 52
private const val FAB_GAP = 12
private val PillShape = RoundedCornerShape(26.dp) // half of 52dp → a perfect circle at 52dp wide

// Songs: no create pill to coordinate with, so a simple centered reveal.
private val SongsRefreshEnter: EnterTransition =
    expandHorizontally(tween(ANIM_MS, easing = FastOutSlowInEasing), expandFrom = Alignment.Start) +
        fadeIn(tween(ANIM_MS, easing = FastOutSlowInEasing))
private val SongsRefreshExit: ExitTransition =
    shrinkHorizontally(tween(ANIM_MS, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start) +
        fadeOut(tween(ANIM_MS, easing = FastOutSlowInEasing))

/**
 * Bottom floating-action overlay shared by the Gallery and Songs tabs.
 *
 * Gallery: a single morphing element — the centered, content-width "Create New Video" pill
 * shrinks its width down to a 52dp circle while gliding into the bottom-right corner, so it
 * literally becomes the (+) float button (no crossfade, no scale distortion → smooth like the
 * full-width version). "See What's New" stays still, then (after a delay) slides in beside the
 * (+) in parallel. Opening (scrolling to the top) plays the reverse.
 *
 * Songs tab has no create button — only the centered "See What's New" pill.
 */
@Composable
fun HomeFabOverlay(
    isGalleryTab: Boolean,
    collapsed: Boolean,
    refreshVisible: Boolean,
    onCreateClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (isGalleryTab) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val fullWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val collapse = animateFloatAsState(
                    targetValue = if (collapsed) 1f else 0f,
                    animationSpec = tween(ANIM_MS, easing = FastOutSlowInEasing),
                    label = "collapse"
                )

                CreateMorphButton(
                    collapsed = collapsed,
                    progress = { collapse.value },
                    fullWidthPx = fullWidthPx,
                    onClick = onCreateClick,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )

                // "See What's New" — revealed in place by a clip wipe: show sweeps start→end
                // (left→right), hide retracts end→start (right→left). Delayed so it waits while
                // the create pill collapses past its start point.
                val swReveal = animateFloatAsState(
                    targetValue = if (refreshVisible) 1f else 0f,
                    animationSpec = tween(
                        SW_MS,
                        delayMillis = if (refreshVisible) SW_DELAY_MS else 0,
                        easing = FastOutSlowInEasing
                    ),
                    label = "swReveal"
                )
                val swActive by remember { derivedStateOf { swReveal.value > 0.001f } }
                if (swActive) {
                    SeeWhatsNewPill(
                        onClick = onRefreshClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = (FAB_SIZE + FAB_GAP).dp)
                            .drawWithContent {
                                val revealWidth = (size.width * swReveal.value).coerceIn(0f, size.width)
                                clipRect(right = revealWidth) { this@drawWithContent.drawContent() }
                            }
                    )
                }
            }
        } else {
            // Songs tab — only the "See What's New" pill, centered.
            AnimatedVisibility(
                visible = refreshVisible,
                enter = SongsRefreshEnter,
                exit = SongsRefreshExit,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                SeeWhatsNewPill(onClick = onRefreshClick)
            }
        }
    }
}

/**
 * The "Create New Video" CTA as one morphing element.
 *
 * Expanded: a centered, content-width pill (measured once via [onGloballyPositioned]).
 * Collapsing: its width animates down to 52dp (becoming the (+) circle) while [progress]
 * glides it from center into the right corner and fades the label out. Single element ⇒ no
 * crossfade / no scale distortion.
 */
@Composable
private fun CreateMorphButton(
    collapsed: Boolean,
    progress: () -> Float,
    fullWidthPx: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var contentWidthPx by remember { mutableIntStateOf(0) }
    val measured = contentWidthPx > 0
    val contentWidthDp = with(LocalDensity.current) { contentWidthPx.toDp() }

    // Width is derived from the collapse progress (NOT a self-animating state), so on first
    // entry / tab switch (progress == 0) it simply appears at full width with no animation;
    // it only animates while the user actually collapses it by scrolling.
    val width = lerp(contentWidthDp, FAB_SIZE.dp, progress())

    Box(
        modifier = modifier
            // Wrap content until measured; then animate the real width (anchored to the right).
            .then(if (measured) Modifier.width(width) else Modifier)
            .height(FAB_SIZE.dp)
            .graphicsLayer {
                // Expanded → shifted left to sit centered; collapsed → 0 (right corner).
                val centerOffset = -((fullWidthPx - contentWidthPx) / 2f)
                translationX = centerOffset * (1f - progress())
            }
            .shadow(elevation = 10.dp, shape = PillShape, spotColor = Primary)
            .clip(PillShape)
            .background(Primary)
            .clickableSingle(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .onGloballyPositioned { if (contentWidthPx == 0) contentWidthPx = it.size.width }
                // start padding 14dp keeps the 24dp icon centered when width == 52dp.
                .padding(start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_circle_plus),
                contentDescription = "Create",
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(R.string.gallery_create_new_video),
                color = Color.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                // Fades out over the first ~60% so it's gone before the pill is a circle.
                modifier = Modifier.graphicsLayer {
                    alpha = (1f - progress() / 0.6f).coerceIn(0f, 1f)
                }
            )
        }
    }
}

/**
 * Black pill with a Primary border, "See What's New" label and a Primary refresh glyph.
 * Wraps its content to match the mockup.
 */
@Composable
private fun SeeWhatsNewPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .background(color = NewIdeasBackground, shape = PillShape)
            .border(width = 1.5.dp, color = Primary, shape = PillShape)
            .clickableSingle(onClick = onClick)
            .padding(start = 24.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "See What's New",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
        Icon(
            imageVector = Icons.Default.Autorenew,
            contentDescription = "Refresh",
            tint = Primary,
            modifier = Modifier.size(24.dp)
        )
    }
}
