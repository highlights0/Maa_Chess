package com.aliothmoon.maameow.domain.xiangqi

import android.content.Context
import android.content.SharedPreferences
import com.aliothmoon.maameow.domain.models.RunMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class XiangqiCalibrationStoreTest {
    private val values = mutableMapOf<String, String>()
    private val preferences = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val context = mockk<Context>()

    private fun store(): XiangqiCalibrationStore {
        every { context.getSharedPreferences("xiangqi_board_calibration", Context.MODE_PRIVATE) } returns preferences
        every { preferences.getString(any(), null) } answers { values[firstArg()] }
        every { preferences.edit() } returns editor
        every { editor.putString(any(), any()) } answers { values[firstArg()] = secondArg(); editor }
        return XiangqiCalibrationStore(context)
    }

    @Test fun unfamiliarAspectRequiresCalibrationAndConfirmedPointsSurviveReloadWithoutStretching() {
        val store = store()
        val error = assertThrows(XiangqiCalibrationRequired::class.java) { store.resolve(RunMode.FOREGROUND, 1080, 2352) }
        assertEquals(2352, error.height)
        val manual = XiangqiBoardCalibration(80.0, 640.0, 1000.0, 1666.0, 1080, 2352)
        store.save(RunMode.FOREGROUND, manual)
        val loaded = store().resolve(RunMode.FOREGROUND, 1080, 2352)
        assertEquals(manual, loaded)
        assertEquals(XiangqiScreenPoint(80, 640), XiangqiSquare(0, 0).screenPoint(loaded, false))
        assertEquals(XiangqiScreenPoint(1000, 1666), XiangqiSquare(9, 8).screenPoint(loaded, false))
        assertThrows(XiangqiCalibrationRequired::class.java) { store.resolve(RunMode.BACKGROUND, 1080, 2352) }
        assertThrows(XiangqiCalibrationRequired::class.java) { store.resolve(RunMode.FOREGROUND, 1080, 2200) }
    }

    @Test fun measuredProfilesAndThumbnailToNativeInputMappingRemainAvailable() {
        val store = store()
        assertEquals(XiangqiBoardCalibration.PHONE_1080X2400, store.resolve(RunMode.FOREGROUND, 1080, 2400))
        val capture = store.resolve(RunMode.BACKGROUND, 720, 1280)
        val native = capture.scaledTo(900, 1600)
        assertEquals(66.0, native.left, 1e-7)
        assertEquals(363.0, native.top, 1e-7)
        assertEquals(834.0, native.right, 1e-7)
        assertEquals(1227.0, native.bottom, 1e-7)
        assertEquals(XiangqiScreenPoint(834, 1227), XiangqiSquare(9, 8).screenPoint(capture.scaledTo(900, 1600), false))
    }

    @Test fun invalidCornersAndInconsistentGridCannotBeSaved() {
        val store = store()
        for (invalid in listOf(
            XiangqiBoardCalibration(1000.0, 640.0, 80.0, 1666.0, 1080, 2352),
            XiangqiBoardCalibration(80.0, 640.0, 1100.0, 1666.0, 1080, 2352),
            XiangqiBoardCalibration(80.0, 640.0, 1000.0, 800.0, 1080, 2352),
        )) assertThrows(IllegalArgumentException::class.java) { store.save(RunMode.FOREGROUND, invalid) }
        assertTrue(values.isEmpty())
    }
}
