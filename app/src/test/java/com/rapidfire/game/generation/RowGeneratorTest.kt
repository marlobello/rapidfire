package com.rapidfire.game.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RowGeneratorTest {

    @Test
    fun `default bomb spawn probability is three percent`() {
        assertEquals(0.03f, GenerationParams().bombProbability, 0f)
    }

    @Test
    fun `bomb probability controls generated brick type`() {
        val alwaysBombs = RowGenerator(GenerationParams(bombProbability = 1f))
        val neverBombs = RowGenerator(GenerationParams(bombProbability = 0f))

        assertTrue(alwaysBombs.generateRow(10).all { it.isBomb })
        assertTrue(neverBombs.generateRow(10).none { it.isBomb })
    }
}
