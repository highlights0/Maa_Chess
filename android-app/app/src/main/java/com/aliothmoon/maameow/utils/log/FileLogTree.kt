package com.aliothmoon.maameow.utils.log

import android.util.Log
import com.aliothmoon.maameow.data.log.ApplicationLogWriter
import com.aliothmoon.maameow.domain.xiangqi.XiangqiTrace
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber


class FileLogTree(
    private val writer: ApplicationLogWriter,
    private val debugMode: StateFlow<Boolean>,
) : Timber.DebugTree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >= Log.WARN || debugMode.value || tag == XiangqiTrace.TAG
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        writer.submit(priority, tag, message, t)
    }
}
