package com.stockmaster.clone.ui

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Bip curto em dois tons. Só deve ser chamado quando uma leitura de código for concluída com sucesso.
 */
internal fun playAwsFunnyScanBip() {
    Thread {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        try {
            tone.startTone(ToneGenerator.TONE_DTMF_9, 70)
            Thread.sleep(80)
            tone.startTone(ToneGenerator.TONE_DTMF_3, 105)
            Thread.sleep(120)
        } catch (_: Exception) {
            // O som é complementar: uma falha de áudio nunca deve impedir a leitura.
        } finally {
            tone.release()
        }
    }.start()
}
