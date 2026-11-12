package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.domain.models.RunMode
import org.junit.Assert.assertEquals
import org.junit.Test

class GameLaunchActionTest {
    @Test
    fun foregroundAlwaysStartsOnPrimaryDisplay() {
        assertEquals(
            GameLaunchAction.START,
            resolveGameLaunchAction(RunMode.FOREGROUND, gameAlive = true, alreadyOnBackgroundDisplay = false),
        )
    }

    @Test
    fun backgroundStartsWhenGameIsNotRunning() {
        assertEquals(
            GameLaunchAction.START,
            resolveGameLaunchAction(RunMode.BACKGROUND, gameAlive = false, alreadyOnBackgroundDisplay = false),
        )
    }

    @Test
    fun backgroundKeepsGameAlreadyOnTargetDisplay() {
        assertEquals(
            GameLaunchAction.KEEP_ON_BACKGROUND_DISPLAY,
            resolveGameLaunchAction(RunMode.BACKGROUND, gameAlive = true, alreadyOnBackgroundDisplay = true),
        )
    }

    @Test
    fun backgroundMovesExistingPrimaryDisplayGame() {
        assertEquals(
            GameLaunchAction.MOVE_TO_BACKGROUND_DISPLAY,
            resolveGameLaunchAction(RunMode.BACKGROUND, gameAlive = true, alreadyOnBackgroundDisplay = false),
        )
    }
}
