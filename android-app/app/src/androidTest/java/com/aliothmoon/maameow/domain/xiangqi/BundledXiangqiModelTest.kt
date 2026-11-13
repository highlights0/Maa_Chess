package com.aliothmoon.maameow.domain.xiangqi

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class BundledXiangqiModelTest {
    @Test fun installsTheVerifiedModelWithoutNetwork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "models/xiangqi-nano-v3.onnx").delete()
        val client = OkHttpClient.Builder().addInterceptor { error("Network must not be used") }.build()
        val repository = XiangqiModelRepository(context, client)
        repository.refreshStatus()
        assertEquals(XiangqiModelStatus.Ready, repository.status.value)
        assertEquals(XiangqiModelRepository.MODEL_SIZE_BYTES, repository.ensureModel().length())
    }
}
