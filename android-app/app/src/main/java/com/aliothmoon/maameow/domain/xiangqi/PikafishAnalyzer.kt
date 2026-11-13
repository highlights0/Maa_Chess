package com.aliothmoon.maameow.domain.xiangqi

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

data class XiangqiAnalysis(
    val side: Char,
    val bestMove: String,
    val depth: Int?,
    val score: String?,
    val variation: List<String>,
    val positionCommand: String,
)

/** Runs the pinned arm64 release from Android's executable native-library directory. */
class PikafishAnalyzer(context: Context) : AutoCloseable {
    private companion object {
        const val ENGINE_SHA = "6c06b8752e10c1ed605fa836d2c9bbf885e9c402b216023040ddf4586f4320b1"
        const val NNUE_SHA = "7d13d73569a9b571ba0eb20cf1596247bc2a42738967e61afef6482b231e900e"
        const val EOF = "<pikafish-eof>"
    }

    private val app = context.applicationContext
    private val binary = File(app.applicationInfo.nativeLibraryDir, "libpikafish.so")
    private val network = File(app.filesDir, "engines/pikafish.nnue")
    private val mutex = Mutex()
    private var process: Process? = null
    private var output: LinkedBlockingQueue<String>? = null

    val isSupported: Boolean get() = binary.isFile

    suspend fun analyze(session: XiangqiGameSession, trace: XiangqiTrace = XiangqiTrace("engine")): XiangqiAnalysis = withContext(Dispatchers.IO) {
        val legal = XiangqiRules.legalMoves(session.rows, session.side).map { it.iccs }.toSet()
        require(legal.isNotEmpty()) { "No legal moves remain" }
        mutex.withLock {
            try {
                start()
                trace.event("engine.ready", "localLegalMoves=${legal.size}")
                send("ucinewgame")
                send("isready")
                waitFor("readyok", 10_000)
                send(session.positionCommand)
                send("d")
                send("isready")
                var engineFen: String? = null
                val verifyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (true) {
                    val line = readLine(verifyDeadline)
                    if (line.startsWith("info string CRITICAL ERROR")) throw IOException(line)
                    if (line.startsWith("Fen: ")) engineFen = line.removePrefix("Fen: ")
                    if (line == "readyok") break
                }
                require(engineFen?.split(' ')?.take(2) == listOf(XiangqiRules.board(session.rows), session.side.toString())) {
                    "Engine history does not match the observed board"
                }
                send("go perft 1")
                val engineLegal = mutableSetOf<String>()
                while (true) {
                    val line = readLine(verifyDeadline)
                    Regex("^([a-i][0-9][a-i][0-9]): ").find(line)?.let { engineLegal += it.groupValues[1] }
                    if (line.startsWith("Nodes searched:")) break
                    if (line.startsWith("info string CRITICAL ERROR")) throw IOException(line)
                }
                require(engineLegal == legal) { "Local and engine legal moves disagree" }
                trace.event("engine.position-verified", "legalMoves=${engineLegal.size} fen=$engineFen")
                send("go movetime 1000")
                var depth: Int? = null
                var score: String? = null
                var variation = emptyList<String>()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
                while (true) {
                    val line = readLine(deadline)
                    if (line.startsWith("info string CRITICAL ERROR")) throw IOException(line)
                    if (line.startsWith("info ")) {
                        Regex("\\bdepth (\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { depth = it }
                        Regex("\\bscore (cp|mate) (-?\\d+)").find(line)?.let {
                            score = "${it.groupValues[1]} ${it.groupValues[2]}"
                        }
                        val pv = line.substringAfter(" pv ", "")
                        if (pv.isNotEmpty()) variation = pv.split(' ')
                    }
                    if (line.startsWith("bestmove ")) {
                        val move = line.split(' ').getOrNull(1) ?: throw IOException("Missing bestmove")
                        require(move in legal) { "Engine returned an illegal move" }
                        return@withLock XiangqiAnalysis(session.side, move, depth, score, variation, session.positionCommand)
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                throw IOException("Missing bestmove")
            } catch (error: Exception) {
                trace.failure("engine", error)
                close()
                throw error
            }
        }
    }

    private fun start() {
        if (process?.isAlive == true) return
        require(binary.isFile && sha256(binary) == ENGINE_SHA) { "Pikafish binary missing or invalid" }
        if (!network.isFile || sha256(network) != NNUE_SHA) {
            network.parentFile?.mkdirs()
            app.assets.open("pikafish.nnue").use { source ->
                network.outputStream().use { destination -> source.copyTo(destination) }
            }
        }
        require(sha256(network) == NNUE_SHA) { "Pikafish NNUE invalid" }
        val launched = ProcessBuilder(binary.absolutePath)
            .directory(app.filesDir)
            .redirectErrorStream(true)
            .start()
        val queue = LinkedBlockingQueue<String>()
        process = launched
        output = queue
        Thread({
            try {
                launched.inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        queue.put(line)
                    }
                }
            } finally {
                queue.put(EOF)
            }
        }, "pikafish-output").apply { isDaemon = true }.start()
        send("uci")
        waitFor("uciok", 10_000)
        send("setoption name Threads value 1")
        send("setoption name Hash value 32")
        send("setoption name EvalFile value ${network.absolutePath}")
        send("isready")
        waitFor("readyok", 15_000)
    }

    private fun send(command: String) {
        val running = process ?: throw IOException("Pikafish not running")
        running.outputStream.write("$command\n".toByteArray(Charsets.UTF_8))
        running.outputStream.flush()
    }

    private fun waitFor(expected: String, timeoutMs: Long) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (readLine(deadline) != expected) Unit
    }

    private fun readLine(deadline: Long): String {
        val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
        if (remaining <= 0) throw IOException("Pikafish timeout")
        val line = output?.poll(remaining, TimeUnit.MILLISECONDS) ?: throw IOException("Pikafish timeout")
        if (line == EOF) throw IOException("Pikafish exited")
        return line
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { source ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    override fun close() {
        process?.destroyForcibly()
        process = null
        output = null
    }
}
