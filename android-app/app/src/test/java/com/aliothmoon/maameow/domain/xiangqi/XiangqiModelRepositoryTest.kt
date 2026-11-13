package com.aliothmoon.maameow.domain.xiangqi

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

class XiangqiModelRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun repository(asset: AssetManager): XiangqiModelRepository {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.filesDir } returns folder.root
        every { context.assets } returns asset
        return XiangqiModelRepository(context, OkHttpClient())
    }

    @Test fun corruptBundledModelIsNeverInstalled() = runBlocking {
        val assets = mockk<AssetManager>()
        every { assets.open("xiangqi-nano-v3.onnx") } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))
        val repository = repository(assets)
        repository.refreshStatus()
        assertTrue(repository.status.value is XiangqiModelStatus.Failed)
        assertFalse(folder.root.resolve("models/xiangqi-nano-v3.onnx").exists())
        assertFalse(folder.root.resolve("models/xiangqi-nano-v3.onnx.install").exists())
    }

    @Test fun customBuildWithoutAssetShowsMissingInsteadOfCrashing() = runBlocking {
        val assets = mockk<AssetManager>()
        every { assets.open("xiangqi-nano-v3.onnx") } throws FileNotFoundException()
        val repository = repository(assets)
        repository.refreshStatus()
        assertEquals(XiangqiModelStatus.Missing, repository.status.value)
    }
}
