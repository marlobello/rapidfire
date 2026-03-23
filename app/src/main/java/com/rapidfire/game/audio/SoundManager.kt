package com.rapidfire.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundManager(context: Context) {

    private val soundPool: SoundPool
    private var soundFire: Int = 0
    private var soundBounce: Int = 0
    private var soundExplode: Int = 0
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

        // Load sound resources — these will be added as raw resources later
        // For now, we gracefully handle missing resources
        try {
            val res = context.resources
            val pkg = context.packageName
            val fireId = res.getIdentifier("snd_fire", "raw", pkg)
            val bounceId = res.getIdentifier("snd_bounce", "raw", pkg)
            val explodeId = res.getIdentifier("snd_explode", "raw", pkg)
            val gameOverId = res.getIdentifier("snd_game_over", "raw", pkg)

            if (fireId != 0) soundFire = soundPool.load(context, fireId, 1)
            if (bounceId != 0) soundBounce = soundPool.load(context, bounceId, 1)
            if (explodeId != 0) soundExplode = soundPool.load(context, explodeId, 1)
            if (gameOverId != 0) soundGameOver = soundPool.load(context, gameOverId, 1)
        } catch (_: Exception) {
            // Sound files not yet added — silent mode
        }
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

    fun playGameOver() {
        if (enabled && soundGameOver != 0) soundPool.play(soundGameOver, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
