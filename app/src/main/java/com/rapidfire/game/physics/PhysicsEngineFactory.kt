package com.rapidfire.game.physics

/**
 * Selects which physics engine implementation the game uses.
 *
 * This is intentionally an internal compile-time switch — there is no user-facing
 * setting. To swap engines, change [USE_V2] and rebuild.
 *
 * v2 (substep + MTV) is the default as of v1.3.0. Simulation across 12,000 shots
 * showed v2 reduces "trapped ball" anomalies in pathological scenarios (high-HP
 * dense boards, especially in turbo) by roughly 30× vs v1, while remaining
 * gameplay-equivalent for normal play.
 *
 * If the v2 engine ever needs to be reverted in the field, set [USE_V2] = false
 * and ship a hotfix build — both engines are kept side-by-side for that reason.
 */
object PhysicsEngineFactory {
    /** When true, the game uses [CollisionDetectorV2]; when false, the legacy [CollisionDetector]. */
    private const val USE_V2 = true

    fun create(): PhysicsEngine =
        if (USE_V2) CollisionDetectorV2() else CollisionDetector()
}
