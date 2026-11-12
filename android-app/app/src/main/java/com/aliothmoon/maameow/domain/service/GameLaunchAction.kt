package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.domain.models.RunMode

internal enum class GameLaunchAction {
    START,
    MOVE_TO_BACKGROUND_DISPLAY,
    KEEP_ON_BACKGROUND_DISPLAY,
}

/** Select the safe launch path after the target display has been prepared. */
internal fun resolveGameLaunchAction(
    runMode: RunMode,
    gameAlive: Boolean,
    alreadyOnBackgroundDisplay: Boolean,
): GameLaunchAction = when {
    runMode == RunMode.FOREGROUND -> GameLaunchAction.START
    !gameAlive -> GameLaunchAction.START
    alreadyOnBackgroundDisplay -> GameLaunchAction.KEEP_ON_BACKGROUND_DISPLAY
    else -> GameLaunchAction.MOVE_TO_BACKGROUND_DISPLAY
}
