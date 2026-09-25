# Tiny386 for Android

[English](README.md) | **简体中文**

**一个能在 Android 上跑的 x86 虚拟机 —— 而且整个项目完全在一台手机上构建。**
没有电脑、没有 Android Studio、没下载过 NDK：整套工具链跑在
[Termux](https://termux.dev) 里，APK 靠 `aapt` / `javac` / `d8` / `apksigner`
手工组装出来。

基于 [tiny386](https://github.com/hchunhui/tiny386)（作者 Chunhui He，BSD-3-Clause 许可）。

---

## 亮点

- **真正的 PC 模拟** —— SeaBIOS POST、启动顺序、VGA 文本模式、IDE 硬盘、软驱、光驱
- **能运行真实的 guest 代码** —— 启动真正的 BIOS，执行手写的 16 位机器码
- **虚拟机管理器界面** —— 创建 / 编辑 / 删除虚拟机，选择镜像，新建稀疏磁盘
- **屏幕软键盘** —— 完整的 PS/2 set-1 扫描码，包含扩展键（方向键、F1–F10）
- **APK 只有 333 KB**，原生 `arm64-v8a`，零外部依赖
- **零成本工具链** —— 整个构建过程都在手机上完成，全部使用免费开源工具

---

## 项目状态

| | |
|---|---|
| 版本 | `0.0.1` |
| 最低系统 | Android 5.0（API 21） |
| 目标系统 | API 29 |
| CPU 架构 | 仅 `arm64-v8a` |
| APK 大小 | 约 333 KB |

**已经能用的**：BIOS POST、文本模式显示、软盘引导、运行手写的引导扇区、
屏幕键盘输入、虚拟机的创建与管理。

**还没做的**：鼠标、声音、从分区硬盘引导、Android 11+ 上的 `/sdcard` 访问、
性能优化。

---

## 它是怎么工作的

```
┌──────────────────────────────────────────────┐
│  Java 层（LauncherActivity / CreateVmActivity）│  虚拟机管理界面
├──────────────────────────────────────────────┤
│  MainActivity ── JNI ──▶ libtiny386.so        │  模拟器画面 + 输入
│    · SurfaceView + Bitmap (ARGB_8888)         │
│    · copyPixelsFromBuffer（零格式转换）        │
├──────────────────────────────────────────────┤
│  tiny386 核心（i386/amd64、VGA、IDE …）        │  C99
│  bridge.c：平台 HAL + JNI + 帧循环             │
├──────────────────────────────────────────────┤
│  assets：SeaBIOS · VGA BIOS · 引导软盘         │
└──────────────────────────────────────────────┘
```

VGA 核心把显存写成 `BGRX` 格式，而这**正好就是 Android `ARGB_8888`
位图的内存布局**，所以整个显示路径不需要任何像素格式转换 ——
每帧只做一次 `memcpy`。再配合 `Bitmap.setHasAlpha(false)`，连 alpha 字节
都可以直接忽略。

键盘输入直接送进 PS/2 控制器（`ps2_put_keycode`），所以 guest 收到的是
真正的键盘扫描码。扩展键以 `0xE0XX` 形式传递（例如 `↑` = `0xE048`）。

---

## 自己编译（就在手机上）

### 需要的东西

| 工具 | 获取方式 | 用途 |
|---|---|---|
| JDK | `pkg install openjdk-17` | `javac`、`keytool` |
| aapt | `pkg install aapt` | 资源、assets、R.java |
| d8 | `pkg install d8` | `.class` → `classes.dex` |
| apksigner | `pkg install apksigner` | 签名 |
| clang | `pkg install clang` | 编译原生 `.so` |
| Android API 存根 | `android-all.jar` | 编译时用的 Android API |

Android API 存根来自 Robolectric 的 `android-all` 包；任何等价的
`android.jar` 都可以用。**不需要安装 Android SDK。**

### 编译

```sh
# 1. 编译原生核心（只需一次）
clang -O3 -w -DNO_ALOG -fPIC -shared -I jni \
      -DI386_ENABLE_FPU -DI386_ENABLE_MMX -DI386_ENABLE_SSE \
      -DI386_ENABLE_SSE2 -DI386_ENABLE_SSE3 -DI386_ENABLE_SSSE3 \
      -ffunction-sections -fdata-sections \
      -o libtiny386.so jni/tiny386/*.c bridge.c -lm -Wl,--gc-sections

# 2. 打包并签名（版本号自动递增）
./build.sh
```

`build.sh` 会依次跑 `aapt → javac → d8 → aapt add → apksigner`，
最后输出 `out/t386.apk`，并**自动拷贝到下载目录 + 校验 md5**。

---

## 相对上游 tiny386 做的 5 处改动

为了让它在手机上真正跑起来，改了 5 个地方。前两个在 VGA 核心里，
第三个是配置文件的问题，后两个是上游隐藏的坑。

1. **`vga.c` —— 默认调色板。** `vga_init()` 之后调色板是全 0，
   结果文本模式渲染出来是黑底黑字。现在在复位时就装入标准的 16 色
   VGA/EGA 调色板（和真实硬件一致）。

2. **`vga.c` —— 寄存器 `0x3C6`。** DAC 像素掩码寄存器没有实现，
   而未实现的 I/O 端口读回是 `0x00`。BIOS 在设置显示模式时会往
   `0x3C6` 写 `0xFF` 并读回校验，所以显示模式永远设不完。现在
   `0x3C6` 会正常保存/返回，默认值 `0xFF`。

3. **引导设备的槽位冲突。** 上游的 `hda` 和 `cda` 会用同一个槽位
   `disks[0]`（一个物理 IDE 位置只能挂一个设备），所以同时写这两个键，
   光盘会悄悄把硬盘覆盖掉。这个 App 改成**按虚拟机配置的"第一引导"
   决定用哪个槽位**，顺便也让 SeaBIOS 的启动顺序跟着界面走 ——
   不用再掐着秒表按 Esc 了。

4. **串口导致的崩溃。** 上游的 `u8250_update()` 是从**宿主机的 stdin**
   读串口输入的，读不到就 `abort()`。命令行版没问题，但在 Android 上
   根本没有 stdin，必崩。本版本保持 `enable_serial = 0`，改用 VGA 控制台。

5. **配置键 `vga_bios`。** ini 里的键名是 `vga_bios`（**带下划线**）。
   写成 `vgabios` 会静默跳过 VGA ROM 的加载，结果就是系统能启动、
   但完全没有显示初始化。这个坑花的时间最多。

---

## 调试记录

从"全黑屏"到"SeaBIOS 露出启动信息"，靠的是编译进 bridge 的 5 个探针，
每帧把数据报到屏幕上：

| 探针 | 想回答的问题 | 结果 |
|---|---|---|
| 帧缓冲非零像素数 | 到底有没有画东西？ | 只有一个光标 |
| guest 内存 `0x7C00` + 引导签名 | BIOS 有没有加载我们的引导扇区？ | 有，`55aa` |
| VGA 显存内容 + 大小 | BIOS 有没有往里写文字？ | 写了 18 KB，但全是空格 |
| BIOS 数据区 `0x449` / 光标 | BIOS 认为自己在什么模式？ | **`mode=00`** |
| `0xC0000` 处的 ROM 区 | VGA ROM 加载了吗？ | **`0000` → 没加载** |

最后一个探针直接指向配置键的问题，而修复只差**一个下划线**。

---

## 路线图

- [ ] 鼠标支持（PS/2，`0xE0XX` 数据包流）
- [ ] 声音（PC 喇叭、SB16）
- [ ] Android 11+ 上通过 SAF 从 `/sdcard` 引导 ISO / 硬盘
- [ ] 提升模拟速度
- [ ] 自适应图标、深浅色主题
- [ ] 支持更多 CPU 架构

---

## 许可证与第三方组件

本项目自己写的代码（Android 界面、`bridge.c`、构建脚本、引导扇区）
以 **BSD-3-Clause** 发布，与上游 tiny386 保持一致。

它静态链接/打包了以下组件，请遵守各自的许可：

| 组件 | 许可 | 说明 |
|---|---|---|
| tiny386 | BSD-3-Clause | 版权 © 2024–2025 Chunhui He |
| fmopl（Adlib OPL2） | LGPL | 源码包含在 `jni/tiny386/` |
| SeaBIOS（`bios.bin`、`vgabios.bin`） | LGPL-3 | 预编译镜像打包在 `assets/` |
| 移植自 QEMU / TinyEMU 的部分 | MIT | 硬件模型 |
| Robolectric `android-all` | Apache-2.0 | 仅编译时使用（不打包进 APK） |

这里的 SeaBIOS 镜像由 tiny386 的发布流程生成（用 coreboot/seabios 加一个小补丁）。
源码：<https://github.com/coreboot/seabios>

---

## 致谢

- **Chunhui He** —— tiny386，没有它就没有这一切
- **SeaBIOS** 团队 —— BIOS 和 VGA BIOS
- **Termux** —— 手机上完整的 Linux 工具链，是这个项目能成立的前提

*这个 App 是在一台运行 Termux 的 Android 手机上编写、编译、调试并打包完成的。*
