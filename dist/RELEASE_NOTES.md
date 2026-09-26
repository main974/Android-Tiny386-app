把 386 模拟器塞进 Android 手机 —— 而且整个项目是在手机上构建出来的。
没有电脑、没有 Android Studio、没下载过 NDK，`aapt` / `javac` / `d8` / `apksigner` 全在 Termux 里跑。

## 📥 下载

**`tiny386-0.0.1-b18.apk`** · 333 KB · `arm64-v8a` · Android 5.0+（minSdk 21 / target 29）

- **SHA-256**：`84f79b94c8498586679921cf1cb26da6f7d4e492206df53c3c8b92ead6f1fd9b`
- **MD5**：`87790c9773b308a309aecc67506f8857`
- 只含 `arm64-v8a`，32 位老机器装不上
- 用的是 debug 签名 —— 安装时提示"未知来源"是正常的

## 这一版能做什么

| 功能 | 状态 |
|---|---|
| 虚拟机管理器（创建 / 编辑 / 删除 / 选镜像） | ✅ |
| 软驱 / IDE 硬盘 / 光盘，第一引导可切换 | ✅ |
| 稀疏虚拟磁盘 | ✅ |
| 56dp 屏幕软键盘（完整 PS/2 set-1 扫描码，含方向键 / F1–F10） | ✅ |
| ⏻ 关机键、防二次启动闪退 | ✅ |
| 从 ISO 引导 FreeDOS 1.1（实测） | ✅ |
| 鼠标 / 声音 | ⬜ 还没做 |

## 相对上游 tiny386 的两处修复

1. VGA 默认调色板
2. 寄存器 `0x3C6` 默认值改为 `0xFF`

基于 [tiny386](https://github.com/hchunhui/tiny386)（作者 Chunhui He）。

## English

A working x86 PC emulator for Android, built entirely on a phone with Termux
(`aapt` / `javac` / `d8` / `apksigner`). Based on
[tiny386](https://github.com/hchunhui/tiny386) by Chunhui He.

The APK below is `arm64-v8a`, Android 5.0+, debug-signed, ~333 KB.
Build instructions are in the [README](https://github.com/main974/Android-Tiny386-app#-build).

**License:** BSD-3-Clause
