"""MaaFramework custom action for saving an Android-internal screenshot."""

from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np
from maa.agent.agent_server import AgentServer
from maa.context import Context
from maa.custom_action import CustomAction

PROJECT_ROOT = Path(__file__).resolve().parents[1]


@AgentServer.custom_action("capture_android_screen")
class CaptureAndroidScreenAction(CustomAction):
    def run(self, context: Context, argv: CustomAction.RunArg) -> bool:
        try:
            params = json.loads(argv.custom_action_param or "{}")
        except json.JSONDecodeError:
            print("capture_android_screen: custom_action_param 不是有效 JSON")
            return False

        output_dir = Path(params.get("output_dir", "debug/maa-captures"))
        if not output_dir.is_absolute():
            output_dir = PROJECT_ROOT / output_dir
        output_dir.mkdir(parents=True, exist_ok=True)

        image = context.tasker.controller.post_screencap().wait().get()
        if image is None:
            print("capture_android_screen: MAA Controller 未返回截图")
            return False

        array = np.asarray(image)
        if array.ndim not in {2, 3} or array.size == 0:
            print("capture_android_screen: 截图数据形状无效")
            return False
        if array.ndim == 3 and array.shape[2] == 4:
            array = cv2.cvtColor(array, cv2.COLOR_BGRA2BGR)

        filename = datetime.now().strftime("maa_%Y%m%d_%H%M%S_%f.png")
        output_path = output_dir / filename
        if not cv2.imwrite(str(output_path), array):
            print(f"capture_android_screen: 写入失败：{output_path}")
            return False

        print(f"capture_android_screen: screenshot saved: {output_path}")
        return True
