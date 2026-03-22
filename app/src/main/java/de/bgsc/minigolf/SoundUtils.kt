package de.bgsc.minigolf

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

interface SoundFeedback {
    fun playClick()
}

val LocalSoundFeedback: ProvidableCompositionLocal<SoundFeedback> = staticCompositionLocalOf {
    object : SoundFeedback {
        override fun playClick() {}
    }
}

@Composable
fun ProvideSafeSound(soundEnabled: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    val safeSound = remember(audioManager, soundEnabled) {
        object : SoundFeedback {
            override fun playClick() {
                if (soundEnabled) {
                    audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK)
                }
            }
        }
    }
    CompositionLocalProvider(LocalSoundFeedback provides safeSound, content = content)
}
