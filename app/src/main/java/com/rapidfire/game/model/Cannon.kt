package com.rapidfire.game.model

data class Cannon(
    var x: Float,           // pixel position along baseline (center of cannon)
    var aimAngle: Float = 90f, // degrees, 90 = straight up, measured from right horizon
    var ammo: Int = 1,
    var isAiming: Boolean = false,
    var isFiring: Boolean = false
)
