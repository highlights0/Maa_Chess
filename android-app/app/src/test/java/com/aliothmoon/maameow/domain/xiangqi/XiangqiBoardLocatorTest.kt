package com.aliothmoon.maameow.domain.xiangqi

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt

class XiangqiBoardLocatorTest {
    private fun board(c: XiangqiBoardCalibration, pieces: Boolean = true): IntArray {
        val w = c.sourceWidth
        val h = c.sourceHeight
        val pixels = IntArray(w * h) { i ->
            val wood = (i * 17 % 7)
            0xff000000.toInt() or ((210 + wood) shl 16) or ((165 + wood) shl 8) or (100 + wood)
        }
        val dx = (c.right - c.left) / 8
        val dy = (c.bottom - c.top) / 9
        val thickness = (dx * .025).coerceAtLeast(1.0)
        for (y in 0 until h) for (x in 0 until w) {
            val col = ((x - c.left) / dx).roundToInt()
            val row = ((y - c.top) / dy).roundToInt()
            val onColumn = col in 0..8 && abs(x - c.left - col * dx) <= thickness && y >= c.top && y <= c.bottom &&
                (col == 0 || col == 8 || y <= c.top + 4 * dy || y >= c.top + 5 * dy)
            val onRow = row in 0..9 && abs(y - c.top - row * dy) <= thickness && x >= c.left && x <= c.right
            if (onColumn || onRow) pixels[y * w + x] = 0xff735531.toInt()
            if (pieces && col in 0..8 && row in 0..9 && (row == 0 || row == 9 || (row == 3 || row == 6) && col % 2 == 0) &&
                (x - c.left - col * dx) * (x - c.left - col * dx) +
                (y - c.top - row * dy) * (y - c.top - row * dy) < dx * dx * .16) pixels[y * w + x] = 0xffedc17c.toInt()
        }
        return pixels
    }

    private fun assertCorners(expected: XiangqiBoardCalibration, actual: XiangqiBoardCalibration?) {
        assertNotNull(actual)
        val tolerance = (expected.right - expected.left) / 8 * .10
        assertEquals(expected.left, actual!!.left, tolerance)
        assertEquals(expected.top, actual.top, tolerance)
        assertEquals(expected.right, actual.right, tolerance)
        assertEquals(expected.bottom, actual.bottom, tolerance)
        assertEquals(expected.sourceWidth, actual.sourceWidth)
        assertEquals(expected.sourceHeight, actual.sourceHeight)
    }

    @Test fun locatesGridWithoutResolutionProfilesIncludingLetterboxedAndSparseBoards() {
        for (c in listOf(
            XiangqiBoardCalibration(90.0, 390.0, 630.0, 997.5, 720, 1280),
            XiangqiBoardCalibration(55.0, 310.0, 669.0, 1000.75, 720, 1280),
            XiangqiBoardCalibration(82.0, 620.0, 998.0, 1650.5, 1080, 2352),
            XiangqiBoardCalibration(130.0, 790.0, 1138.0, 1924.0, 1268, 2756),
            XiangqiBoardCalibration(450.0, 250.0, 1050.0, 925.0, 1500, 1100),
        )) for (pieces in listOf(true, false)) {
            assertCorners(c, XiangqiBoardLocator.locate(board(c, pieces), c.sourceWidth, c.sourceHeight))
        }
    }

    @Test fun cachedGridStaysIdenticalAcrossPieceChangesButIsRelocatedAfterLayoutChanges() {
        val c = XiangqiBoardCalibration(90.0, 390.0, 630.0, 997.5, 720, 1280)
        val first = XiangqiBoardLocator.locate(board(c), 720, 1280)!!
        assertSame(first, XiangqiBoardLocator.locate(board(c, false), 720, 1280, first))
        val moved = c.copy(left = 55.0, top = 310.0, right = 669.0, bottom = 1000.75)
        assertCorners(moved, XiangqiBoardLocator.locate(board(moved), 720, 1280, first))
        val oneRank = c.copy(top = c.top + 67.5, bottom = c.bottom + 67.5)
        assertCorners(oneRank, XiangqiBoardLocator.locate(board(oneRank), 720, 1280, first))
        val oneFile = c.copy(left = c.left + 67.5, right = c.right + 67.5)
        assertCorners(oneFile, XiangqiBoardLocator.locate(board(oneFile), 720, 1280, first))
    }

    @Test fun blankAndNonGridWindowsNeverFallBackToCachedCoordinates() {
        val c = XiangqiBoardCalibration(90.0, 390.0, 630.0, 997.5, 720, 1280)
        assertNull(XiangqiBoardLocator.locate(IntArray(720 * 1280) { 0xffcccccc.toInt() }, 720, 1280, c))
        val stripes = IntArray(720 * 1280) { i -> if (i / 720 % 100 < 4) 0xff111111.toInt() else 0xffcccccc.toInt() }
        assertNull(XiangqiBoardLocator.locate(stripes, 720, 1280))
    }

    /** Optional private replay corpus. Images and their local paths never enter Git. */
    @Test fun replayLocalDiagnosticImagesWhenProvided() {
        val manifest = System.getenv("XIANGQI_GRID_SAMPLES")?.let(::File) ?: return
        for (line in manifest.readLines().filter { it.isNotBlank() }) {
            val fields = line.split('\t')
            val image = ImageIO.read(File(fields[0]))
            val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
            if (fields[1] == "none") {
                assertNull("Unexpected grid in ${File(fields[0]).name}", XiangqiBoardLocator.locate(pixels, image.width, image.height))
                continue
            }
            val c = fields.drop(1).map(String::toDouble)
            val located = XiangqiBoardLocator.locate(pixels, image.width, image.height)
            assertNotNull("No grid in ${File(fields[0]).name}", located)
            assertCorners(XiangqiBoardCalibration(c[0], c[1], c[2], c[3], image.width, image.height), located)
        }
    }
}
