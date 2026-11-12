package com.aliothmoon.maameow.presentation.view.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTouchMappingTest {

    @Test
    fun portraitPreviewMapsTouchesToXiangqiDisplay() {
        val display = DefaultDisplayConfig.RES_XIANGQI
        val view = IntSize(1080, 1920)

        val center = viewToVirtualDisplay(Offset(540f, 960f), view, display)
        assertTrue(center.inside)
        assertEquals(IntOffset(450, 800), center.offset)

        val bottomRight = viewToVirtualDisplay(Offset(1079f, 1919f), view, display)
        assertTrue(bottomRight.inside)
        assertEquals(IntOffset(899, 1599), bottomRight.offset)
    }

    @Test
    fun letterboxIsOutsideGameAndDragIsClamped() {
        val display = DefaultDisplayConfig.RES_XIANGQI
        val view = IntSize(1080, 2400)

        val center = viewToVirtualDisplay(Offset(540f, 1200f), view, display)
        assertTrue(center.inside)
        assertEquals(IntOffset(450, 800), center.offset)

        val outside = viewToVirtualDisplay(Offset(540f, 0f), view, display)
        assertFalse(outside.inside)
        assertEquals(0, outside.offset.y)
    }
}
