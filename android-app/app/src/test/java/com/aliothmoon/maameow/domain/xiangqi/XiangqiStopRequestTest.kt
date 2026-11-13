package com.aliothmoon.maameow.domain.xiangqi

import com.aliothmoon.maameow.presentation.state.XiangqiRecognitionUiState
import com.aliothmoon.maameow.presentation.state.requestXiangqiStop
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Test

class XiangqiStopRequestTest {
    @Test fun synchronousCancellationCannotLeaveThePanelStopping() = runBlocking {
        val state = MutableStateFlow(XiangqiRecognitionUiState(isAutoPlaying = true))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() }
                finally { state.update { it.copy(isAutoPlaying = false, isStoppingAutomation = false) } }
            }
            requestXiangqiStop(job, state, "stopped", "unverified")
            assertTrue(job.isCompleted)
            assertFalse(state.value.isStoppingAutomation)
            assertFalse(state.value.isAutoPlaying)
            assertEquals("stopped", state.value.automationStatus)
        } finally { scope.cancel() }
    }

    @Test fun pendingCleanupIsShownUntilFinishedAndUnverifiedInputIsPreserved() = runBlocking {
        val state = MutableStateFlow(XiangqiRecognitionUiState(isAutoPlaying = true, inputUnverified = true))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val cleanup = CompletableDeferred<Unit>()
        try {
            val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() }
                finally {
                    withContext(NonCancellable) { cleanup.await() }
                    state.update { it.copy(isStoppingAutomation = false) }
                }
            }
            requestXiangqiStop(job, state, "stopped", "unverified")
            assertFalse(job.isCompleted)
            assertTrue(state.value.isStoppingAutomation)
            assertEquals("unverified", state.value.moveError)
            cleanup.complete(Unit)
            job.join()
            assertFalse(state.value.isStoppingAutomation)
            assertTrue(state.value.inputUnverified)
        } finally { cleanup.complete(Unit); scope.cancel() }
    }
}
