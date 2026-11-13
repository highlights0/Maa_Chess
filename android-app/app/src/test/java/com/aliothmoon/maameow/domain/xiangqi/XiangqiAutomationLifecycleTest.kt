package com.aliothmoon.maameow.domain.xiangqi

import com.aliothmoon.maameow.domain.service.XiangqiAutomationService
import org.junit.Assert.*
import org.junit.Test

class XiangqiAutomationLifecycleTest {
    @Test fun completionOfOldRunCannotStopItsReplacement() {
        val old = XiangqiAutomationService.Companion.Run(1) {}
        val replacement = XiangqiAutomationService.Companion.Run(2) {}
        XiangqiAutomationService.current.value = replacement
        try {
            XiangqiAutomationService.finish(old)
            assertSame(replacement, XiangqiAutomationService.current.value)
            XiangqiAutomationService.finish(replacement)
            assertNull(XiangqiAutomationService.current.value)
        } finally { XiangqiAutomationService.current.value = null }
    }
}
