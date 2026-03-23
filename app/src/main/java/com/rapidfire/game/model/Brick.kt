package com.rapidfire.game.model

import com.rapidfire.game.util.ColorPalette

data class Brick(
    val row: Int,
    val col: Int,
    val shape: BrickShape,
    var value: Int,
    val originalValue: Int = value
) {
    val color: Int get() = ColorPalette.colorForValue(value)
    val isDestroyed: Boolean get() = value <= 0

    fun hit() {
        value--
    }
}
