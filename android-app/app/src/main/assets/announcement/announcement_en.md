# Welcome to Maa_Chess

Maa_Chess is an Android automation project for Tencent's Tiantian Xiangqi.

## Current build

- The Android app reuses MAA-Meow's device management, Shizuku / Root permission handling, and background virtual-display infrastructure.
- The task page can launch and close Tiantian Xiangqi and preview its background virtual display.
- The initial portrait display profile is 900×1600 at 240 DPI.
- Standard-match and puzzle-challenge entry points are available. Board recognition, position validation, Pikafish analysis, and automated moves are still in development.

## Getting started

1. Check device and permission status on Home.
2. Foreground mode can launch the game directly. Background virtual-display mode requires Shizuku or Root.
3. Confirm the package under Settings → Game Client Packages.
4. Open Tasks, choose Standard Match or Puzzle Challenge, and launch the game.
5. In background mode, tap the preview to view the virtual display full screen.

The current MuMu profile uses package `com.tencent.qqgame.xq`, 900×1600 resolution, and 240 DPI. Recheck the package and recalibrate the board after changing device, resolution, or game layout.

Background virtual-display compatibility depends on Android, Shizuku / Root permissions, and the game. Untested devices must not be marked as compatible.

## Feedback and development

- Repository: [highlights0/Maa_Chess](https://github.com/highlights0/Maa_Chess)
- Issues: [GitHub Issues](https://github.com/highlights0/Maa_Chess/issues)

The Android app is adapted from MAA-Meow (AGPL-3.0); upstream license and third-party notices are retained.
