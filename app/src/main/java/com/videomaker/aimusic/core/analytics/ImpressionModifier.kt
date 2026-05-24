package com.videomaker.aimusic.core.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView

/**
 * Fires [onVisible] once when the host composable becomes visible on screen for the first time.
 *
 * Visibility is measured as the fraction of the layout's bounds that intersect with the host
 * View's window rect. The callback fires when that fraction crosses [threshold] (default 50%).
 *
 * Use [key] to reset the "has triggered" flag when the underlying item changes (e.g. recycled
 * card binding to a new song id). Each impression site should pass a stable item identifier.
 *
 * Pair with [Analytics] IMPRESSION-policy events — Analytics' singleton dedupe set ensures the
 * same (event, item, location) tuple fires only once per process even if multiple call sites
 * trigger this modifier.
 */
@Composable
fun Modifier.onFirstVisible(
    key: Any,
    threshold: Float = 0.5f,
    onVisible: () -> Unit
): Modifier {
    var hasTriggered by remember(key) { mutableStateOf(false) }
    val view = LocalView.current
    return this.onGloballyPositioned { coords ->
        if (hasTriggered) return@onGloballyPositioned
        if (!coords.isAttached) return@onGloballyPositioned
        val bounds = coords.boundsInWindow()
        val viewWidth = view.width
        val viewHeight = view.height
        if (viewWidth == 0 || viewHeight == 0) return@onGloballyPositioned
        val visibleLeft = bounds.left.coerceAtLeast(0f)
        val visibleTop = bounds.top.coerceAtLeast(0f)
        val visibleRight = bounds.right.coerceAtMost(viewWidth.toFloat())
        val visibleBottom = bounds.bottom.coerceAtMost(viewHeight.toFloat())
        val visibleWidth = (visibleRight - visibleLeft).coerceAtLeast(0f)
        val visibleHeight = (visibleBottom - visibleTop).coerceAtLeast(0f)
        val visibleArea = visibleWidth * visibleHeight
        val totalArea = bounds.width * bounds.height
        if (totalArea > 0f && visibleArea / totalArea >= threshold) {
            hasTriggered = true
            onVisible()
        }
    }
}
