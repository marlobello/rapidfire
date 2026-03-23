package com.rapidfire.game.model

data class Ball(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var active: Boolean = true
)
