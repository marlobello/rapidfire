package com.rapidfire.game.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererTest {
    @Test
    fun `bomb pulse changes between frames even at large timestamps`() {
        val timeMs = 1_800_000_000_000L

        assertTrue(Renderer.bombPulseAmount(timeMs + 16L) > Renderer.bombPulseAmount(timeMs))
    }

    @Test
    fun `bomb pulse reaches both colors and repeats smoothly`() {
        val timeMs = 1_800_000_000_000L

        assertEquals(0.5f, Renderer.bombPulseAmount(timeMs), 0.0001f)
        assertEquals(1f, Renderer.bombPulseAmount(timeMs + 450L), 0.0001f)
        assertEquals(0.5f, Renderer.bombPulseAmount(timeMs + 900L), 0.0001f)
        assertEquals(0f, Renderer.bombPulseAmount(timeMs + 1350L), 0.0001f)
        assertEquals(
            Renderer.bombPulseAmount(timeMs + 16L),
            Renderer.bombPulseAmount(timeMs + 1816L),
            0.0001f
        )
    }
}
