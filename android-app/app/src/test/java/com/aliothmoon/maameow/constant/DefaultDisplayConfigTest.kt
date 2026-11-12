package com.aliothmoon.maameow.constant

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultDisplayConfigTest {
    @Test
    fun xiangqiUsesPortraitResolutionMatchingMumuProfile() {
        assertEquals(
            DefaultDisplayConfig.RES_XIANGQI,
            DefaultDisplayConfig.resolveResolution(
                "Xiangqi",
                DefaultDisplayConfig.ResolutionPreference.P720,
            ),
        )
        assertEquals(900, DefaultDisplayConfig.RES_XIANGQI.width)
        assertEquals(1600, DefaultDisplayConfig.RES_XIANGQI.height)
        assertEquals(240, DefaultDisplayConfig.RES_XIANGQI.dpi)
    }

    @Test
    fun existingArknightsClientsKeepTheirResolutionPreference() {
        assertEquals(
            DefaultDisplayConfig.RES_720P,
            DefaultDisplayConfig.resolveResolution(
                "Official",
                DefaultDisplayConfig.ResolutionPreference.P720,
            ),
        )
    }
}
