package com.rapidfire.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.rapidfire.game.R

class SoundManager(context: Context) {

    private val soundPool: SoundPool
    private var soundFire: Int = 0
    private var soundBounce: Int = 0
    private var soundExplode: Int = 0
    private var soundBomb: Int = 0
    private var soundGameOver: Int = 0
    var enabled: Boolean = true

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()

        soundFire = soundPool.load(context, R.raw.snd_fire, 1)
        soundBounce = soundPool.load(context, R.raw.snd_bounce, 1)
        soundExplode = soundPool.load(context, R.raw.snd_explode, 1)
        soundBomb = soundPool.load(context, R.raw.snd_bomb, 1)
        soundGameOver = soundPool.load(context, R.raw.snd_game_over, 1)
    }

    fun playFire() {
        if (enabled && soundFire != 0) soundPool.play(soundFire, 0.5f, 0.5f, 1, 0, 1f)
    }

    fun playBounce() {
        if (enabled && soundBounce != 0) soundPool.play(soundBounce, 0.3f, 0.3f, 1, 0, 1f)
    }

    fun playExplode() {
        if (enabled && soundExplode != 0) soundPool.play(soundExplode, 0.7f, 0.7f, 1, 0, 1f)
    }

    fun playBombExplosion() {
        if (enabled && soundBomb != 0) soundPool.play(soundBomb, 1f, 1f, 1, 0, 1f)
    }

    fun playGameOver() {
        if (enabled && soundGameOver != 0) soundPool.play(soundGameOver, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
