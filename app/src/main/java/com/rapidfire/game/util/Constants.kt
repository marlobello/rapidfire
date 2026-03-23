package com.rapidfire.game.util

object Constants {
    // Grid dimensions
    const val GRID_COLUMNS = 7
    const val GRID_ROWS = 9
    const val ACTIVE_ROWS = 8 // Row 9 (index 8) is always empty, Row 8 (index 7) gets new bricks

    // Physics
    const val BALL_RADIUS = 16f
    const val BALL_SPEED = 1200f // pixels per second
    const val CORNER_RADIUS = 3f // small radius at brick corners
    const val FIRE_DELAY_MS = 80L // delay between successive ball firings

    // Cannon — doubled from previous
    const val MIN_AIM_ANGLE_DEG = 10f // minimum angle from horizon (both sides)
    const val MAX_AIM_ANGLE_DEG = 170f // 180 - MIN = max from left horizon
    const val CANNON_WIDTH = 160f
    const val CANNON_HEIGHT = 200f
    const val BARREL_LENGTH = 120f
    const val BARREL_WIDTH = 40f

    // Game loop
    const val TARGET_FPS = 60
    const val FRAME_TIME_MS = 1000L / TARGET_FPS
    const val MAX_FRAME_SKIP = 5

    // Spacing
    const val BRICK_SPACING = 4f // gap between bricks
    const val BOARD_PADDING = 8f // padding around the board edges

    // Power-ups
    const val TURBO_SPEED_MULTIPLIER = 4f

    // Animations
    const val CANNON_SLIDE_SPEED = 2000f // pixels per second
    const val BRICK_SHIFT_SPEED = 4f     // cells per second (1 cell in ~0.25s)

    // Board clear fanfare
    const val BOARD_CLEAR_BONUS = 500
    const val BOARD_CLEAR_DISPLAY_SECS = 2.0f  // how long the banner shows
}
