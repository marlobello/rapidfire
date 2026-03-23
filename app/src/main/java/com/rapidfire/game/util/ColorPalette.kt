package com.rapidfire.game.util

import android.graphics.Color

object ColorPalette {
    private val ranges = listOf(
        5 to Color.parseColor("#FF4CAF50"),      // Green: 1-5
        20 to Color.parseColor("#FFFFC828"),     // Gold (matches "Fire" title): 6-20
        50 to Color.parseColor("#FFFF9800"),     // Orange: 21-50
        100 to Color.parseColor("#FFFF5028"),    // Red-orange (matches "Rapid" title): 51-100
        200 to Color.parseColor("#FFE91E63"),    // Pink: 101-200
        300 to Color.parseColor("#FF9C27B0"),    // Purple: 201-300
        400 to Color.parseColor("#FF3F51B5"),    // Indigo: 301-400
        500 to Color.parseColor("#FF2196F3"),    // Blue: 401-500
    )

    private val overflowColor = Color.parseColor("#FF00BCD4") // Cyan: 501+

    fun colorForValue(value: Int): Int {
        for ((maxVal, color) in ranges) {
            if (value <= maxVal) return color
        }
        return overflowColor
    }

    val backgroundColor = Color.parseColor("#FF0D0D0D")
    val cannonColor = Color.parseColor("#FFE0E0E0")
    val ballColor = Color.WHITE
    val aimLineColor = Color.argb(128, 255, 255, 255)
    val hudTextColor = Color.WHITE
}
