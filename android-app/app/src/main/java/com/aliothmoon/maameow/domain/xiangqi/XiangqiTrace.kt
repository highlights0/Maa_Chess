package com.aliothmoon.maameow.domain.xiangqi

import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

class XiangqiTrace(operation: String) {
    companion object {
        const val TAG = "XiangqiFlow"
        private val sequence = AtomicLong()
    }
    val id = sequence.incrementAndGet()
    private val started = System.nanoTime()

    init { event("start", "operation=$operation") }

    fun event(stage: String, details: String = "") {
        Timber.tag(TAG).i("flow=%d stage=%s elapsedMs=%d %s", id, stage,
            (System.nanoTime() - started) / 1_000_000, details)
    }

    fun failure(stage: String, error: Throwable) {
        Timber.tag(TAG).e(error, "flow=%d stage=%s elapsedMs=%d", id, stage,
            (System.nanoTime() - started) / 1_000_000)
    }
}
