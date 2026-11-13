package com.aliothmoon.maameow.domain.xiangqi

import org.junit.Assert.*
import org.junit.Test

class XiangqiOrientationTest {
    private data class Frame(val flipped: Boolean, val validPalaces: Boolean, val uncertain: Int)

    @Test fun blackAtBottomNeedsOnlyOneInferenceAfterOrientationIsKnown() {
        val calls = mutableListOf<Boolean>()
        val result = selectXiangqiOrientation(true, { calls += it; Frame(it, it, 0) }, { it.validPalaces }, { it.uncertain })
        assertTrue(result.flipped)
        assertEquals(listOf(true), calls)
    }

    @Test fun aNewGameWithOppositeOrientationIsDetectedAndTransientUncertaintyDoesNotDoubleWork() {
        val calls = mutableListOf<Boolean>()
        val result = selectXiangqiOrientation(true, { calls += it; Frame(it, !it, 0) }, { it.validPalaces }, { it.uncertain })
        assertFalse(result.flipped)
        assertEquals(listOf(true, false), calls)
        calls.clear()
        val uncertain = selectXiangqiOrientation(false, { calls += it; Frame(it, true, 1) }, { it.validPalaces }, { it.uncertain })
        assertEquals(1, uncertain.uncertain)
        assertEquals(listOf(false), calls)
    }
}
