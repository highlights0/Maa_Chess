package com.aliothmoon.maameow.domain.xiangqi

import com.aliothmoon.maameow.data.log.ApplicationLogWriter
import com.aliothmoon.maameow.utils.log.FileLogTree
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import timber.log.Timber

class XiangqiTraceTest {
    @Test fun normalOperationLogsArePersistedWithDebugDisabled() {
        val writer = mockk<ApplicationLogWriter>(relaxed = true)
        val tree = FileLogTree(writer, MutableStateFlow(false))
        Timber.plant(tree)
        try {
            XiangqiTrace("recognize").event("capture", "bytes=123")
            verify { writer.submit(any(), XiangqiTrace.TAG, match { it.contains("stage=capture") }, any()) }
        } finally { Timber.uproot(tree) }
    }
}
