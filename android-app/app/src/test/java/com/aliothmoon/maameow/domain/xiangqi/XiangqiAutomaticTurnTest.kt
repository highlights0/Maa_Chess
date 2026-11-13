package com.aliothmoon.maameow.domain.xiangqi

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class XiangqiAutomaticTurnTest {
    @Test fun continuousHintsAnalyzeEveryTurnWithoutSendingAnyInput() = runTest {
        var analyses = 0
        repeat(4) {
            runXiangqiAutomaticTurn(hintsOnly = true, analyze = { analyses++ },
                execute = { error("Hints must never send touch input") })
        }
        assertEquals(4, analyses)
    }

    @Test fun automaticPlayAnalyzesBeforeExecutingAndFailureNeverSendsInput() = runTest {
        val actions = mutableListOf<String>()
        runXiangqiAutomaticTurn(hintsOnly = false, analyze = { actions += "analyze" }, execute = { actions += "execute" })
        assertEquals(listOf("analyze", "execute"), actions)
        try {
            runXiangqiAutomaticTurn(hintsOnly = false, analyze = { error("Unconfirmed position") },
                execute = { fail("Failed analysis must never send input") })
            fail("Analysis must fail")
        } catch (_: IllegalStateException) { }
    }

    @Test fun stoppingDuringAnalysisPreventsTheNextInput() = runTest {
        var touched = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            runXiangqiAutomaticTurn(hintsOnly = false, analyze = { awaitCancellation() }, execute = { touched = true })
        }
        job.cancelAndJoin()
        assertFalse(touched)
    }
}
