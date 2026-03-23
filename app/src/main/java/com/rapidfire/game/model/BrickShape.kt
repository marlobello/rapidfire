package com.rapidfire.game.model

enum class BrickShape {
    SQUARE,
    TRIANGLE_TL, // right angle at top-left, hypotenuse from top-right to bottom-left
    TRIANGLE_TR, // right angle at top-right, hypotenuse from top-left to bottom-right
    TRIANGLE_BL, // right angle at bottom-left, hypotenuse from top-left to bottom-right
    TRIANGLE_BR  // right angle at bottom-right, hypotenuse from top-right to bottom-left
}
