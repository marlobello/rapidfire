package com.rapidfire.game.util

object Constants {
    // Grid dimensions
    const val GRID_COLUMNS = 7
    const val GRID_ROWS = 9
    const val ACTIVE_ROWS = 8 // Row 9 (index 8) is always empty, Row 8 (index 7) gets new bricks

    // Physics
    const val BALL_RADIUS = 16f
    const val BALL_SPEED = 1200f // pixels per second
    const val FIRE_DELAY_MS = 80L // delay between successive ball firings

    // Minimum |vy|/|v| ratio enforced after every reflection. Prevents balls
    // converging onto a near-horizontal trajectory (looks like "rolling" and
    // can produce stable orbits between left/right walls that never reach
    // the baseline). 0.15 ≈ 8.6° from horizontal.
    const val MIN_VERTICAL_VELOCITY_RATIO = 0.15f

    // Cap a single CCD substep at one normal-frame's worth of motion. In
    // turbo mode the effective dt is 4× normal, so we run 4 sub-frames.
    // Keeps multi-brick collision resolution stable.
    const val MAX_PHYSICS_SUBSTEP_SECS = 1f / 60f

    // Cannon — doubled from previous
    const val MIN_AIM_ANGLE_DEG = 15f // minimum angle from horizon (both sides)
    const val MAX_AIM_ANGLE_DEG = 165f // 180 - MIN = max from left horizon
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
