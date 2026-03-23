package com.rapidfire.game.engine

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class VisualEffects {

    data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float,       // 1.0 → 0.0
        val maxLife: Float,
        val color: Int,
        val size: Float
    )

    data class ScorePopup(
        var x: Float, var y: Float,
        var life: Float,       // 1.0 → 0.0
        val text: String,
        val color: Int
    )

    data class MuzzleFlash(
        var x: Float, var y: Float,
        var life: Float        // 1.0 → 0.0
    )

    data class ButtonPopup(
        var x: Float, var y: Float,
        var life: Float,       // 1.0 → 0.0
        val text: String
    )

    val particles = mutableListOf<Particle>()
    val scorePopups = mutableListOf<ScorePopup>()
    val buttonPopups = mutableListOf<ButtonPopup>()
    var muzzleFlash: MuzzleFlash? = null
        private set

    /** Spawn brick destruction particles at the given screen position */
    fun spawnBrickParticles(cx: Float, cy: Float, brickColor: Int, count: Int = 8) {
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = 150f + Random.nextFloat() * 250f
            particles.add(
                Particle(
                    x = cx, y = cy,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    life = 1f,
                    maxLife = 0.5f + Random.nextFloat() * 0.3f,
                    color = brickColor,
                    size = 4f + Random.nextFloat() * 6f
                )
            )
        }
    }

    /** Spawn a floating score popup */
    fun spawnScorePopup(x: Float, y: Float, points: Int) {
        scorePopups.add(
            ScorePopup(
                x = x, y = y,
                life = 1f,
                text = "+$points",
                color = android.graphics.Color.WHITE
            )
        )
    }

    /** Spawn a muzzle flash at barrel tip */
    fun spawnMuzzleFlash(x: Float, y: Float) {
        muzzleFlash = MuzzleFlash(x = x, y = y, life = 1f)
    }

    /** Spawn a "+1" popup over a button position */
    fun spawnButtonPopup(x: Float, y: Float, text: String = "+1") {
        buttonPopups.add(ButtonPopup(x = x, y = y, life = 1f, text = text))
    }

    /** Advance all effects by dt seconds */
    fun update(dt: Float) {
        // Particles
        val pIter = particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += 300f * dt  // gravity
            p.life -= dt / p.maxLife
            if (p.life <= 0f) pIter.remove()
        }

        // Score popups — drift up and fade
        val sIter = scorePopups.iterator()
        while (sIter.hasNext()) {
            val s = sIter.next()
            s.y -= 80f * dt   // float upward
            s.life -= dt / 0.8f  // fade over 0.8 seconds
            if (s.life <= 0f) sIter.remove()
        }

        // Muzzle flash — very brief
        muzzleFlash?.let {
            it.life -= dt / 0.08f  // fade over ~80ms
            if (it.life <= 0f) muzzleFlash = null
        }

        // Button popups — float up and fade
        val bIter = buttonPopups.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            b.y -= 100f * dt    // float upward
            b.life -= dt / 1.0f // fade over 1 second
            if (b.life <= 0f) bIter.remove()
        }
    }

    fun clear() {
        particles.clear()
        scorePopups.clear()
        buttonPopups.clear()
        muzzleFlash = null
    }
}
