package de.bgsc.minigolf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Ein Wrapper für HapticFeedback, der die Benutzereinstellung (aktiviert/deaktiviert) berücksichtigt.
 * Wenn hapticEnabled false ist, werden alle haptischen Feedbacks unterdrückt.
 */
@Composable
fun ProvideSafeHaptic(hapticEnabled: Boolean, content: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val safeHaptic = remember(haptic, hapticEnabled) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (hapticEnabled) {
                    haptic.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
    CompositionLocalProvider(LocalHapticFeedback provides safeHaptic, content = content)
}
