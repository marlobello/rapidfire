package com.rapidfire.game.model

data class Ball(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var active: Boolean = true,
    // Seconds the ball has been alive. Used by GameLoop to despawn balls that
    // get into pathological orbits in high-HP boss rounds (failsafe).
    var ageSecs: Float = 0f
)
