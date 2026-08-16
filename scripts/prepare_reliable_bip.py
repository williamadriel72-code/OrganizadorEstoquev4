from pathlib import Path

path = Path('app/src/main/java/com/organizador/estoque/LauncherActivity.kt')
text = path.read_text(encoding='utf-8')

text = text.replace('import android.media.AudioAttributes\nimport android.media.SoundPool\n', 'import android.media.AudioAttributes\nimport android.media.MediaPlayer\n')

text = text.replace(
'''    private var bipSoundPool: SoundPool? = null
    private var bipSoundId: Int = 0
    @Volatile private var bipSoundReady = false
''',
'''    private var bipPlayer: MediaPlayer? = null
''')

old_prepare = '''    private fun prepareBipSound() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        bipSoundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attributes)
            .build()
            .also { pool ->
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (sampleId == bipSoundId && status == 0) bipSoundReady = true
                }
                bipSoundId = pool.load(this, R.raw.scan_funny, 1)
            }
    }

    private fun playBipSound() {
        val pool = bipSoundPool ?: return
        if (!bipSoundReady || bipSoundId == 0) return
        pool.play(bipSoundId, 1f, 1f, 1, 0, 1f)
    }
'''

new_prepare = '''    private fun prepareBipSound() {
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

if old_prepare not in text:
    raise SystemExit('Bloco atual do bip não encontrado em LauncherActivity.kt')
text = text.replace(old_prepare, new_prepare)

text = text.replace(
'''        bipSoundPool?.release()
        bipSoundPool = null
        bipSoundReady = false
''',
'''        try { bipPlayer?.release() } catch (_: Throwable) {}
        bipPlayer = null
''')

path.write_text(text, encoding='utf-8')
print('Bip configurado no canal de mídia com MediaPlayer e reinício a cada leitura.')
