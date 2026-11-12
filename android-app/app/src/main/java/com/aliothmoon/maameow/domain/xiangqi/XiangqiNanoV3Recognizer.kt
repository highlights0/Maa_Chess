package com.aliothmoon.maameow.domain.xiangqi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

data class XiangqiBoardCalibration(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
    val sourceWidth: Int = 900,
    val sourceHeight: Int = 1600,
) {
    fun scaledTo(width: Int, height: Int): XiangqiBoardCalibration {
        require(width > 0 && height > 0) { "截图尺寸无效" }
        val oldRatio = sourceWidth.toDouble() / sourceHeight
        val newRatio = width.toDouble() / height
        require(abs(oldRatio - newRatio) / oldRatio <= 0.01) {
            "截图宽高比与棋盘校准不一致，请重新校准"
        }
        val scaleX = width.toDouble() / sourceWidth
        val scaleY = height.toDouble() / sourceHeight
        return copy(
            left = left * scaleX,
            top = top * scaleY,
            right = right * scaleX,
            bottom = bottom * scaleY,
            sourceWidth = width,
            sourceHeight = height,
        )
    }

    fun validate(width: Int, height: Int) {
        require(right > left && bottom > top) { "棋盘四角顺序或位置无效" }
        require(left >= 0 && top >= 0 && right < width && bottom < height) {
            "棋盘校准点超出当前截图范围"
        }
        val dx = (right - left) / 8.0
        val dy = (bottom - top) / 9.0
        require(dx >= 12.0 && dy >= 12.0) { "棋盘分辨率过低" }
    }

    companion object {
        // Measured against the user's 900x1600 MuMu internal screenshot.
        val MUMU_900X1600 = XiangqiBoardCalibration(
            left = 66.0,
            top = 363.0,
            right = 834.0,
            bottom = 1227.0,
        )

        // Measured from the user's 1080x2400 standard and puzzle screenshots.
        val PHONE_1080X2400 = XiangqiBoardCalibration(
            left = 80.0,
            top = 665.0,
            right = 1000.0,
            bottom = 1690.0,
            sourceWidth = 1080,
            sourceHeight = 2400,
        )
    }
}

data class XiangqiSquare(val row: Int, val col: Int)

data class XiangqiPiecePrediction(
    val row: Int,
    val col: Int,
    val symbol: Char,
    val confidence: Float,
)

data class XiangqiRecognitionResult(
    val rows: List<String>,
    val calibration: XiangqiBoardCalibration,
    val fenBoard: String?,
    val predictions: List<XiangqiPiecePrediction>,
    val uncertainSquares: List<XiangqiSquare>,
    val meanConfidence: Float,
    val redAtTopOnScreen: Boolean,
    val problemCode: String?,
) {
    val isComplete: Boolean
        get() = fenBoard != null && uncertainSquares.isEmpty() && problemCode == null
}

/**
 * On-device nano-v3 inference. The model is the upstream 90-intersection,
 * 16-class classifier; no per-piece crops or locally trained weights are used.
 */
class XiangqiNanoV3Recognizer(
    private val modelRepository: XiangqiModelRepository,
) : AutoCloseable {
    companion object {
        private const val INPUT_HEIGHT = 315
        private const val INPUT_WIDTH = 280
        private const val OUTPUT_SQUARES = 90
        private const val OUTPUT_CLASSES = 16
        private const val MIN_CONFIDENCE = 0.80f
        const val PROBLEM_GENERAL_COUNT = "missing_or_duplicate_general"
        private const val CLASS_SYMBOLS = ".xKABNRCPkabnrcp"
        private val MEAN = doubleArrayOf(123.675, 116.28, 103.53)
        private val STD = doubleArrayOf(58.395, 57.12, 57.375)
    }

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val inferenceMutex = Mutex()
    private val sessionLock = Any()
    @Volatile private var session: OrtSession? = null
    @Volatile private var sessionPath: String? = null
    private var inputName: String? = null
    private var outputName: String? = null

    val modelStatus = modelRepository.status

    suspend fun refreshModelStatus() = modelRepository.refreshStatus()

    suspend fun downloadModel(): File = modelRepository.downloadModel()

    suspend fun recognizeJpeg(
        jpeg: ByteArray,
        calibration: XiangqiBoardCalibration = XiangqiBoardCalibration.MUMU_900X1600,
        redAtTopOnScreen: Boolean? = null,
    ): XiangqiRecognitionResult {
        require(jpeg.isNotEmpty()) { "截图数据为空" }
        val modelFile = modelRepository.ensureModel()
        return withContext(Dispatchers.Default) {
            val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                ?: throw IOException("无法解码象棋画面")
            try {
                val scaledCalibration = calibration.scaledTo(bitmap.width, bitmap.height)
                scaledCalibration.validate(bitmap.width, bitmap.height)
                inferenceMutex.withLock {
                    if (redAtTopOnScreen != null) {
                        infer(bitmap, scaledCalibration, redAtTopOnScreen, modelFile).publicResult
                    } else {
                        val normal = infer(bitmap, scaledCalibration, false, modelFile)
                        if (normal.orientationIsValid) {
                            normal.publicResult
                        } else {
                            val flipped = infer(bitmap, scaledCalibration, true, modelFile)
                            when {
                                flipped.orientationIsValid -> flipped.publicResult
                                flipped.publicResult.uncertainSquares.size < normal.publicResult.uncertainSquares.size -> flipped.publicResult
                                else -> normal.publicResult
                            }
                        }
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun infer(
        bitmap: Bitmap,
        calibration: XiangqiBoardCalibration,
        redAtTopOnScreen: Boolean,
        modelFile: File,
    ): InternalResult {
        val input = preprocess(bitmap, calibration, redAtTopOnScreen)
        val activeSession = getSession(modelFile)
        val directBuffer = ByteBuffer.allocateDirect(input.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        directBuffer.put(input)
        directBuffer.rewind()

        OnnxTensor.createTensor(
            environment,
            directBuffer,
            longArrayOf(1, 3, INPUT_HEIGHT.toLong(), INPUT_WIDTH.toLong()),
        ).use { inputTensor ->
            activeSession.run(mapOf(inputName!! to inputTensor)).use { result ->
                val tensor = result.get(outputName!!).orElse(null) as? OnnxTensor
                    ?: throw IOException("nano-v3 模型输出不是 Tensor")
                val shape = (tensor.info as TensorInfo).shape
                if (!shape.contentEquals(longArrayOf(1, OUTPUT_SQUARES.toLong(), OUTPUT_CLASSES.toLong()))) {
                    throw IOException("nano-v3 模型输出尺寸不匹配：${shape.contentToString()}")
                }
                val buffer = tensor.floatBuffer
                if (buffer.remaining() != OUTPUT_SQUARES * OUTPUT_CLASSES) {
                    throw IOException("nano-v3 模型输出数据长度不匹配")
                }
                val probabilities = FloatArray(OUTPUT_SQUARES * OUTPUT_CLASSES)
                buffer.get(probabilities)
                return decode(probabilities, redAtTopOnScreen, calibration)
            }
        }
    }

    private fun getSession(modelFile: File): OrtSession = synchronized(sessionLock) {
        val existing = session
        if (existing != null && sessionPath == modelFile.absolutePath) return existing

        existing?.close()
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            val created = environment.createSession(modelFile.absolutePath, options)
            val inputs = created.inputNames.toList()
            val outputs = created.outputNames.toList()
            if (inputs.size != 1 || outputs.size != 1) {
                created.close()
                throw IOException("nano-v3 模型必须只有一个输入和一个输出")
            }
            inputName = inputs.single()
            outputName = outputs.single()
            sessionPath = modelFile.absolutePath
            session = created
            created
        } finally {
            options.close()
        }
    }

    private fun preprocess(
        bitmap: Bitmap,
        calibration: XiangqiBoardCalibration,
        redAtTopOnScreen: Boolean,
    ): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return XiangqiNanoV3Preprocessor.preprocessArgb(
            pixels = pixels,
            width = width,
            height = height,
            calibration = calibration,
            redAtTopOnScreen = redAtTopOnScreen,
        )
    }

    private fun decode(
        probabilities: FloatArray,
        redAtTopOnScreen: Boolean,
        calibration: XiangqiBoardCalibration,
    ): InternalResult {
        val rows = Array(10) { CharArray(9) { '.' } }
        val predictions = ArrayList<XiangqiPiecePrediction>(OUTPUT_SQUARES)
        val uncertain = linkedSetOf<XiangqiSquare>()
        var confidenceSum = 0.0

        for (index in 0 until OUTPUT_SQUARES) {
            val row = index / 9
            val col = index % 9
            val offset = index * OUTPUT_CLASSES
            var best = 0
            var bestScore = Float.NEGATIVE_INFINITY
            for (classIndex in 0 until OUTPUT_CLASSES) {
                val score = probabilities[offset + classIndex]
                if (!score.isFinite() || score < 0f || score > 1.001f) {
                    throw IOException("nano-v3 模型返回无效概率")
                }
                if (score > bestScore) {
                    bestScore = score
                    best = classIndex
                }
            }

            val symbol = CLASS_SYMBOLS[best]
            confidenceSum += bestScore
            predictions += XiangqiPiecePrediction(row, col, symbol, bestScore)
            if (symbol == 'x' || bestScore < MIN_CONFIDENCE) {
                uncertain += XiangqiSquare(row, col)
                rows[row][col] = symbol
            } else {
                rows[row][col] = symbol
            }
        }

        val positionProblem = validatePosition(rows, uncertain)
        val orientationValid = hasCorrectKingPalaces(rows)
        val fenBoard = if (uncertain.isEmpty() && positionProblem == null) {
            rows.joinToString("/") { compressFenRow(it) }
        } else {
            null
        }
        val result = XiangqiRecognitionResult(
            rows = rows.map { String(it) },
            calibration = calibration,
            fenBoard = fenBoard,
            predictions = predictions,
            uncertainSquares = uncertain.toList(),
            meanConfidence = (confidenceSum / OUTPUT_SQUARES).toFloat(),
            redAtTopOnScreen = redAtTopOnScreen,
            problemCode = positionProblem,
        )
        return InternalResult(result, orientationValid)
    }

    private fun validatePosition(
        rows: Array<CharArray>,
        uncertain: MutableSet<XiangqiSquare>,
    ): String? {
        val counts = mutableMapOf<Char, MutableList<XiangqiSquare>>()
        for (row in 0 until 10) {
            for (col in 0 until 9) {
                val piece = rows[row][col]
                if (piece == '.' || piece == 'x') continue
                val square = XiangqiSquare(row, col)
                counts.getOrPut(piece) { mutableListOf() } += square
                if (!canOccupy(piece, row, col)) uncertain += square
            }
        }

        val maximum = mapOf(
            'K' to 1, 'A' to 2, 'B' to 2, 'N' to 2, 'R' to 2, 'C' to 2, 'P' to 5,
            'k' to 1, 'a' to 2, 'b' to 2, 'n' to 2, 'r' to 2, 'c' to 2, 'p' to 5,
        )
        for ((piece, squares) in counts) {
            if (squares.size > (maximum[piece] ?: 0)) uncertain += squares
        }

        if (counts['K']?.size != 1 || counts['k']?.size != 1) {
            return PROBLEM_GENERAL_COUNT
        }
        return null
    }

    private fun canOccupy(piece: Char, row: Int, col: Int): Boolean = when (piece) {
        'K', 'A' -> row in 7..9 && col in 3..5
        'k', 'a' -> row in 0..2 && col in 3..5
        'B' -> row in 5..9
        'b' -> row in 0..4
        'P' -> row in 0..6
        'p' -> row in 3..9
        '.', 'N', 'R', 'C', 'n', 'r', 'c' -> true
        else -> false
    }

    private fun hasCorrectKingPalaces(rows: Array<CharArray>): Boolean {
        val redKings = mutableListOf<XiangqiSquare>()
        val blackKings = mutableListOf<XiangqiSquare>()
        for (row in 0 until 10) {
            for (col in 0 until 9) {
                when (rows[row][col]) {
                    'K' -> redKings += XiangqiSquare(row, col)
                    'k' -> blackKings += XiangqiSquare(row, col)
                }
            }
        }
        return redKings.size == 1 && blackKings.size == 1 &&
            redKings.single().row in 7..9 && redKings.single().col in 3..5 &&
            blackKings.single().row in 0..2 && blackKings.single().col in 3..5
    }

    private fun compressFenRow(row: CharArray): String = buildString {
        var emptyCount = 0
        for (piece in row) {
            if (piece == '.') {
                emptyCount++
            } else {
                if (emptyCount > 0) {
                    append(emptyCount)
                    emptyCount = 0
                }
                append(piece)
            }
        }
        if (emptyCount > 0) append(emptyCount)
    }

    override fun close() {
        synchronized(sessionLock) {
            session?.close()
            session = null
            sessionPath = null
            inputName = null
            outputName = null
        }
    }

    private data class InternalResult(
        val publicResult: XiangqiRecognitionResult,
        val orientationIsValid: Boolean,
    )
}
