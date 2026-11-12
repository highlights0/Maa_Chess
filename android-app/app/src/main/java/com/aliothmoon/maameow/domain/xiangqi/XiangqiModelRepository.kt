package com.aliothmoon.maameow.domain.xiangqi

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

private const val HEX_DIGITS = "0123456789abcdef"

sealed interface XiangqiModelStatus {
    data object Checking : XiangqiModelStatus
    data object Missing : XiangqiModelStatus
    data object Ready : XiangqiModelStatus
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long = XiangqiModelRepository.MODEL_SIZE_BYTES,
    ) : XiangqiModelStatus
    data class Failed(val message: String) : XiangqiModelStatus
}

/**
 * Downloads the pinned upstream model to app-private storage.
 *
 * The model is not packaged into the APK. A completed download is only made
 * visible after both its exact size and SHA-256 have been verified.
 */
class XiangqiModelRepository(
    context: Context,
    private val httpClient: OkHttpClient,
) {
    companion object {
        const val MODEL_SIZE_BYTES = 31_101_356L
        const val MODEL_SHA256 =
            "da66ba9809f15127f8ae729b1755e42ee61c100c4f9979ce0ef13602ac471298"
        const val MODEL_URL =
            "https://huggingface.co/spaces/yolo12138/Chinese_Chess_Recognition/" +
                "resolve/4bcaf91777effa645040ee76d008d45c2ffc03f3/" +
                "onnx/layout_recognition/nano_v3-0319.onnx"
    }

    private val modelFile = File(context.applicationContext.filesDir, "models/xiangqi-nano-v3.onnx")
    private val mutex = Mutex()
    private val _status = MutableStateFlow<XiangqiModelStatus>(XiangqiModelStatus.Checking)

    val status: StateFlow<XiangqiModelStatus> = _status.asStateFlow()

    suspend fun refreshStatus() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                _status.value = if (isValidModel(modelFile)) {
                    XiangqiModelStatus.Ready
                } else {
                    XiangqiModelStatus.Missing
                }
            }
        }
    }

    suspend fun ensureModel(): File = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (isValidModel(modelFile)) {
                _status.value = XiangqiModelStatus.Ready
                return@withContext modelFile
            }
            downloadLocked()
        }
    }

    suspend fun downloadModel(): File = ensureModel()

    private fun downloadLocked(): File {
        modelFile.parentFile?.let { directory ->
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("无法创建棋子识别模型目录：${directory.absolutePath}")
            }
        }

        val temporary = File(modelFile.parentFile, "${modelFile.name}.download")
        temporary.delete()
        _status.value = XiangqiModelStatus.Downloading(0)

        try {
            val request = Request.Builder()
                .url(MODEL_URL)
                .header("User-Agent", "Maa_Chess-Android/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("模型下载失败：HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("模型下载响应没有内容")
                val declaredLength = body.contentLength()
                if (declaredLength > 0 && declaredLength != MODEL_SIZE_BYTES) {
                    throw IOException("模型文件长度异常：服务器声明 ${declaredLength} bytes")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                body.byteStream().use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            received += count
                            _status.value = XiangqiModelStatus.Downloading(received)
                        }
                        output.fd.sync()
                    }
                }

                val actualHash = digest.digest().toHexString()
                if (received != MODEL_SIZE_BYTES || actualHash != MODEL_SHA256) {
                    throw IOException(
                        "模型校验失败：size=${received}, sha256=${actualHash}",
                    )
                }
            }

            if (modelFile.exists() && !modelFile.delete()) {
                throw IOException("无法替换旧的象棋识别模型")
            }
            if (!temporary.renameTo(modelFile)) {
                throw IOException("无法将已验证的模型写入应用目录")
            }

            _status.value = XiangqiModelStatus.Ready
            return modelFile
        } catch (error: Exception) {
            _status.value = XiangqiModelStatus.Failed(
                error.message ?: error.javaClass.simpleName,
            )
            throw error
        } finally {
            temporary.delete()
        }
    }

    private fun isValidModel(file: File): Boolean {
        if (!file.isFile || file.length() != MODEL_SIZE_BYTES) return false
        return runCatching { sha256(file) == MODEL_SHA256 }.getOrDefault(false)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String = buildString(size * 2) {
        for (value in this@toHexString) {
            append(HEX_DIGITS[(value.toInt() ushr 4) and 0x0f])
            append(HEX_DIGITS[value.toInt() and 0x0f])
        }
    }

}
