# Tiny386 for Android

**A working x86 PC emulator for Android — built entirely on a phone.**
No computer, no Android Studio, no downloaded NDK: the whole toolchain runs inside
[Termux](https://termux.dev), and the APK is assembled by hand with `aapt` / `javac` /
`d8` / `apksigner`.

Based on the excellent [tiny386](https://github.com/hchunhui/tiny386) PC emulator by
Chunhui He (BSD-3-Clause).

---

## Highlights

- **Real PC emulation** — SeaBIOS POST, boot order, VGA text mode, IDE, floppy, CD-ROM
- **Runs guest code** — boots a real BIOS and executes hand-written 16-bit machine code
- **Virtual machine manager UI** — create/save/select VMs, pick disk images, make sparse disks
- **On-screen keyboard** — full PS/2 set-1 scancodes, including extended keys (arrows, F1–F10)
- **333 KB APK**, native `arm64-v8a`, no external dependencies
- **Zero-cost toolchain** — the entire build runs on-device with free and open-source tools

---

## Status

| | |
|---|---|
| Version | `0.0.1-b18` |
| Min SDK | 21 (Android 5.0) |
| Target SDK | 29 |
| ABI | `arm64-v8a` only |
| APK size | ~333 KB |

What works today: BIOS POST, text-mode display, floppy boot, hand-written boot sector,
software keyboard input, VM creation/management.

Not implemented yet: mouse, sound, hard-disk partitioned boot, `/sdcard` file access on
Android 11+, performance tuning.

---

## How it works

```
┌──────────────────────────────────────────────┐
│  Java (LauncherActivity / CreateVmActivity)  │  VM manager UI
├──────────────────────────────────────────────┤
│  MainActivity ── JNI ──▶ libtiny386.so       │  emulator view + input
│    · SurfaceView + Bitmap (ARGB_8888)         │
│    · copyPixelsFromBuffer  (zero conversion)  │
├──────────────────────────────────────────────┤
│  tiny386 core (i386/amd64, VGA, IDE, …)      │  C99
│  bridge.c: platform HAL + JNI + frame loop    │
├──────────────────────────────────────────────┤
│  assets: SeaBIOS · VGA BIOS · boot floppy     │
└──────────────────────────────────────────────┘
```

The frame buffer is written by the VGA core as `BGRX`, which is byte-for-byte the memory
layout of an Android `ARGB_8888` bitmap, so the display path needs no pixel conversion —
one `memcpy` per frame. `Bitmap.setHasAlpha(false)` lets the alpha byte be ignored.

Input goes straight to the PS/2 controller (`ps2_put_keycode`), so the guest sees real
keyboard scancodes. Extended keys are passed as `0xE0XX` (e.g. `↑` = `0xE048`).

---

## Build it yourself (on the phone)

### Requirements

| Tool | Package | Purpose |
|---|---|---|
| JDK | `openjdk-17` | `javac`, `keytool` |
| aapt | `aapt` | resources, assets, R.java |
| d8 | `d8` | `.class` → `classes.dex` |
| apksigner | `apksigner` | signing |
| clang | `clang` | native `.so` |
| Android API stubs | `android-all.jar` | compile against Android APIs |

The Android API stubs come from Robolectric's `android-all` artifact; any equivalent
`android.jar` works. There is no need for the Android SDK.

### Build

```sh
# 1. compile the native core (once)
clang -O2 -w -DNO_ALOG -fPIC -shared -I jni \
      -o libtiny386.so jni/tiny386/*.c bridge.c -lm

# 2. build and sign the APK (auto-increments the version)
./build.sh
```

`build.sh` runs `aapt → javac → d8 → aapt add → apksigner` and writes
`out/t386.apk`.

---

## Changes relative to upstream tiny386

Three fixes were needed to get the emulator to boot in this environment. The first two are
in the VGA core, the third was a configuration mistake worth documenting.

1. **`vga.c` — default palette.** `vga_init()` left the palette zeroed, so text mode
   rendered black-on-black. A standard 16-colour VGA/EGA palette is now installed at
   reset, as real hardware has.
2. **`vga.c` — register `0x3C6`.** The DAC pixel-mask register was unimplemented, and
   unknown I/O ports read back `0x00`. The BIOS writes `0xFF` to `0x3C6` during mode set
   and verifies it, so the video mode never completed. `0x3C6` now stores and returns its
   value, defaulting to `0xFF`.
3. **Boot-order slot collision.** The core maps both `hda` and `cda` onto
   `disks[0]` (one slot per physical IDE position), so writing both makes the
   CD silently overwrite the hard disk. This app instead picks the slot based
   on the VM's configured first boot device, which also makes SeaBIOS's boot
   order follow the UI setting — no more pressing Esc at the right moment.
4. **Serial console abort.** `u8250_update()` reads serial *input* from the
   host's `stdin` and calls `abort()` when the read returns 0. That is fine for
   a CLI build but fatal on Android, where there is no stdin. This build keeps
   `enable_serial = 0` and uses the VGA console instead.
5. **Config key `vga_bios`.** The ini key is `vga_bios` (with an underscore). Writing
   `vgabios` silently skips loading the VGA ROM, which produces a machine that boots fine
   but has no video initialisation at all. This one cost the most time.

---

## Debugging notes

Getting from "black screen" to "SeaBIOS banner" was done with five small probes compiled
into the bridge, all reported to the screen each frame:

| Probe | What it answered | Finding |
|---|---|---|
| frame-buffer non-zero pixel count | is anything drawn? | only the cursor |
| guest RAM at `0x7C00` + boot signature | did the BIOS load our boot sector? | yes — `55aa` |
| VGA RAM content + size | did the BIOS write text? | 18 KB written, but all spaces |
| BIOS data area `0x449` / cursor | what mode does the BIOS think it is in? | **`mode=00`** |
| ROM area at `0xC0000` | was the VGA ROM loaded? | **`0000` → not loaded** |

The last probe pointed at the ini key, and the fix was a single underscore.

---

## Roadmap

- [ ] Mouse support (PS/2, `0xE0XX` packet stream)
- [ ] Sound (PC speaker, SB16)
- [ ] ISO / hard-disk boot from `/sdcard` via SAF on Android 11+
- [ ] Improve emulation throughput (currently a few 10k instructions/s)
- [ ] Adaptive launcher icon, dark/light theming
- [ ] Multiple ABIs

---

## License and third-party components

This project's own code (the Android UI, `bridge.c`, build scripts, and the boot sector)
is released under **BSD-3-Clause**, matching upstream tiny386.

It statically bundles and/or links the following components. Please respect their terms:

| Component | License | Notes |
|---|---|---|
| tiny386 | BSD-3-Clause | copyright © 2024–2025 Chunhui He |
| fmopl (Adlib OPL2) | LGPL | source included in `jni/tiny386/` |
| SeaBIOS (`bios.bin`, `vgabios.bin`) | LGPL-3 | prebuilt images bundled in `assets/` |
| QEMU / TinyEMU derived parts | MIT | ported hardware models |
| Robolectric `android-all` | Apache-2.0 | compile-time stubs only (not shipped) |

The SeaBIOS images bundled here were produced by the tiny386 release process, which builds
`coreboot/seabios` with a small patch. Source: <https://github.com/coreboot/seabios>.

---

## Credits

- **Chunhui He** — tiny386, without which none of this exists
- **SeaBIOS** team — BIOS and VGA BIOS
- **Termux** — a complete Linux toolchain on a phone, which made the whole build possible

*This app was written, compiled, debugged and packaged on an Android phone running Termux.*
