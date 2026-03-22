package de.bgsc.minigolf

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Interface für Sound-Feedback in der App.
 */
interface SoundFeedback {
    fun playClick()
}

/**
 * Ein CompositionLocal, um den SoundManager überall in der UI-Hierarchie verfügbar zu machen.
 */
val LocalSoundFeedback: ProvidableCompositionLocal<SoundFeedback> = staticCompositionLocalOf {
    object : SoundFeedback {
        override fun playClick() {}
    }
}

/**
 * Ein Wrapper für Sound-Feedback, der die Benutzereinstellung (soundEnabled) berücksichtigt.
 * Wir nutzen den AudioManager direkt, um volle Kontrolle über den Sound zu haben
 * und automatische Systemsounds (die zu Doppelklicks führen) zu vermeiden.
 */
@Composable
fun ProvideSafeSound(soundEnabled: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    
    val safeSound = remember(audioManager, soundEnabled) {
        object : SoundFeedback {
            override fun playClick() {
                if (soundEnabled) {
                    // Spielt den Standard-Klick-Sound des Systems ab
                    audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK)
                }
            }
        }
    }
    CompositionLocalProvider(LocalSoundFeedback provides safeSound, content = content)
}
