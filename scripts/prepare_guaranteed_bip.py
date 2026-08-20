from pathlib import Path

path = Path('app/src/main/java/com/organizador/estoque/LauncherActivity.kt')
text = path.read_text(encoding='utf-8')

if 'import android.media.AudioManager' not in text:
    text = text.replace('import android.media.AudioAttributes\nimport android.media.MediaPlayer\n', 'import android.media.AudioAttributes\nimport android.media.AudioManager\nimport android.media.MediaPlayer\nimport android.media.ToneGenerator\n')

text = text.replace(
'''    private var bipPlayer: MediaPlayer? = null
''',
'''    private var bipPlayer: MediaPlayer? = null
    private var bipFallbackTone: ToneGenerator? = null
''')

old = '''    private fun prepareBipSound() {
        bipPlayer?.release()
        bipPlayer = MediaPlayer.create(this, R.raw.scan_funny)?.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setVolume(1f, 1f)
            setOnCompletionListener { player ->
                try { player.seekTo(0) } catch (_: Throwable) {}
            }
        }
    }

    private fun playBipSound() {
        runOnUiThread {
            try {
                var player = bipPlayer
                if (player == null) {
                    prepareBipSound()
                    player = bipPlayer
                }
                if (player == null) return@runOnUiThread
                if (player.isPlaying) {
                    player.pause()
                }
                player.seekTo(0)
                player.start()
            } catch (_: Throwable) {
                try {
                    bipPlayer?.release()
                } catch (_: Throwable) {}
                bipPlayer = null
                prepareBipSound()
                try {
                    bipPlayer?.seekTo(0)
                    bipPlayer?.start()
                } catch (_: Throwable) {}
            }
        }
    }
'''

new = '''    private fun prepareBipSound() {
        try { bipPlayer?.release() } catch (_: Throwable) {}
        bipPlayer = null
        try {
            val afd = resources.openRawResourceFd(R.raw.scan_funny)
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            player.setVolume(1f, 1f)
            player.prepare()
            player.setOnCompletionListener { p -> try { p.seekTo(0) } catch (_: Throwable) {} }
            player.setOnErrorListener { _, _, _ ->
                playFallbackBip()
                true
            }
            bipPlayer = player
        } catch (_: Throwable) {
            bipPlayer = null
        }
    }

    private fun playFallbackBip() {
        try {
            if (bipFallbackTone == null) {
                bipFallbackTone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            }
            bipFallbackTone?.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        } catch (_: Throwable) {}
    }

    private fun playBipSound() {
        runOnUiThread {
            try {
                if (bipPlayer == null) prepareBipSound()
                val player = bipPlayer
                if (player == null) {
                    playFallbackBip()
                    return@runOnUiThread
                }
                if (player.isPlaying) player.pause()
                player.seekTo(0)
                player.start()
                webView.postDelayed({
                    try {
                        if (bipPlayer?.isPlaying != true) playFallbackBip()
                    } catch (_: Throwable) {
                        playFallbackBip()
                    }
                }, 60L)
            } catch (_: Throwable) {
                try { bipPlayer?.release() } catch (_: Throwable) {}
                bipPlayer = null
                prepareBipSound()
                try {
                    bipPlayer?.seekTo(0)
                    bipPlayer?.start()
                } catch (_: Throwable) {
                    playFallbackBip()
                }
            }
        }
    }
'''

if old not in text:
    raise SystemExit('Bloco MediaPlayer do bip não encontrado após prepare_reliable_bip.py')
text = text.replace(old, new)

text = text.replace(
'''        try { bipPlayer?.release() } catch (_: Throwable) {}
        bipPlayer = null
''',
'''        try { bipPlayer?.release() } catch (_: Throwable) {}
        bipPlayer = null
        try { bipFallbackTone?.release() } catch (_: Throwable) {}
        bipFallbackTone = null
''',
1
)

checks = [
    'ToneGenerator(AudioManager.STREAM_MUSIC, 100)',
    'resources.openRawResourceFd(R.raw.scan_funny)',
    'playFallbackBip()',
    'player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)',
]
missing = [x for x in checks if x not in text]
if missing:
    raise SystemExit('Bip garantido incompleto: ' + ', '.join(missing))

path.write_text(text, encoding='utf-8')
print('Bip reforçado: áudio personalizado preparado corretamente + tom de fallback Android.')
