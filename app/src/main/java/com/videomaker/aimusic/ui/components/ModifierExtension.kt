package com.videomaker.aimusic.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.Role

object ModifierExtension {
    internal fun interface MultipleEventsCutter {
        fun processEvent(event: () -> Unit)

        companion object
    }

    @SuppressLint("RememberReturnType", "ModifierFactoryUnreferencedReceiver")
    fun Modifier.clickableSingle(
        enabled: Boolean = true,
        onClickLabel: String? = null,
        role: Role? = null,
        indicated: Boolean = false,
        onClick: () -> Unit
    ) = composed(
        inspectorInfo = debugInspectorInfo {
            name = "clickable"
            properties["enabled"] = enabled
            properties["onClickLabel"] = onClickLabel
            properties["role"] = role
            properties["onClick"] = onClick
        }
    ) {
        val multipleEventsCutter = remember { MultipleEventsCutter.get() }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        Modifier.clickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            onClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                multipleEventsCutter.processEvent { onClick() }
            },
            role = role,
            indication = if (indicated) LocalIndication.current else null,
            interactionSource = remember { MutableInteractionSource() }
        )
    }

    internal fun MultipleEventsCutter.Companion.get(): MultipleEventsCutter =
        MultipleEventsCutterImpl()

    private class MultipleEventsCutterImpl : MultipleEventsCutter {
        private val now: Long
            get() = System.currentTimeMillis()

        private var lastEventTimeMs: Long = 0

        override fun processEvent(event: () -> Unit) {
            if (now - lastEventTimeMs >= 300L) {
                event.invoke()
            }
            lastEventTimeMs = now
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun Modifier.clickOutSideToHideKeyBoard() = composed {
        val localSoftwareKeyboardController = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        clickableSingle(indicated = false) {
            localSoftwareKeyboardController?.hide()
            focusManager.clearFocus()
        }
    }
}