# 欢迎使用 Maa_Chess

Maa_Chess 是面向腾讯《天天象棋》的 Android 自动化项目。

## 当前版本

- Android 原生应用已迁移 MAA-Meow 的设备管理、Shizuku / Root 权限服务和后台虚拟显示基础设施。
- 任务页已接入天天象棋启动、关闭和后台虚拟显示预览。
- 已根据 900×1600、240 DPI 设备配置后台竖屏显示。
- 标准对局和残局闯关入口已建立；棋盘识别、局面规则校验、Pikafish 分析和自动落子仍在开发中。

## 使用说明

1. 在首页检查设备连接与权限状态。
2. 前台模式可直接启动游戏；后台虚拟显示模式需要 Shizuku 或 Root。
3. 在「设置 → 游戏客户端包名」中确认天天象棋包名。
4. 进入「任务」，选择标准对局或残局闯关并启动游戏。
5. 后台模式下，点击画面预览可全屏查看虚拟显示器画面。

当前 MuMu 设备配置使用天天象棋包名 `com.tencent.qqgame.xq`、900×1600 分辨率和 240 DPI。更换设备、分辨率或游戏布局后，请重新检查包名并校准棋盘。

后台虚拟显示兼容性取决于 Android 系统、Shizuku / Root 权限和游戏本身。未经实际验证的设备不能视为已兼容。

## 反馈与开发

- QQ 交流群：**MAA自动下象棋**，群号 **1130135731**。
- [点击链接加入群聊【MAA自动下象棋】](https://qm.qq.com/q/gBBORVpNMk)
- 项目仓库：[highlights0/Maa_Chess](https://github.com/highlights0/Maa_Chess)
- 问题反馈：[GitHub Issues](https://github.com/highlights0/Maa_Chess/issues)

Android 工程基于 MAA-Meow（AGPL-3.0）改造，保留上游许可证和第三方代码声明。
