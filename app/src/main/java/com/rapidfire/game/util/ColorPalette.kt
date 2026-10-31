package com.rapidfire.game.util

import android.graphics.Color
import androidx.core.graphics.toColorInt

object ColorPalette {
    private val ranges = listOf(
        5 to "#FF4CAF50".toColorInt(),      // Green: 1-5
        20 to "#FFFFC828".toColorInt(),     // Gold (matches "Fire" title): 6-20
        50 to "#FFFF9800".toColorInt(),     // Orange: 21-50
        100 to "#FFFF5028".toColorInt(),    // Red-orange (matches "Rapid" title): 51-100
        200 to "#FFE91E63".toColorInt(),    // Pink: 101-200
        300 to "#FF9C27B0".toColorInt(),    // Purple: 201-300
        400 to "#FF3F51B5".toColorInt(),    // Indigo: 301-400
        500 to "#FF2196F3".toColorInt(),    // Blue: 401-500
    )

    private val overflowColor = "#FF00BCD4".toColorInt() // Cyan: 501+

    fun colorForValue(value: Int): Int {
        for ((maxVal, color) in ranges) {
            if (value <= maxVal) return color
        }
        return overflowColor
    }

    val backgroundColor = "#FF0D0D0D".toColorInt()
    val cannonColor = "#FFE0E0E0".toColorInt()
    val ballColor = Color.WHITE
    val aimLineColor = Color.argb(128, 255, 255, 255)
    val hudTextColor = Color.WHITE
}
